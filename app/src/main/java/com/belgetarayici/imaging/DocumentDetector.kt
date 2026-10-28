package com.belgetarayici.imaging

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Four document corners in normalised [0,1] image coordinates: TL, TR, BR, BL. */
data class Quad(val points: List<PointF>) {
    init { require(points.size == 4) }

    fun encode(): String = points.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.ROOT, it.x, it.y) }

    fun isFull(): Boolean = this == FULL

    /** Area as a fraction of the image (shoelace formula). */
    fun area(): Float = kotlin.math.abs(points.indices.sumOf { i ->
        val a = points[i]; val b = points[(i + 1) % 4]
        (a.x * b.y - b.x * a.y).toDouble()
    }).toFloat() / 2f

    fun distanceTo(o: Quad): Float = points.indices.maxOf { hypot(points[it].x - o.points[it].x, points[it].y - o.points[it].y) }

    companion object {
        val FULL = Quad(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)))

        fun decode(s: String?): Quad? = s?.split(';')?.takeIf { it.size == 4 }?.mapNotNull { p ->
            val (x, y) = p.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            PointF(x.toFloatOrNull() ?: return@mapNotNull null, y.toFloatOrNull() ?: return@mapNotNull null)
        }?.takeIf { it.size == 4 }?.let(::Quad)

        /** Orders arbitrary corners as TL, TR, BR, BL (clockwise from the corner nearest the origin). */
        fun ordered(pts: List<PointF>): Quad {
            val cx = pts.sumOf { it.x.toDouble() } / pts.size
            val cy = pts.sumOf { it.y.toDouble() } / pts.size
            val clockwise = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
            val first = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
            return Quad(List(4) { clockwise[(first + it) % 4] })
        }
    }
}

object DocumentDetector {
    private const val WORK_EDGE = 640.0

    fun detect(bitmap: Bitmap): Quad? {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return detectGray(gray).also { gray.release() }
    }

    /**
     * Detection on a camera luminance plane (rows padded to [rowStride]); the result is mapped
     * into display orientation using the frame's [rotationDegrees].
     */
    fun detectLuminance(luminance: ByteArray, rowStride: Int, width: Int, height: Int, rotationDegrees: Int): Quad? {
        val tight = if (rowStride == width) luminance else ByteArray(width * height).also { out ->
            for (row in 0 until height) System.arraycopy(luminance, row * rowStride, out, row * width, width)
        }
        val mat = Mat(height, width, CvType.CV_8UC1)
        mat.put(0, 0, tight)
        val q = try { detectGray(mat) } finally { mat.release() } ?: return null
        return Quad.ordered(q.points.map { p ->
            when (rotationDegrees) {
                90 -> PointF(1 - p.y, p.x)
                180 -> PointF(1 - p.x, 1 - p.y)
                270 -> PointF(p.y, 1 - p.x)
                else -> p
            }
        })
    }

    private class Candidate(val quad: Quad, val score: Double)

    /**
     * Detects the most document-like quadrilateral in a grayscale frame. Several edge maps are
     * tried (normal and low-contrast Canny, Otsu brightness split) because no single one works
     * for every desk, light and paper; the candidate covering most of the frame with the most
     * rectangular outline wins.
     */
    fun detectGray(src: Mat): Quad? {
        val w = src.cols().toDouble()
        val h = src.rows().toDouble()
        val scale = minOf(1.0, WORK_EDGE / max(w, h))
        val small = Mat()
        Imgproc.resize(src, small, Size((w * scale).roundToInt().toDouble(), (h * scale).roundToInt().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
        val candidates = ArrayList<Candidate>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val map = Mat()
        try {
            // Thresholds relative to the frame's median brightness work across lighting conditions;
            // the second, lower pair finds white paper on a light desk.
            val median = median(small)
            for ((lo, hi) in listOf(max(10.0, 0.66 * median) to max(30.0, 1.33 * median), max(5.0, 0.25 * median) to max(15.0, 0.5 * median))) {
                Imgproc.Canny(small, map, lo, hi)
                Imgproc.morphologyEx(map, map, Imgproc.MORPH_CLOSE, kernel)
                Imgproc.dilate(map, map, kernel)
                collect(map, Imgproc.RETR_LIST, candidates)
            }
            // Bright paper against a darker background, robust to text and texture inside the page.
            Imgproc.threshold(small, map, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(map, map, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(map, map, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 3)
            collect(map, Imgproc.RETR_EXTERNAL, candidates)
        } finally {
            small.release(); map.release(); kernel.release()
        }
        return candidates.maxByOrNull { it.score }?.quad
    }

    private fun collect(map: Mat, mode: Int, out: MutableList<Candidate>) {
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(map, contours, Mat(), mode, Imgproc.CHAIN_APPROX_SIMPLE)
        val sw = map.cols().toDouble()
        val sh = map.rows().toDouble()
        val frame = sw * sh
        try {
            for ((c, a) in contours.map { it to Imgproc.contourArea(it) }.sortedByDescending { it.second }.take(8)) {
                if (a < frame * 0.1) break
                val quad = quadOf(c, sw, sh) ?: continue
                val pts = quad.map { Point(it.x * sw, it.y * sh) }
                val quadArea = Imgproc.contourArea(MatOfPoint2f(*pts.toTypedArray()))
                // Frame-sized outlines are the image border, not a page.
                if (quadArea > frame * 0.97 || quadArea < frame * 0.1) continue
                val fill = (a / quadArea).coerceAtMost(1.0)
                if (fill < 0.75) continue
                out += Candidate(Quad.ordered(quad), quadArea / frame * fill * fill)
            }
        } finally {
            contours.forEach { it.release() }
        }
    }

    /** Four plausible page corners (normalised) for a contour, or null. */
    private fun quadOf(contour: MatOfPoint, sw: Double, sh: Double): List<PointF>? {
        val hullIdx = MatOfInt()
        Imgproc.convexHull(contour, hullIdx)
        val all = contour.toArray()
        val hull = MatOfPoint2f(*hullIdx.toArray().map { all[it] }.toTypedArray())
        hullIdx.release()
        val perimeter = Imgproc.arcLength(hull, true)
        for (eps in doubleArrayOf(0.02, 0.035, 0.05, 0.08)) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(hull, approx, eps * perimeter, true)
            val pts = approx.toArray()
            if (pts.size < 4) break
            if (pts.size == 4 && plausibleCorners(pts, minOf(sw, sh))) return pts.map { PointF((it.x / sw).toFloat(), (it.y / sh).toFloat()) }
        }
        // Rounded or torn corners: the rotated bounding box of a nearly rectangular outline.
        val rect = Imgproc.minAreaRect(hull)
        if (Imgproc.contourArea(hull) / (rect.size.width * rect.size.height) < 0.9) return null
        val box = arrayOfNulls<Point>(4)
        rect.points(box)
        return box.map { PointF((it!!.x / sw).toFloat().coerceIn(0f, 1f), (it.y / sh).toFloat().coerceIn(0f, 1f)) }
    }

    /** Corner angles of a photographed page stay well away from 0° and 180°; sides are not tiny. */
    private fun plausibleCorners(p: Array<Point>, minEdge: Double): Boolean = p.indices.all { i ->
        val a = p[(i + 3) % 4]
        val b = p[i]
        val c = p[(i + 1) % 4]
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val l1 = hypot(v1x, v1y); val l2 = hypot(v2x, v2y)
        if (l1 < minEdge * 0.15 || l2 < minEdge * 0.15) return@all false
        val cos = (v1x * v2x + v1y * v2y) / (l1 * l2)
        cos in -0.6..0.6 // roughly 53°–127°
    }

    private fun median(gray: Mat): Double {
        val hist = IntArray(256)
        val bytes = ByteArray(gray.cols() * gray.rows())
        val g = if (gray.isContinuous) gray else gray.clone()
        g.get(0, 0, bytes)
        for (b in bytes) hist[b.toInt() and 0xff]++
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= bytes.size / 2) return i.toDouble()
        }
        return 127.0
    }

    /** Warps the quad region of [bitmap] to an upright rectangle. */
    fun warp(bitmap: Bitmap, quad: Quad): Bitmap {
        if (quad.isFull()) return bitmap
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val p = quad.points.map { Point((it.x * w).toDouble(), (it.y * h).toDouble()) }
        val outW = max(dist(p[0], p[1]), dist(p[3], p[2])).roundToInt().coerceAtLeast(16)
        val outH = max(dist(p[0], p[3]), dist(p[1], p[2])).roundToInt().coerceAtLeast(16)
        val src = MatOfPoint2f(*p.toTypedArray())
        val dst = MatOfPoint2f(Point(0.0, 0.0), Point(outW - 1.0, 0.0), Point(outW - 1.0, outH - 1.0), Point(0.0, outH - 1.0))
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val input = Mat()
        Utils.bitmapToMat(bitmap, input)
        val out = Mat(outH, outW, CvType.CV_8UC4)
        Imgproc.warpPerspective(input, out, m, Size(outW.toDouble(), outH.toDouble()), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, result)
        input.release(); out.release(); m.release()
        return result
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
