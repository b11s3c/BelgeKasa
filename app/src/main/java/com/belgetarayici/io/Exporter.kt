package com.belgetarayici.io

import com.belgetarayici.i18n.trf
import com.belgetarayici.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import com.belgetarayici.data.DocumentRepository
import com.belgetarayici.data.PageInfo
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.PageComposer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

enum class ExportFormat(private val labelTr: String, val extension: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("Word (DOCX)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    JPEG("Görüntü (JPEG)", "jpg", "image/jpeg"),
    TXT("Metin (TXT)", "txt", "text/plain");

    val label: String get() = tr(labelTr)
}

enum class PageSize(private val labelTr: String, val rect: PDRectangle?) {
    A4("A4", PDRectangle.A4),
    LETTER("Letter", PDRectangle.LETTER),
    FIT("Görüntüye uygun", null);

    val label: String get() = tr(labelTr)
}

enum class Quality(private val labelTr: String, val jpeg: Int, val maxEdge: Int) {
    HIGH("Yüksek", 90, 3200),
    MEDIUM("Orta (sıkıştırılmış)", 72, 2000),
    LOW("Düşük (en küçük)", 55, 1300);

    val label: String get() = tr(labelTr)
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val pageSize: PageSize = PageSize.A4,
    val quality: Quality = Quality.HIGH,
    val textLayer: Boolean = true,
    val password: String? = null,
    val watermark: String? = null,
)

/** Writes vault documents to user-chosen destinations. Pages are decrypted one at a time. */
class Exporter(private val context: Context, private val repo: DocumentRepository) {

    /** File extension actually produced (multi-page JPEG becomes a ZIP). */
    fun extensionFor(options: ExportOptions, pageCount: Int) =
        if (options.format == ExportFormat.JPEG && pageCount > 1) "zip" else options.format.extension

    fun mimeFor(options: ExportOptions, pageCount: Int) =
        if (options.format == ExportFormat.JPEG && pageCount > 1) "application/zip" else options.format.mime

    fun export(pages: List<PageInfo>, options: ExportOptions, out: OutputStream) {
        when (options.format) {
            ExportFormat.PDF -> pdf(pages, options, out)
            ExportFormat.DOCX -> DocxWriter.write(out, pages.map { p ->
                val bmp = render(p, options)
                DocxWriter.Page(Bitmaps.jpeg(bmp, options.quality.jpeg), bmp.width, bmp.height, p.ocrText)
            })
            ExportFormat.JPEG -> if (pages.size == 1) {
                out.write(Bitmaps.jpeg(render(pages[0], options), options.quality.jpeg))
            } else {
                ZipOutputStream(out).apply {
                    pages.forEachIndexed { i, p ->
                        putNextEntry(ZipEntry("sayfa-%03d.jpg".format(i + 1)))
                        write(Bitmaps.jpeg(render(p, options), options.quality.jpeg))
                        closeEntry()
                    }
                    finish()
                }
            }
            ExportFormat.TXT -> out.write(
                pages.mapIndexed { i, p -> trf("— Sayfa %s —\n%s", i + 1, p.ocrText) }.joinToString("\n\n").toByteArray(Charsets.UTF_8),
            )
        }
        out.flush()
    }

    private fun render(p: PageInfo, options: ExportOptions): Bitmap {
        var bmp = Bitmaps.capEdge(repo.pageBitmap(p.id), options.quality.maxEdge)
        options.watermark?.takeIf { it.isNotBlank() }?.let { bmp = PageComposer.watermark(bmp, it) }
        return bmp
    }

    private fun pdf(pages: List<PageInfo>, options: ExportOptions, out: OutputStream) {
        PDDocument().use { doc ->
            val font: PDFont? = if (options.textLayer && pages.any { it.ocrText.isNotBlank() }) TextLayer.font(context, doc) else null

            for (p in pages) {
                val bmp = render(p, options)
                val landscape = bmp.width > bmp.height
                val box = options.pageSize.rect?.let { if (landscape) PDRectangle(it.height, it.width) else it }
                    ?: PDRectangle(595f, 595f * bmp.height / bmp.width)
                val page = PDPage(box)
                doc.addPage(page)
                val scale = min(box.width / bmp.width, box.height / bmp.height)
                val iw = bmp.width * scale
                val ih = bmp.height * scale
                val ix = (box.width - iw) / 2
                val iy = (box.height - ih) / 2
                val image = JPEGFactory.createFromImage(doc, bmp, options.quality.jpeg / 100f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, ix, iy, iw, ih)
                    if (font != null && p.ocrText.isNotBlank()) TextLayer.write(cs, font, p.ocrText, p.ocrWords, ix, iy, iw, ih)
                }
            }

            options.password?.takeIf { it.isNotEmpty() }?.let { pw ->
                val policy = StandardProtectionPolicy(pw, pw, AccessPermission())
                policy.encryptionKeyLength = 256
                doc.protect(policy)
            }
            doc.save(out)
        }
    }
}
