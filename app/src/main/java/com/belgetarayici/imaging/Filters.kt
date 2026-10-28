package com.belgetarayici.imaging

import com.belgetarayici.i18n.tr
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

enum class Filter(val id: String, private val labelTr: String) {
    ORIGINAL("original", "Orijinal"),
    MAGIC("magic", "Sihirli renk"),
    CLEAN("clean", "Gölge temizle"),
    GRAY("gray", "Gri"),
    BW("bw", "Siyah-beyaz");

    val label: String get() = tr(labelTr)

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

/** Clean-room document enhancement filters built on OpenCV. */
object Filters {
    fun apply(bitmap: Bitmap, filter: Filter): Bitmap {
        if (filter == Filter.ORIGINAL) return bitmap
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()
        val out: Mat = when (filter) {
            Filter.CLEAN -> removeShadows(rgb)
            Filter.MAGIC -> magic(rgb)
            Filter.GRAY -> gray(rgb)
            Filter.BW -> blackWhite(rgb)
            Filter.ORIGINAL -> rgb
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        if (out.channels() == 1) Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGBA) else Imgproc.cvtColor(out, out, Imgproc.COLOR_RGB2RGBA)
        Utils.matToBitmap(out, result)
        out.release()
        if (out !== rgb) rgb.release()
        return result
    }

    /**
     * Divides each channel by an estimate of its background (paper + shadow), which flattens
     * uneven lighting while keeping ink. The background is estimated at quarter resolution.
     */
    private fun flatten(channel: Mat): Mat {
        val small = Mat()
        Imgproc.resize(channel, small, Size(), 0.25, 0.25, Imgproc.INTER_AREA)
        Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)))
        Imgproc.medianBlur(small, small, 15)
        val bg = Mat()
        Imgproc.resize(small, bg, channel.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        small.release()
        val out = Mat()
        Core.divide(channel, bg, out, 255.0)
        bg.release()
        return out
    }

    private fun removeShadows(rgb: Mat): Mat {
        val channels = ArrayList<Mat>()
        Core.split(rgb, channels)
        val flat = channels.map { flatten(it).also { _ -> it.release() } }
        val out = Mat()
        Core.merge(flat, out)
        flat.forEach { it.release() }
        return out
    }

    private fun magic(rgb: Mat): Mat {
        val clean = removeShadows(rgb)
        val hsv = Mat()
        Imgproc.cvtColor(clean, hsv, Imgproc.COLOR_RGB2HSV)
        val ch = ArrayList<Mat>()
        Core.split(hsv, ch)
        ch[1].convertTo(ch[1], -1, 1.35, 0.0) // richer ink colours
        Core.merge(ch, hsv)
        ch.forEach { it.release() }
        Imgproc.cvtColor(hsv, clean, Imgproc.COLOR_HSV2RGB)
        hsv.release()
        clean.convertTo(clean, -1, 1.12, -12.0) // crisper contrast, whiter paper
        return clean
    }

    private fun gray(rgb: Mat): Mat {
        val g = Mat()
        Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(g, g)
        return g
    }

    private fun blackWhite(rgb: Mat): Mat {
        val g = Mat()
        Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY)
        val flat = flatten(g)
        g.release()
        Imgproc.GaussianBlur(flat, flat, Size(3.0, 3.0), 0.0)
        val out = Mat(flat.size(), CvType.CV_8UC1)
        val block = (minOf(flat.cols(), flat.rows()) / 40).let { if (it % 2 == 0) it + 1 else it }.coerceIn(15, 61)
        Imgproc.adaptiveThreshold(flat, out, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 10.0)
        flat.release()
        return out
    }
}
