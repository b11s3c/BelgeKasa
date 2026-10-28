package com.belgetarayici

import com.belgetarayici.crypto.ChunkedGcm
import com.belgetarayici.data.TextNorm
import com.belgetarayici.io.DocxReader
import com.belgetarayici.io.DocxWriter
import com.belgetarayici.share.Btz
import com.belgetarayici.share.SharedDocument
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class CoreFormatsTest {
    private val key = ByteArray(32) { it.toByte() }
    private val aad = "aad".toByteArray()

    private fun gcmRoundTrip(size: Int) {
        val plain = Random(size).nextBytes(size)
        val out = ByteArrayOutputStream()
        ChunkedGcm.encryptingStream(key, aad, out).use { it.write(plain) }
        val back = ChunkedGcm.decryptingStream(key, aad, ByteArrayInputStream(out.toByteArray())).readBytes()
        assertArrayEquals(plain, back)
    }

    @Test fun chunkedGcmRoundTripsAtSegmentBoundaries() {
        listOf(0, 1, ChunkedGcm.SEGMENT - 1, ChunkedGcm.SEGMENT, ChunkedGcm.SEGMENT + 1, 3 * ChunkedGcm.SEGMENT, 200_003)
            .forEach(::gcmRoundTrip)
    }

    @Test fun chunkedGcmDetectsTruncation() {
        val out = ByteArrayOutputStream()
        ChunkedGcm.encryptingStream(key, aad, out).use { it.write(ByteArray(3 * ChunkedGcm.SEGMENT)) }
        val bytes = out.toByteArray()
        // Drop the final segment: the previous segment was sealed as "not last".
        val truncated = bytes.copyOf(bytes.size - 16)
        expectFailure { ChunkedGcm.decryptingStream(key, aad, ByteArrayInputStream(truncated)).readBytes() }
    }

    private fun sample() = SharedDocument(
        "Kira sözleşmesi — İstanbul",
        listOf(
            SharedDocument.Page(Random(1).nextBytes(150_000), "Şişli, Çağlayan\nİkinci satır", "1 2 3 4 Şişli"),
            SharedDocument.Page(Random(2).nextBytes(10), "", ""),
        ),
    )

    private fun pack(header: Btz.Header, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        Btz.write(out, header, Btz.Protection.Password(password.toCharArray(), iterations = 100_000)) { sample().writeTo(it) }
        return out.toByteArray()
    }

    private fun unpack(bytes: ByteArray, password: String): Pair<Btz.Header, SharedDocument> {
        val input = ByteArrayInputStream(bytes)
        val env = Btz.readEnvelope(input)
        val k = Btz.keyFromPassword(env, password.toCharArray())
        return env.header to SharedDocument.readFrom(Btz.openBody(env, input, k))
    }

    @Test fun btzPasswordRoundTrip() {
        val header = Btz.Header(createdAt = 1_000, viewOnly = true, expiresAt = 5_000)
        val (h, doc) = unpack(pack(header, "parola-123"), "parola-123")
        assertEquals(header, h)
        assertEquals(sample().name, doc.name)
        assertEquals(2, doc.pages.size)
        assertArrayEquals(sample().pages[0].jpeg, doc.pages[0].jpeg)
        assertEquals("Şişli, Çağlayan\nİkinci satır", doc.pages[0].text)
        assertTrue(h.isExpired(now = 6_000))
    }

    @Test fun btzRejectsWrongPassword() {
        val bytes = pack(Btz.Header(1, false, null), "doğru")
        expectFailure { unpack(bytes, "yanlış") }
    }

    @Test fun btzRejectsTamperedHeader() {
        val bytes = pack(Btz.Header(1, viewOnly = true, expiresAt = null), "p")
        val text = String(bytes, Charsets.ISO_8859_1)
        val i = text.indexOf("viewOnly=true")
        // Same length, so only authentication (not parsing) can catch it: "true" -> "tru3" is not
        // a valid boolean; use a sibling header byte instead.
        val tampered = bytes.copyOf().also { it[i + "viewOnly=".length] = 'T'.code.toByte() }
        expectFailure { unpack(tampered, "p") }
    }

    @Test fun docxRoundTrip() {
        val out = ByteArrayOutputStream()
        val jpeg = Random(3).nextBytes(2_000)
        DocxWriter.write(out, listOf(DocxWriter.Page(jpeg, 1000, 1400, "Merhaba <dünya> & Ağaç\nİkinci")))
        val blocks = DocxReader.read(ByteArrayInputStream(out.toByteArray()))
        val images = blocks.filterIsInstance<DocxReader.Block.Image>()
        assertEquals(1, images.size)
        assertArrayEquals(jpeg, images[0].bytes)
        val texts = blocks.filterIsInstance<DocxReader.Block.Text>().map { it.text }.filter { it.isNotBlank() }
        assertEquals(listOf("Merhaba <dünya> & Ağaç", "İkinci"), texts)
    }

    @Test fun searchNormalisationFoldsTurkish() {
        assertEquals("isik sogus igdir", TextNorm.normalize("IŞIK  Soğuş\nIĞDIR"))
        assertEquals(TextNorm.normalize("istanbul"), TextNorm.normalize("İSTANBUL"))
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected failure")
        } catch (_: Exception) {
        }
    }
}
