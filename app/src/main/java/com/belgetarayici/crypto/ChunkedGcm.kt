package com.belgetarayici.crypto

import java.io.EOFException
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-256-GCM in fixed-size segments (STREAM construction).
 *
 * Layout: noncePrefix(7) | segment* where each segment is GCM(plain ≤ SEGMENT) + 16-byte tag.
 * Segment nonce = noncePrefix(7) | counter(4, big-endian) | lastFlag(1). The final segment is
 * the only one with lastFlag = 1, so truncation and reordering are detected.
 */
object ChunkedGcm {
    const val SEGMENT = 64 * 1024
    private const val PREFIX = 7
    private const val TAG = 16

    fun encryptingStream(key: ByteArray, aad: ByteArray, out: OutputStream): OutputStream {
        require(key.size == 32) { "AES-256 key required" }
        val prefix = ByteArray(PREFIX).also { SecureRandom().nextBytes(it) }
        out.write(prefix)
        return EncryptingStream(key, aad, prefix, out)
    }

    fun decryptingStream(key: ByteArray, aad: ByteArray, input: InputStream): InputStream {
        require(key.size == 32) { "AES-256 key required" }
        val prefix = input.readNBytesCompat(PREFIX)
        if (prefix.size != PREFIX) throw GeneralSecurityException("Truncated stream")
        return DecryptingStream(key, aad, prefix, PushbackInputStream(input, 1))
    }

    private fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG * 8, nonce))
            updateAAD(aad)
        }

    private class EncryptingStream(
        private val key: ByteArray,
        private val aad: ByteArray,
        private val prefix: ByteArray,
        out: OutputStream,
    ) : FilterOutputStream(out) {
        private val buffer = ByteArray(SEGMENT)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var o = off
            var remaining = len
            while (remaining > 0) {
                // A full buffer is flushed only when more data follows, so the last segment is never
                // emitted with lastFlag = 0.
                if (filled == SEGMENT) emit(last = false)
                val n = minOf(remaining, SEGMENT - filled)
                System.arraycopy(b, o, buffer, filled, n)
                filled += n
                o += n
                remaining -= n
            }
        }

        private fun emit(last: Boolean) {
            val c = cipher(Cipher.ENCRYPT_MODE, key, nonce(prefix, counter++, last), aad)
            out.write(c.doFinal(buffer, 0, filled))
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            emit(last = true)
            closed = true
            out.close()
        }
    }

    private class DecryptingStream(
        private val key: ByteArray,
        private val aad: ByteArray,
        private val prefix: ByteArray,
        private val input: PushbackInputStream,
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var finished = false

        private fun fill(): Boolean {
            while (pos >= plain.size) {
                if (finished) return false
                val ct = input.readNBytesCompat(SEGMENT + TAG)
                val last = if (ct.size < SEGMENT + TAG) true else {
                    val next = input.read()
                    if (next == -1) true else { input.unread(next); false }
                }
                if (ct.size < TAG) throw GeneralSecurityException("Truncated segment")
                plain = cipher(Cipher.DECRYPT_MODE, key, nonce(prefix, counter++, last), aad).doFinal(ct)
                pos = 0
                if (last) finished = true
            }
            return true
        }

        override fun read(): Int = if (!fill()) -1 else plain[pos++].toInt() and 0xff

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = input.close()
    }
}

/** InputStream.readNBytes is API 33+ on Android; this reads until [n] bytes or EOF. */
internal fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(out, read, n - read)
        if (r < 0) break
        read += r
    }
    return if (read == n) out else out.copyOf(read)
}

internal fun InputStream.readExactly(n: Int): ByteArray {
    val b = readNBytesCompat(n)
    if (b.size != n) throw EOFException("Expected $n bytes, got ${b.size}")
    return b
}
