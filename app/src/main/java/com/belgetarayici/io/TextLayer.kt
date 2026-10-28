package com.belgetarayici.io

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix

/** Invisible (render mode "neither") text that makes scanned pages searchable and selectable. */
object TextLayer {
    /**
     * Noto Sans covers Turkish; the standard 14 PDF fonts do not encode ş, ğ, ı. Form field
     * appearances need the [full] font: PdfBox cannot subset fonts used in AcroForm resources.
     */
    fun font(context: Context, doc: PDDocument, full: Boolean = false): PDFont =
        context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(doc, it, !full) }

    /**
     * Writes [text] over an image placed at ([ix], [iy], [iw], [ih]) in PDF user space. [words] holds
     * OCR word boxes (`l t r b word` per line, normalised, top-left origin); without them the lines
     * are stacked from the top.
     */
    fun write(cs: PDPageContentStream, font: PDFont, text: String, words: String, ix: Float, iy: Float, iw: Float, ih: Float) {
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        val boxes = words(words)
        if (boxes.isNotEmpty()) {
            for (w in boxes) {
                val x = ix + w.l * iw
                val y = iy + (1 - w.b) * ih
                val boxW = (w.r - w.l) * iw
                val size = ((w.b - w.t) * ih).coerceIn(2f, 72f)
                val word = encodable(font, w.text) ?: continue
                val natural = font.getStringWidth(word) / 1000f * size
                cs.setFont(font, size)
                if (natural > 0) cs.setHorizontalScaling((boxW / natural * 100f).coerceIn(10f, 500f))
                cs.setTextMatrix(Matrix.getTranslateInstance(x, y))
                // Trailing space so text extraction/copy keeps word boundaries.
                cs.showText(encodable(font, "$word ") ?: word)
            }
        } else {
            val size = 10f
            cs.setFont(font, size)
            var y = iy + ih - size * 1.5f
            for (line in text.lines()) {
                encodable(font, line)?.let {
                    cs.setTextMatrix(Matrix.getTranslateInstance(ix + 8, y))
                    cs.showText(it)
                }
                y -= size * 1.3f
                if (y < iy) break
            }
        }
        cs.endText()
    }

    /** One OCR word box, normalised to the page (top-left origin). */
    class Word(val l: Float, val t: Float, val r: Float, val b: Float, val text: String)

    fun words(serialized: String): List<Word> = serialized.lineSequence().mapNotNull(::parseWord).toList()

    private fun parseWord(line: String): Word? {
        val parts = line.split(' ', limit = 5)
        if (parts.size < 5) return null
        return Word(
            parts[0].toFloatOrNull() ?: return null, parts[1].toFloatOrNull() ?: return null,
            parts[2].toFloatOrNull() ?: return null, parts[3].toFloatOrNull() ?: return null, parts[4],
        )
    }

    /** Drops characters the embedded font cannot encode instead of failing the whole export. */
    fun encodable(font: PDFont, s: String): String? {
        if (s.isBlank()) return null
        return try {
            font.encode(s); s
        } catch (_: Exception) {
            s.filter { c -> runCatching { font.encode(c.toString()) }.isSuccess }.ifBlank { null }
        }
    }
}
