package com.belgetarayici.imaging

import com.belgetarayici.i18n.tr
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object Bitmaps {
    /** Long-edge cap for stored pages: ~300 dpi on A4 while keeping memory bounded. */
    const val MAX_PAGE_EDGE = 3200

    fun decode(bytes: ByteArray, maxEdge: Int = 0): Bitmap {
        val opts = BitmapFactory.Options()
        if (maxEdge > 0) {
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error(tr("Görüntü çözülemedi"))
        return if (maxEdge > 0) capEdge(bmp, maxEdge) else bmp
    }

    fun jpeg(bitmap: Bitmap, quality: Int = 90): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    fun capEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val s = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt(), (bitmap.height * s).roundToInt(), true)
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    fun thumbnail(bitmap: Bitmap): Bitmap = capEdge(bitmap, 480)

    fun mutableCopy(bitmap: Bitmap): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

    /** Composites transparent images (PNG/WebP) onto white paper; JPEG and OCR would see black. */
    fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        out.setHasAlpha(false)
        return out
    }
}
