package com.belgetarayici.data

import android.content.ContentValues
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

data class DocumentInfo(
    val id: String,
    val name: String,
    val created: Long,
    val updated: Long,
    val pageCount: Int,
    val coverPageId: String?,
)

data class PageInfo(
    val id: String,
    val docId: String,
    val index: Int,
    val width: Int,
    val height: Int,
    val filter: String,
    val quad: String?,
    val ocrText: String,
    val ocrWords: String,
)

data class Contact(val id: String, val name: String, val key: String)

data class PdfInfo(val id: String, val name: String, val pages: Int, val size: Long, val updated: Long, val protected: Boolean)

/** SQLCipher database holding all metadata and OCR text. */
class VaultDb(file: File, passphrase: ByteArray) {
    private val db: SQLiteDatabase

    init {
        System.loadLibrary("sqlcipher")
        db = SQLiteDatabase.openOrCreateDatabase(file, passphrase, null, null, null)
        migrate()
    }

    private fun migrate() {
        val version = db.version
        if (version < 1) {
            db.beginTransaction()
            try {
                db.execSQL(
                    """CREATE TABLE documents(id TEXT PRIMARY KEY, name TEXT NOT NULL, name_norm TEXT NOT NULL,
                       created INTEGER NOT NULL, updated INTEGER NOT NULL)""",
                )
                db.execSQL(
                    """CREATE TABLE pages(id TEXT PRIMARY KEY, doc_id TEXT NOT NULL, idx INTEGER NOT NULL,
                       width INTEGER NOT NULL, height INTEGER NOT NULL, filter TEXT NOT NULL, quad TEXT,
                       ocr_text TEXT NOT NULL DEFAULT '', ocr_norm TEXT NOT NULL DEFAULT '', ocr_words TEXT NOT NULL DEFAULT '')""",
                )
                db.execSQL("CREATE INDEX pages_doc ON pages(doc_id, idx)")
                db.execSQL("CREATE TABLE signatures(id TEXT PRIMARY KEY, created INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE contacts(id TEXT PRIMARY KEY, name TEXT NOT NULL, key TEXT NOT NULL UNIQUE, created INTEGER NOT NULL)")
                db.version = 1
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        if (db.version < 2) {
            // v2: original PDF files (PDF tab). Text is kept normalised for search only.
            db.execSQL(
                """CREATE TABLE pdfs(id TEXT PRIMARY KEY, name TEXT NOT NULL, name_norm TEXT NOT NULL,
                   pages INTEGER NOT NULL, size INTEGER NOT NULL, protected INTEGER NOT NULL DEFAULT 0,
                   text_norm TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL, updated INTEGER NOT NULL)""",
            )
            db.version = 2
        }
    }

    // --- pdfs ---

    fun pdfs(query: String?): List<PdfInfo> {
        val q = query?.let(TextNorm::normalize)?.trim().orEmpty()
        val cols = "SELECT id, name, pages, size, updated, protected FROM pdfs"
        val c = if (q.isEmpty()) db.rawQuery("$cols ORDER BY updated DESC", null) else {
            val like = likePattern(q)
            db.rawQuery("$cols WHERE name_norm LIKE ? ESCAPE '\\' OR text_norm LIKE ? ESCAPE '\\' ORDER BY updated DESC", arrayOf(like, like))
        }
        return c.mapAll { PdfInfo(getString(0), getString(1), getInt(2), getLong(3), getLong(4), getInt(5) != 0) }
    }

    fun pdf(id: String): PdfInfo? = pdfs(null).firstOrNull { it.id == id }

    fun upsertPdf(info: PdfInfo, text: String?) {
        val values = ContentValues().apply {
            put("id", info.id); put("name", info.name); put("name_norm", TextNorm.normalize(info.name))
            put("pages", info.pages); put("size", info.size); put("protected", if (info.protected) 1 else 0)
            put("updated", info.updated)
            if (text != null) put("text_norm", TextNorm.normalize(text))
        }
        if (db.update("pdfs", values, "id = ?", arrayOf(info.id)) == 0) {
            values.put("created", info.updated)
            if (text == null) values.put("text_norm", "")
            db.insert("pdfs", null, values)
        }
    }

    fun deletePdf(id: String) {
        db.delete("pdfs", "id = ?", arrayOf(id))
    }

    fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    // --- documents ---

    fun documents(query: String?): List<DocumentInfo> {
        val base = """SELECT d.id, d.name, d.created, d.updated,
            (SELECT COUNT(*) FROM pages p WHERE p.doc_id = d.id),
            (SELECT p.id FROM pages p WHERE p.doc_id = d.id ORDER BY p.idx LIMIT 1)
            FROM documents d"""
        val q = query?.let(TextNorm::normalize)?.trim().orEmpty()
        val cursor = if (q.isEmpty()) {
            db.rawQuery("$base ORDER BY d.updated DESC", null)
        } else {
            val like = likePattern(q)
            db.rawQuery(
                """$base WHERE d.name_norm LIKE ? ESCAPE '\' OR EXISTS
                   (SELECT 1 FROM pages p WHERE p.doc_id = d.id AND p.ocr_norm LIKE ? ESCAPE '\')
                   ORDER BY d.updated DESC""",
                arrayOf(like, like),
            )
        }
        return cursor.mapAll { DocumentInfo(getString(0), getString(1), getLong(2), getLong(3), getInt(4), getStringOrNull(5)) }
    }

    fun document(id: String): DocumentInfo? = documents(null).firstOrNull { it.id == id }

    fun insertDocument(id: String, name: String, now: Long) {
        db.insert("documents", null, ContentValues().apply {
            put("id", id); put("name", name); put("name_norm", TextNorm.normalize(name))
            put("created", now); put("updated", now)
        })
    }

    fun renameDocument(id: String, name: String) {
        db.update("documents", ContentValues().apply {
            put("name", name); put("name_norm", TextNorm.normalize(name)); put("updated", System.currentTimeMillis())
        }, "id = ?", arrayOf(id))
    }

    fun touch(id: String) {
        db.update("documents", ContentValues().apply { put("updated", System.currentTimeMillis()) }, "id = ?", arrayOf(id))
    }

    fun deleteDocument(id: String) {
        db.delete("pages", "doc_id = ?", arrayOf(id))
        db.delete("documents", "id = ?", arrayOf(id))
    }

    // --- pages ---

    fun pages(docId: String): List<PageInfo> =
        db.rawQuery(
            "SELECT id, doc_id, idx, width, height, filter, quad, ocr_text, ocr_words FROM pages WHERE doc_id = ? ORDER BY idx",
            arrayOf(docId),
        ).mapAll(::readPage)

    fun page(id: String): PageInfo? =
        db.rawQuery(
            "SELECT id, doc_id, idx, width, height, filter, quad, ocr_text, ocr_words FROM pages WHERE id = ?",
            arrayOf(id),
        ).mapAll(::readPage).firstOrNull()

    private fun readPage(c: Cursor) = with(c) {
        PageInfo(getString(0), getString(1), getInt(2), getInt(3), getInt(4), getString(5), getStringOrNull(6), getString(7), getString(8))
    }

    fun insertPage(p: PageInfo) {
        db.insert("pages", null, ContentValues().apply {
            put("id", p.id); put("doc_id", p.docId); put("idx", p.index)
            put("width", p.width); put("height", p.height); put("filter", p.filter); put("quad", p.quad)
            put("ocr_text", p.ocrText); put("ocr_norm", TextNorm.normalize(p.ocrText)); put("ocr_words", p.ocrWords)
        })
    }

    fun nextIndex(docId: String): Int =
        db.rawQuery("SELECT COALESCE(MAX(idx), -1) + 1 FROM pages WHERE doc_id = ?", arrayOf(docId)).mapAll { getInt(0) }.first()

    fun updatePageImage(id: String, width: Int, height: Int, filter: String?, quad: String?) {
        db.update("pages", ContentValues().apply {
            put("width", width); put("height", height)
            if (filter != null) put("filter", filter)
            if (quad != null) put("quad", quad)
        }, "id = ?", arrayOf(id))
    }

    fun setOcr(id: String, text: String, words: String) {
        db.update("pages", ContentValues().apply {
            put("ocr_text", text); put("ocr_norm", TextNorm.normalize(text)); put("ocr_words", words)
        }, "id = ?", arrayOf(id))
    }

    fun setPageOrder(docId: String, orderedIds: List<String>) = transaction {
        orderedIds.forEachIndexed { i, id ->
            db.update("pages", ContentValues().apply { put("idx", i) }, "id = ? AND doc_id = ?", arrayOf(id, docId))
        }
    }

    fun deletePage(id: String) {
        db.delete("pages", "id = ?", arrayOf(id))
    }

    // --- signatures & contacts ---

    fun signatures(): List<String> = db.rawQuery("SELECT id FROM signatures ORDER BY created DESC", null).mapAll { getString(0) }

    fun insertSignature(id: String) {
        db.insert("signatures", null, ContentValues().apply { put("id", id); put("created", System.currentTimeMillis()) })
    }

    fun deleteSignature(id: String) {
        db.delete("signatures", "id = ?", arrayOf(id))
    }

    fun contacts(): List<Contact> =
        db.rawQuery("SELECT id, name, key FROM contacts ORDER BY name COLLATE NOCASE", null).mapAll { Contact(getString(0), getString(1), getString(2)) }

    fun upsertContact(c: Contact) {
        db.insertWithOnConflict("contacts", null, ContentValues().apply {
            put("id", c.id); put("name", c.name); put("key", c.key); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteContact(id: String) {
        db.delete("contacts", "id = ?", arrayOf(id))
    }

    fun close() = db.close()

    private inline fun <T> Cursor.mapAll(f: Cursor.() -> T): List<T> = use {
        val out = ArrayList<T>(count)
        while (moveToNext()) out += f()
        out
    }

    /** `%q%` with LIKE wildcards in [q] escaped (used with `ESCAPE '\'`). */
    private fun likePattern(q: String) = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
}
