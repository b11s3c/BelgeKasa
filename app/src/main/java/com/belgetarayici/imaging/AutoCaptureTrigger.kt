package com.belgetarayici.imaging

/**
 * Auto-capture: fires once when a detected document has stayed still for [stillMs]. Stillness is
 * measured against where the document was when it settled, so hand tremor does not restart the
 * countdown. Moving to another page (or losing the document) re-arms it.
 */
class AutoCaptureTrigger(
    private val stillMs: Long = 1300,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var anchor: Quad? = null
    private var shot: Quad? = null
    private var since = 0L
    private var fired = false

    /** Countdown towards the next capture, 0..1 (for the shutter ring). */
    var progress = 0f
        private set

    /** After a capture: do not fire again until the document moves or disappears. */
    fun reset() { shot = anchor; anchor = null; since = 0; fired = true; progress = 0f }

    /** Feed one frame's (steadied) detection; returns true when a capture should be taken now. */
    fun update(q: Quad?): Boolean {
        val now = clock()
        if (q == null) { anchor = null; shot = null; fired = false; progress = 0f; return false }
        val s = shot
        if (fired && (s == null || q.distanceTo(s) > 0.08f)) fired = false
        val a = anchor
        if (a == null || q.distanceTo(a) > 0.04f) {
            anchor = q
            since = now
            progress = 0f
            return false
        }
        if (fired) { progress = 0f; return false }
        progress = ((now - since).toFloat() / stillMs).coerceIn(0f, 1f)
        if (now - since > stillMs) { fired = true; shot = q; progress = 0f; return true }
        return false
    }
}
