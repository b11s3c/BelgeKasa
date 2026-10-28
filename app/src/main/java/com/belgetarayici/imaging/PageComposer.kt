package com.belgetarayici.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sqrt

/** Page-level compositions: ID card sheet, book spread split, watermark, overlays. */
object PageComposer {
    // A4 at 300 dpi.
    const val A4_W = 2480
    const val A4_H = 3508
    private const val CARD_W = 1011 // 85.6 mm
    private const val CARD_H = 638 // 54 mm

    /** Front and back of an ID card at true size on one A4 sheet, as photocopy shops do. */
    fun idCardSheet(front: Bitmap, back: Bitmap?): Bitmap {
        val sheet = Bitmap.createBitmap(A4_W, A4_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val left = (A4_W - CARD_W) / 2f
        fun place(b: Bitmap, top: Float) = c.drawBitmap(b, null, fit(b, RectF(left, top, left + CARD_W, top + CARD_H)), paint)
        place(front, A4_H * 0.18f)
        back?.let { place(it, A4_H * 0.18f + CARD_H + 240f) }
        return sheet
    }

    /**
     * Splits a two-page book spread at the spine. The cut runs across the longer side: a spread
     * photographed with the phone held upright arrives rotated, with the pages stacked vertically.
     */
    fun splitSpread(spread: Bitmap): List<Bitmap> =
        if (spread.width >= spread.height) {
            val half = spread.width / 2
            listOf(
                Bitmap.createBitmap(spread, 0, 0, half, spread.height),
                Bitmap.createBitmap(spread, half, 0, spread.width - half, spread.height),
            )
        } else {
            val half = spread.height / 2
            listOf(
                Bitmap.createBitmap(spread, 0, 0, spread.width, half),
                Bitmap.createBitmap(spread, 0, half, spread.width, spread.height - half),
            )
        }

    /** Diagonal, repeating, semi-transparent text watermark. */
    fun watermark(src: Bitmap, text: String, alpha: Int = 60): Bitmap {
        if (text.isBlank()) return src
        val out = Bitmaps.mutableCopy(src)
        val c = Canvas(out)
        val diag = sqrt((out.width * out.width + out.height * out.height).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 200, 30, 30)
            textSize = out.width / 14f
            isFakeBoldText = true
        }
        val step = paint.textSize * 3.2f
        c.save()
        c.rotate(-35f, out.width / 2f, out.height / 2f)
        var y = (out.height - diag) / 2f
        while (y < (out.height + diag) / 2f) {
            var x = (out.width - diag) / 2f
            while (x < (out.width + diag) / 2f) {
                c.drawText(text, x, y, paint)
                x += paint.measureText(text) + paint.textSize * 2
            }
            y += step
        }
        c.restore()
        return out
    }

    /** Draws [overlay] into [base] at a rectangle given in normalised page coordinates. */
    fun overlay(base: Bitmap, overlay: Bitmap, normRect: RectF): Bitmap {
        val out = Bitmaps.mutableCopy(base)
        val dst = RectF(normRect.left * out.width, normRect.top * out.height, normRect.right * out.width, normRect.bottom * out.height)
        Canvas(out).drawBitmap(overlay, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** White page for imported documents without a raster (e.g. DOCX text). */
    fun blankA4(scale: Float = 0.5f): Bitmap =
        Bitmap.createBitmap((A4_W * scale).toInt(), (A4_H * scale).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun fit(b: Bitmap, box: RectF): RectF {
        val s = min(box.width() / b.width, box.height() / b.height)
        val w = b.width * s
        val h = b.height * s
        return RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
    }
}
