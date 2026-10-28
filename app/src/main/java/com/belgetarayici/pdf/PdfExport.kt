package com.belgetarayici.pdf

import android.content.Context
import android.graphics.Bitmap
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.io.DocxWriter
import com.belgetarayici.io.TextLayer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.OutputStream
import kotlin.math.min

/** Conversions between PDFs and other formats ("create PDF" / "export PDF"). */
object PdfExport {
    /** Images (with optional recognised/known text) → A4 PDF, one image per page. */
    fun fromBitmaps(context: Context, pages: List<Pair<Bitmap, String>>, out: OutputStream) {
        PDDocument().use { doc ->
            val font = if (pages.any { it.second.isNotBlank() }) TextLayer.font(context, doc) else null
            for ((bmp, text) in pages) {
                val img = Bitmaps.capEdge(bmp, 2400)
                val box = if (img.width > img.height) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                val page = PDPage(box)
                doc.addPage(page)
                val s = min(box.width / img.width, box.height / img.height)
                val w = img.width * s
                val h = img.height * s
                val x = (box.width - w) / 2
                val y = (box.height - h) / 2
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(JPEGFactory.createFromImage(doc, img, 0.85f), x, y, w, h)
                    if (font != null && text.isNotBlank()) TextLayer.write(cs, font, text, "", x, y, w, h)
                }
            }
            doc.save(out)
        }
    }

    /** PDF → DOCX: each page as an image followed by its extracted text (editable in Word). */
    fun toDocx(context: Context, bytes: ByteArray, password: String?, out: OutputStream) {
        val texts = PdfTools.pageTexts(bytes, password)
        PdfView(context, PdfTools.viewCopy(bytes, password)).use { view ->
            DocxWriter.write(out, (0 until view.pageCount).map { i ->
                val bmp = view.render(i, 1400)
                DocxWriter.Page(Bitmaps.jpeg(bmp, 80), bmp.width, bmp.height, texts.getOrElse(i) { "" })
            })
        }
    }

    /** Rendered page images for OCR/export. */
    fun renderPages(context: Context, bytes: ByteArray, password: String?, widthPx: Int, pages: List<Int>, each: (Int, Bitmap) -> Unit) {
        PdfView(context, PdfTools.viewCopy(bytes, password)).use { view ->
            pages.forEach { each(it, view.render(it, widthPx)) }
        }
    }
}
