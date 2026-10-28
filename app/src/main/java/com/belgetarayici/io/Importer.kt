package com.belgetarayici.io

import com.belgetarayici.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.belgetarayici.data.NewPage
import com.belgetarayici.imaging.Bitmaps
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Imports external files into memory as [NewPage]s. Nothing is written to storage here: the
 * caller encrypts the pages into the vault.
 */
class Importer(private val context: Context) {
    sealed interface Result {
        class Ok(val name: String, val pages: List<NewPage>) : Result
        object NeedsPassword : Result
        class Failed(val message: String) : Result
    }

    enum class Kind { IMAGE, PDF, DOCX, BTZ, UNKNOWN }

    fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Belge"
        return name
    }

    fun kindOf(uri: Uri): Kind {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).lowercase()
        return when {
            name.endsWith(".btz") || mime == com.belgetarayici.share.Btz.MIME -> Kind.BTZ
            mime == "application/pdf" || name.endsWith(".pdf") -> Kind.PDF
            mime.contains("wordprocessingml") || name.endsWith(".docx") -> Kind.DOCX
            mime.startsWith("image/") || name.substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp") -> Kind.IMAGE
            else -> Kind.UNKNOWN
        }
    }

    fun import(uri: Uri, password: String? = null): Result = try {
        val title = displayName(uri).substringBeforeLast('.')
        when (kindOf(uri)) {
            Kind.IMAGE -> Result.Ok(title, listOf(NewPage(image(uri), filter = "original")))
            Kind.PDF -> pdf(uri, title, password)
            Kind.DOCX -> Result.Ok(title, docx(uri))
            Kind.BTZ -> Result.Failed(tr("Paylaşım paketleri ayrı açılır"))
            Kind.UNKNOWN -> Result.Failed(tr("Desteklenmeyen dosya türü"))
        }
    } catch (e: OutOfMemoryError) {
        Result.Failed(tr("Dosya bu cihaz için çok büyük"))
    } catch (e: Exception) {
        Result.Failed(e.message ?: tr("Dosya okunamadı"))
    }

    private fun bytes(uri: Uri, max: Long = 300L * 1024 * 1024): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error(tr("Dosya açılamadı"))
        return input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > max) error(tr("Dosya çok büyük"))
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    fun image(uri: Uri): Bitmap {
        val data = bytes(uri, 80L * 1024 * 1024)
        val bmp = Bitmaps.decode(data, Bitmaps.MAX_PAGE_EDGE)
        val rotation = runCatching {
            when (ExifInterface(data.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        return Bitmaps.rotate(Bitmaps.onWhite(bmp), rotation)
    }

    private fun pdf(uri: Uri, title: String, password: String?): Result {
        val data = bytes(uri)
        val doc = try {
            PDDocument.load(data, password.orEmpty())
        } catch (_: InvalidPasswordException) {
            return Result.NeedsPassword
        }
        doc.use {
            val count = min(it.numberOfPages, MAX_PDF_PAGES)
            val stripper = PDFTextStripper()
            val texts = (1..count).map { n ->
                runCatching { stripper.startPage = n; stripper.endPage = n; stripper.getText(it).trim() }.getOrDefault("")
            }
            val images: List<Bitmap> = if (!it.isEncrypted) renderWithPlatform(uri, count) ?: renderWithPdfBox(it, count) else renderWithPdfBox(it, count)
            return Result.Ok(title, images.mapIndexed { i, b -> NewPage(b, text = texts.getOrElse(i) { "" }) })
        }
    }

    private fun renderWithPlatform(uri: Uri, count: Int): List<Bitmap>? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            PdfRenderer(fd).use { r ->
                (0 until min(count, r.pageCount)).map { i ->
                    r.openPage(i).use { page ->
                        val s = PDF_EDGE.toFloat() / max(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * s).toInt(), (page.height * s).toInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bmp
                    }
                }
            }
        }
    }.getOrNull()

    private fun renderWithPdfBox(doc: PDDocument, count: Int): List<Bitmap> {
        val renderer = PDFRenderer(doc)
        return (0 until count).map { i ->
            val box = doc.getPage(i).mediaBox
            val s = PDF_EDGE / max(box.width, box.height)
            renderer.renderImage(i, s)
        }
    }

    /** Lays out DOCX paragraphs and images on A4 pages (150 dpi). */
    private fun docx(uri: Uri): List<NewPage> {
        val blocks = context.contentResolver.openInputStream(uri)!!.use { DocxReader.read(it) }
        val w = 1240
        val h = 1754
        val margin = 110f
        val contentW = (w - 2 * margin).toInt()
        val pages = ArrayList<NewPage>()
        var bmp: Bitmap? = null
        var canvas: Canvas? = null
        var y = margin
        val text = StringBuilder()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
        val heading = TextPaint(body).apply { textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun finish() {
            bmp?.let { pages += NewPage(it, text = text.toString().trim()) }
            bmp = null; canvas = null; text.clear()
        }
        fun ensurePage(): Canvas {
            canvas?.let { return it }
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            bmp = b
            y = margin
            return Canvas(b).also { canvas = it }
        }

        for (block in blocks) when (block) {
            is DocxReader.Block.Text -> {
                if (block.text.isBlank()) { y += body.textSize * 0.8f; continue }
                val paint = if (block.heading) heading else body
                val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()
                for (line in 0 until layout.lineCount) {
                    val lh = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                    if (y + lh > h - margin) finish()
                    val c = ensurePage()
                    val lineText = block.text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd('\n')
                    c.drawText(lineText, margin, y + (layout.getLineBaseline(line) - layout.getLineTop(line)), paint)
                    text.append(lineText.trimEnd()).append('\n')
                    y += lh
                }
                y += body.textSize * 0.5f
            }
            is DocxReader.Block.Image -> {
                val img = runCatching { Bitmaps.decode(block.bytes, 2000) }.getOrNull() ?: continue
                val s = min(contentW.toFloat() / img.width, (h - 2 * margin) / img.height).coerceAtMost(1.5f)
                val iw = img.width * s
                val ih = img.height * s
                if (y + ih > h - margin) finish()
                val c = ensurePage()
                c.drawBitmap(img, null, RectF(margin, y, margin + iw, y + ih), imgPaint)
                y += ih + body.textSize * 0.5f
            }
        }
        finish()
        if (pages.isEmpty()) pages += NewPage(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
        return pages
    }

    companion object {
        private const val MAX_PDF_PAGES = 500
        private const val PDF_EDGE = 2200
    }
}
