package com.belgetarayici.pdf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable

/**
 * An open PDF. [bytes] is the file as stored (possibly password-protected); [view] renders an
 * unprotected in-memory copy. [id] is null until an external file is first saved to the vault.
 */
class PdfSession(
    private val context: Context,
    var id: String?,
    name: String,
    bytes: ByteArray,
    val password: String?,
    /** Received as a view-only `.btz`: no editing, saving or exporting. */
    val readOnly: Boolean = false,
) : Closeable {
    var name by mutableStateOf(name)
    var bytes: ByteArray = bytes
        private set
    var view: PdfView = PdfView(context, PdfTools.viewCopy(bytes, password))
        private set

    /** Bumped whenever [bytes] change so pages re-render. */
    var version by mutableIntStateOf(0)
        private set

    private var textCache: Pair<Int, List<String>>? = null

    /** Text per page, re-extracted after edits (OCR, form filling, page changes). */
    val pageTexts: List<String>
        get() = textCache?.takeIf { it.first == version }?.second
            ?: PdfTools.pageTexts(bytes, password).also { textCache = version to it }

    fun replace(newBytes: ByteArray) {
        val newView = PdfView(context, PdfTools.viewCopy(newBytes, password))
        view.close()
        view = newView
        bytes = newBytes
        version++
    }

    override fun close() = view.close()
}
