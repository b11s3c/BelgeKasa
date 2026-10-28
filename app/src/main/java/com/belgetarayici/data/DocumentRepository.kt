package com.belgetarayici.data

import android.graphics.Bitmap
import android.util.LruCache
import com.belgetarayici.imaging.Bitmaps
import com.google.crypto.tink.Aead
import java.io.File
import java.util.UUID

/** A freshly scanned/imported page, held in memory until it is encrypted into the vault. */
class NewPage(
    val image: Bitmap,
    val original: Bitmap? = null,
    val filter: String = "original",
    val quad: String? = null,
    val text: String = "",
    val words: String = "",
)

/**
 * The encrypted vault. Page images live in `filesDir/vault` as Tink AEAD ciphertexts whose
 * associated data binds them to their page id and role, so files cannot be swapped.
 */
private const val FULL_QUAD = "0,0;1,0;1,1;0,1"

class DocumentRepository(
    private val root: File,
    private val db: VaultDb,
    private val aead: Aead,
) {
    private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        File(root, "pages").mkdirs()
        File(root, "signatures").mkdirs()
    }

    private fun file(pageId: String, kind: String) = File(root, "pages/$pageId.$kind")

    private fun writeBlob(f: File, aadTag: String, plain: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(aead.encrypt(plain, aadTag.toByteArray()))
        if (!tmp.renameTo(f)) {
            f.delete(); tmp.renameTo(f)
        }
    }

    private fun readBlob(f: File, aadTag: String): ByteArray = aead.decrypt(f.readBytes(), aadTag.toByteArray())

    // --- queries ---

    fun documents(query: String? = null) = db.documents(query)
    fun document(id: String) = db.document(id)
    fun pages(docId: String) = db.pages(docId)
    fun page(id: String) = db.page(id)

    fun pageJpeg(pageId: String): ByteArray = readBlob(file(pageId, "img"), "page:$pageId:img")
    fun pageBitmap(pageId: String): Bitmap = Bitmaps.decode(pageJpeg(pageId))

    fun originalBitmap(pageId: String): Bitmap? {
        val f = file(pageId, "orig")
        return if (f.exists()) Bitmaps.decode(readBlob(f, "page:$pageId:orig")) else null
    }

    fun thumbnail(pageId: String): Bitmap {
        thumbs.get(pageId)?.let { return it }
        val f = file(pageId, "thumb")
        val bmp = if (f.exists()) Bitmaps.decode(readBlob(f, "page:$pageId:thumb")) else Bitmaps.thumbnail(pageBitmap(pageId))
        thumbs.put(pageId, bmp)
        return bmp
    }

    // --- mutations ---

    fun createDocument(name: String, pages: List<NewPage>): String {
        val id = UUID.randomUUID().toString()
        db.insertDocument(id, name, System.currentTimeMillis())
        addPages(id, pages)
        return id
    }

    fun addPages(docId: String, pages: List<NewPage>, at: Int? = null) {
        val existing = db.pages(docId).map { it.id }.toMutableList()
        val newIds = pages.map { storePage(docId, it) }
        if (at != null && at < existing.size) {
            existing.addAll(at, newIds)
            db.setPageOrder(docId, existing)
        }
        db.touch(docId)
    }

    private fun storePage(docId: String, p: NewPage): String {
        val id = UUID.randomUUID().toString()
        writeImages(id, p.image)
        p.original?.let { writeBlob(file(id, "orig"), "page:$id:orig", Bitmaps.jpeg(it, 92)) }
        db.insertPage(PageInfo(id, docId, db.nextIndex(docId), p.image.width, p.image.height, p.filter, p.quad, p.text, p.words))
        return id
    }

    private fun writeImages(pageId: String, image: Bitmap) {
        writeBlob(file(pageId, "img"), "page:$pageId:img", Bitmaps.jpeg(image, 90))
        val thumb = Bitmaps.thumbnail(image)
        writeBlob(file(pageId, "thumb"), "page:$pageId:thumb", Bitmaps.jpeg(thumb, 80))
        thumbs.put(pageId, thumb)
    }

    /** Replaces the processed image (after crop/filter/rotate/sign/annotate). */
    fun replacePageImage(pageId: String, image: Bitmap, filter: String? = null, quad: String? = null, original: Bitmap? = null) {
        writeImages(pageId, image)
        original?.let { writeBlob(file(pageId, "orig"), "page:$pageId:orig", Bitmaps.jpeg(it, 92)) }
        db.updatePageImage(pageId, image.width, image.height, filter, quad)
        db.page(pageId)?.let { db.touch(it.docId) }
    }

    /**
     * Stores an edit drawn onto the processed page (rotation, pen, signature). If the page keeps an
     * original photo for re-cropping, that is replaced too, so a later re-crop keeps the edit.
     */
    fun replacePageImageBakingEdits(pageId: String, image: Bitmap) {
        if (file(pageId, "orig").exists()) replacePageImage(pageId, image, filter = "original", quad = FULL_QUAD, original = image)
        else replacePageImage(pageId, image)
    }

    fun setOcr(pageId: String, text: String, words: String) = db.setOcr(pageId, text, words)

    fun rename(docId: String, name: String) = db.renameDocument(docId, name)

    fun reorder(docId: String, orderedIds: List<String>) {
        db.setPageOrder(docId, orderedIds)
        db.touch(docId)
    }

    fun deletePage(pageId: String) {
        val page = db.page(pageId) ?: return
        db.deletePage(pageId)
        deletePageFiles(pageId)
        db.setPageOrder(page.docId, db.pages(page.docId).map { it.id })
        db.touch(page.docId)
    }

    fun deleteDocument(docId: String) {
        val pages = db.pages(docId)
        db.deleteDocument(docId)
        pages.forEach { deletePageFiles(it.id) }
    }

    private fun deletePageFiles(pageId: String) {
        listOf("img", "orig", "thumb").forEach { file(pageId, it).delete() }
        thumbs.remove(pageId)
    }

    /** Copies pages (re-encrypting under new ids) into a new document. */
    fun copyPagesToNewDocument(name: String, pageIds: List<String>): String {
        val newPages = pageIds.mapNotNull { id ->
            val info = db.page(id) ?: return@mapNotNull null
            NewPage(pageBitmap(id), originalBitmap(id), info.filter, info.quad, info.ocrText, info.ocrWords)
        }
        return createDocument(name, newPages)
    }

    fun mergeDocuments(name: String, docIds: List<String>): String =
        copyPagesToNewDocument(name, docIds.flatMap { db.pages(it).map(PageInfo::id) })

    // --- signatures ---

    fun signatures(): List<String> = db.signatures()

    fun saveSignature(png: Bitmap): String {
        val id = UUID.randomUUID().toString()
        writeBlob(File(root, "signatures/$id"), "sig:$id", Bitmaps.png(png))
        db.insertSignature(id)
        return id
    }

    fun signature(id: String): Bitmap = Bitmaps.decode(readBlob(File(root, "signatures/$id"), "sig:$id"))

    fun deleteSignature(id: String) {
        db.deleteSignature(id)
        File(root, "signatures/$id").delete()
    }

    // --- contacts ---

    fun contacts() = db.contacts()
    fun saveContact(name: String, key: String) = db.upsertContact(Contact(UUID.randomUUID().toString(), name, key.trim()))
    fun deleteContact(id: String) = db.deleteContact(id)
}
