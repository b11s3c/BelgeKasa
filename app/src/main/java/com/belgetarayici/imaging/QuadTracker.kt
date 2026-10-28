package com.belgetarayici.imaging

import android.graphics.PointF

/**
 * Steadies live detections: single missed frames keep the last outline (for [graceMs]), a new
 * outline must appear twice before it is shown, and corners are smoothed so the overlay does not
 * flicker or jitter while the phone is hand-held.
 */
class QuadTracker(
    private val graceMs: Long = 700,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var shown: Quad? = null
    private var pending: Quad? = null
    private var seenAt = 0L

    val current: Quad? get() = shown

    fun update(q: Quad?): Quad? {
        val now = clock()
        val s = shown
        when {
            q == null -> if (s != null && now - seenAt > graceMs) shown = null
            s != null && q.distanceTo(s) < 0.12f -> { shown = blend(s, q, 0.5f); seenAt = now }
            pending != null && q.distanceTo(pending!!) < 0.06f -> { shown = q; pending = null; seenAt = now }
            else -> {
                pending = q
                // Another outline while one is shown: keep the shown one until its grace runs out.
                if (s != null && now - seenAt > graceMs) shown = null
            }
        }
        return shown
    }

    fun clear() { shown = null; pending = null }

    private fun blend(a: Quad, b: Quad, t: Float) =
        Quad(a.points.indices.map { PointF(a.points[it].x + (b.points[it].x - a.points[it].x) * t, a.points[it].y + (b.points[it].y - a.points[it].y) * t) })
}
