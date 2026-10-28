package com.belgetarayici.pdf

import com.belgetarayici.i18n.tr
import com.belgetarayici.data.PdfInfo
import com.belgetarayici.data.VaultDb
import com.google.crypto.tink.Aead
import java.io.File
import java.util.UUID

/**
 * Original PDF files in the vault (vector text, forms and annotations preserved), each encrypted
 * as one Tink AEAD blob bound to its id.
 */
class PdfStore(root: File, private val db: VaultDb, private val aead: Aead) {
    private val dir = File(root, "pdfs").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.enc")
    private fun aad(id: String) = "pdf:$id".toByteArray()

    fun list(query: String? = null) = db.pdfs(query)
    fun get(id: String) = db.pdf(id)

    fun bytes(id: String): ByteArray = aead.decrypt(file(id).readBytes(), aad(id))

    /**
     * Stores [bytes] under [id] (new entry when null) and indexes page count and text for search.
     * [password] is only used to read the file; protected files stay protected.
     */
    fun save(id: String?, name: String, bytes: ByteArray, password: String?): String {
        require(bytes.size <= MAX_BYTES) { tr("PDF çok büyük (en fazla 150 MB)") }
        // A protected file imported before its password is known is indexed later, when opened.
        val info = try { PdfTools.inspect(bytes, password) } catch (_: PdfTools.NeedsPassword) { PdfTools.Info(0, "", true) }
        val newId = id ?: UUID.randomUUID().toString()
        val tmp = File(dir, "$newId.tmp")
        tmp.writeBytes(aead.encrypt(bytes, aad(newId)))
        if (!tmp.renameTo(file(newId))) {
            file(newId).delete(); tmp.renameTo(file(newId))
        }
        db.upsertPdf(PdfInfo(newId, name, info.pages, bytes.size.toLong(), System.currentTimeMillis(), info.encrypted), info.text)
        return newId
    }

    fun rename(id: String, name: String) {
        val info = db.pdf(id) ?: return
        db.upsertPdf(info.copy(name = name, updated = System.currentTimeMillis()), null)
    }

    fun delete(id: String) {
        db.deletePdf(id)
        file(id).delete()
    }

    companion object {
        const val MAX_BYTES = 150 * 1024 * 1024
    }
}
