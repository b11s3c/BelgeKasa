package com.belgetarayici.share

import com.belgetarayici.i18n.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.belgetarayici.crypto.KeyManager
import com.belgetarayici.data.DocumentRepository
import com.belgetarayici.data.NewPage
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.PageComposer
import com.belgetarayici.io.ExportOptions
import com.belgetarayici.io.Exporter
import com.belgetarayici.pdf.PdfStore
import com.belgetarayici.pdf.PdfTools
import java.io.File
import java.io.InputStream

/**
 * Builds and opens share packages. Outgoing files are written only to `cache/outbox` (app-private)
 * and handed to the share sheet through a FileProvider; they are purged on the next app start.
 */
class ShareService(
    private val context: Context,
    private val repo: DocumentRepository,
    private val pdfs: PdfStore,
    private val keys: KeyManager,
    private val exporter: Exporter,
) {
    private val outbox = File(context.cacheDir, "outbox")

    fun purgeOutbox(olderThanMs: Long = 0) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        outbox.listFiles()?.forEach { if (it.lastModified() <= cutoff) it.delete() }
    }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(60).ifBlank { "belge" }

    /** Creates an encrypted `.btz` package for [docId]. */
    fun createPackage(docId: String, header: Btz.Header, protection: Btz.Protection, watermark: String?): File {
        val doc = repo.document(docId) ?: error(tr("Belge bulunamadı"))
        outbox.mkdirs()
        purgeOutbox(olderThanMs = 10 * 60_000)
        val file = File(outbox, safeName(doc.name) + "." + Btz.EXTENSION)
        val pages = repo.pages(docId)
        file.outputStream().buffered().use { out ->
            Btz.write(out, header, protection) { body ->
                // Pages are decrypted and re-encrypted into the package one at a time.
                SharedDocument.write(body, doc.name, pages.size) { i ->
                    val p = pages[i]
                    val jpeg = if (watermark.isNullOrBlank()) repo.pageJpeg(p.id)
                    else Bitmaps.jpeg(PageComposer.watermark(repo.pageBitmap(p.id), watermark), 88)
                    SharedDocument.Page(jpeg, p.ocrText, p.ocrWords)
                }
            }
        }
        return file
    }

    /**
     * Packages a vault PDF. The original file travels unprotected inside the `.btz` (the package
     * itself is the encryption), so the recipient needs only the package password or key.
     */
    fun createPdfPackage(pdfId: String, pdfPassword: String?, header: Btz.Header, protection: Btz.Protection): File {
        val info = pdfs.get(pdfId) ?: error(tr("PDF bulunamadı"))
        val plain = PdfTools.unprotect(pdfs.bytes(pdfId), pdfPassword)
        outbox.mkdirs()
        purgeOutbox(olderThanMs = 10 * 60_000)
        val file = File(outbox, safeName(info.name) + "." + Btz.EXTENSION)
        file.outputStream().buffered().use { out ->
            Btz.write(out, header, protection) { body -> SharedDocument.write(body, info.name, 0, plain) { error("no pages") } }
        }
        return file
    }

    /** A plain or password-protected copy of a vault PDF for sharing outside the app. */
    fun createPdfExport(pdfId: String, pdfPassword: String?, newPassword: String?): File {
        val info = pdfs.get(pdfId) ?: error(tr("PDF bulunamadı"))
        val bytes = pdfs.bytes(pdfId)
        val out = if (newPassword.isNullOrEmpty()) PdfTools.unprotect(bytes, pdfPassword) else PdfTools.protect(bytes, pdfPassword, newPassword)
        outbox.mkdirs()
        purgeOutbox(olderThanMs = 10 * 60_000)
        return File(outbox, safeName(info.name) + ".pdf").also { it.writeBytes(out) }
    }

    /** Exports to a regular format (optionally password-protected PDF) for sharing outside the app. */
    fun createExport(docId: String, options: ExportOptions): File {
        val doc = repo.document(docId) ?: error(tr("Belge bulunamadı"))
        val pages = repo.pages(docId)
        outbox.mkdirs()
        purgeOutbox(olderThanMs = 10 * 60_000)
        val file = File(outbox, safeName(doc.name) + "." + exporter.extensionFor(options, pages.size))
        file.outputStream().buffered().use { exporter.export(pages, options, it) }
        return file
    }

    fun shareIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".outbox", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, tr("Paylaş")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // --- receiving ---

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)?.buffered() ?: error(tr("Paket açılamadı"))

    fun envelope(uri: Uri): Btz.Envelope = open(uri).use { Btz.readEnvelope(it) }

    /** Decrypts a package fully into memory. Throws on a wrong password/key or tampering. */
    fun decrypt(uri: Uri, password: CharArray?): SharedDocument = open(uri).use { input ->
        val env = Btz.readEnvelope(input)
        val key = when (env.mode) {
            Btz.Mode.PASSWORD -> Btz.keyFromPassword(env, password ?: error(tr("Parola gerekli")))
            Btz.Mode.RECIPIENT -> Btz.keyFromRecipient(env, keys.unwrapper)
        }
        try {
            SharedDocument.readFrom(Btz.openBody(env, input, key))
        } finally {
            key.fill(0)
        }
    }

    fun savePdfToVault(doc: SharedDocument): String =
        pdfs.save(null, doc.name.ifBlank { tr("Paylaşılan PDF") }, doc.pdf ?: error(tr("PDF yok")), null)

    fun saveToVault(doc: SharedDocument): String =
        repo.createDocument(doc.name.ifBlank { tr("Paylaşılan belge") }, doc.pages.map { p ->
            NewPage(Bitmaps.decode(p.jpeg), text = p.text, words = p.words)
        })
}
