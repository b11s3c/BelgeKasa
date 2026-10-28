package com.belgetarayici

import com.belgetarayici.io.QrCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodesTest {
    /** Camera Y planes pad rows to a stride; the QR sits somewhere in a grey frame. */
    private fun frame(text: String, stride: Int): ByteArray {
        val m = QrCodes.encode(text, 360)
        val lum = ByteArray(stride * 480) { 200.toByte() }
        for (y in 0 until m.height) for (x in 0 until m.width) {
            lum[(y + 60) * stride + x + 60] = (if (m.get(x, y)) 25 else 235).toByte()
        }
        return lum
    }

    @Test fun decodesShareKeyFromPaddedPlane() {
        // Same shape as a real key: BTKEY1: + base64url of a Tink HPKE public keyset (~150 chars).
        val key = "BTKEY1:CPeNkNwPEnAKZAo0dHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuSHBrZVB1YmxpY0tleRIoEgYIARABGAIaIOtdQ0vP2xY7mFq9sR3uWc1zK8bNfH5aJ6pL0tVgDyEo"
        assertEquals(key, QrCodes.decode(frame(key, 512), 512, 480, 480))
    }

    @Test fun decodesUnpaddedPlane() {
        assertEquals("BTKEY1:abc", QrCodes.decode(frame("BTKEY1:abc", 480), 480, 480, 480))
    }
}
