package com.belgetarayici.share

import com.belgetarayici.crypto.ChunkedGcm
import com.belgetarayici.crypto.PasswordKdf
import com.belgetarayici.crypto.readExactly
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * `.btz` — BelgeKasa's end-to-end encrypted share package (version 1).
 *
 * ```
 * "BTZ1" | mode(1) | headerLen(4) | header | wrapLen(4) | wrap | ChunkedGcm(body)
 * ```
 * The header is readable without a key (so the app can say what it needs), but every byte before
 * the body is authenticated as AAD, so it cannot be altered (e.g. clearing `viewOnly`).
 * - Password mode: wrap = salt(16) | iterations(4); key = PBKDF2-HMAC-SHA256(password).
 * - Recipient mode: wrap = HPKE(recipient public key, random content key, context = header).
 */
object Btz {
    private val MAGIC = "BTZ1".toByteArray(Charsets.US_ASCII)
    const val MIME = "application/vnd.belgetarayici.btz"
    const val EXTENSION = "btz"

    enum class Mode(val code: Int) { PASSWORD(1), RECIPIENT(2) }

    data class Header(
        val createdAt: Long,
        val viewOnly: Boolean,
        val expiresAt: Long?,
        val version: Int = 1,
    ) {
        fun encode(): ByteArray = buildString {
            append("v=").append(version).append('\n')
            append("createdAt=").append(createdAt).append('\n')
            append("viewOnly=").append(viewOnly).append('\n')
            if (expiresAt != null) append("expiresAt=").append(expiresAt).append('\n')
        }.toByteArray(Charsets.UTF_8)

        fun isExpired(now: Long = System.currentTimeMillis()) = expiresAt != null && now > expiresAt

        companion object {
            fun decode(bytes: ByteArray): Header {
                val map = bytes.toString(Charsets.UTF_8).lines().filter { '=' in it }
                    .associate { it.substringBefore('=') to it.substringAfter('=') }
                val version = map["v"]?.toIntOrNull() ?: throw GeneralSecurityException("Bad header")
                if (version != 1) throw GeneralSecurityException("Unsupported version $version")
                return Header(
                    createdAt = map["createdAt"]?.toLongOrNull() ?: 0L,
                    viewOnly = map["viewOnly"].toBoolean(),
                    expiresAt = map["expiresAt"]?.toLongOrNull(),
                    version = version,
                )
            }
        }
    }

    /** Wraps a content key for one recipient; implemented with Tink HPKE on Android. */
    interface RecipientWrapper {
        fun wrap(contentKey: ByteArray, context: ByteArray): ByteArray
    }

    fun interface RecipientUnwrapper {
        fun unwrap(wrapped: ByteArray, context: ByteArray): ByteArray
    }

    sealed interface Protection {
        class Password(val password: CharArray, val iterations: Int = PasswordKdf.SHARE_ITERATIONS) : Protection
        class Recipient(val wrapper: RecipientWrapper) : Protection
    }

    class Envelope internal constructor(
        val mode: Mode,
        val header: Header,
        internal val headerBytes: ByteArray,
        internal val wrap: ByteArray,
        internal val aad: ByteArray,
    )

    /** Writes a package; [body] receives a stream whose bytes are encrypted on the fly. */
    fun write(out: OutputStream, header: Header, protection: Protection, body: (OutputStream) -> Unit) {
        val headerBytes = header.encode()
        val contentKey: ByteArray
        val mode: Mode
        val wrap: ByteArray
        when (protection) {
            is Protection.Password -> {
                val salt = PasswordKdf.randomBytes(16)
                contentKey = PasswordKdf.derive(protection.password, salt, protection.iterations)
                wrap = ByteArrayOutputStream().also {
                    DataOutputStream(it).apply { write(salt); writeInt(protection.iterations) }
                }.toByteArray()
                mode = Mode.PASSWORD
            }
            is Protection.Recipient -> {
                contentKey = PasswordKdf.randomBytes(32)
                wrap = protection.wrapper.wrap(contentKey, headerBytes)
                mode = Mode.RECIPIENT
            }
        }
        val prefix = prefix(mode, headerBytes, wrap)
        try {
            out.write(prefix)
            ChunkedGcm.encryptingStream(contentKey, prefix, NonClosing(out)).use(body)
        } finally {
            contentKey.fill(0)
        }
    }

    /** Reads everything before the body; [input] is left positioned at the encrypted body. */
    fun readEnvelope(input: InputStream): Envelope {
        val data = DataInputStream(input)
        val magic = data.readExactly(4)
        if (!magic.contentEquals(MAGIC)) throw GeneralSecurityException("Not a BelgeKasa package")
        val mode = when (data.readUnsignedByte()) {
            1 -> Mode.PASSWORD
            2 -> Mode.RECIPIENT
            else -> throw GeneralSecurityException("Unknown protection mode")
        }
        val headerBytes = data.readExactly(checkedLength(data.readInt(), 64 * 1024))
        val wrap = data.readExactly(checkedLength(data.readInt(), 64 * 1024))
        return Envelope(mode, Header.decode(headerBytes), headerBytes, wrap, prefix(mode, headerBytes, wrap))
    }

    fun keyFromPassword(envelope: Envelope, password: CharArray): ByteArray {
        check(envelope.mode == Mode.PASSWORD)
        val data = DataInputStream(envelope.wrap.inputStream())
        val salt = data.readExactly(16)
        val iterations = data.readInt()
        if (iterations !in 100_000..10_000_000) throw GeneralSecurityException("Bad KDF parameters")
        return PasswordKdf.derive(password, salt, iterations)
    }

    fun keyFromRecipient(envelope: Envelope, unwrapper: RecipientUnwrapper): ByteArray {
        check(envelope.mode == Mode.RECIPIENT)
        return unwrapper.unwrap(envelope.wrap, envelope.headerBytes)
    }

    /** Decrypting body stream. Authentication failures surface as exceptions while reading. */
    fun openBody(envelope: Envelope, input: InputStream, contentKey: ByteArray): InputStream =
        ChunkedGcm.decryptingStream(contentKey, envelope.aad, input)

    private fun prefix(mode: Mode, headerBytes: ByteArray, wrap: ByteArray): ByteArray =
        ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                write(MAGIC)
                writeByte(mode.code)
                writeInt(headerBytes.size)
                write(headerBytes)
                writeInt(wrap.size)
                write(wrap)
            }
        }.toByteArray()

    private fun checkedLength(n: Int, max: Int): Int {
        if (n < 0 || n > max) throw GeneralSecurityException("Corrupt package")
        return n
    }

    private class NonClosing(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = out.flush()
    }
}
