package com.belgetarayici.ui

import com.belgetarayici.i18n.L
import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.belgetarayici.container
import com.belgetarayici.data.NewPage
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.DocumentDetector
import com.belgetarayici.imaging.Filter
import com.belgetarayici.imaging.Filters
import com.belgetarayici.imaging.PageComposer
import com.belgetarayici.imaging.Quad
import com.belgetarayici.io.Importer
import com.belgetarayici.pdf.PdfExport
import com.belgetarayici.pdf.PdfSession
import com.belgetarayici.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Library : Screen
    data object Settings : Screen
    data object Keys : Screen
    data class Camera(val targetDocId: String? = null) : Screen
    data object ScanReview : Screen
    data class Document(val id: String) : Screen
    data class Page(val pageId: String) : Screen
    data class Recrop(val pageId: String) : Screen
    data class Sign(val pageId: String) : Screen
    data class Annotate(val pageId: String) : Screen
    data class Share(val target: ShareTarget) : Screen
    data class Receive(val uri: Uri) : Screen

    // PDF tab (reader/editor); all operate on [AppViewModel.pdf].
    data object PdfViewer : Screen
    data class PdfAnnotate(val page: Int) : Screen
    data object PdfForm : Screen
    data object PdfPages : Screen
    data class PdfReflow(val page: Int) : Screen
}

fun Screen.isPdfScreen() = when (this) {
    Screen.PdfViewer, Screen.PdfForm, Screen.PdfPages, is Screen.PdfAnnotate, is Screen.PdfReflow -> true
    is Screen.Share -> target == ShareTarget.Pdf
    else -> false
}

sealed interface ShareTarget {
    data class Scan(val docId: String) : ShareTarget
    /** The PDF open in [AppViewModel.pdf]. */
    data object Pdf : ShareTarget
}

enum class HomeTab(private val labelTr: String) {
    SCANS("Taramalar"), PDF("PDF"), TOOLS("Araçlar");

    val label: String get() = tr(labelTr)
}

enum class ScanMode(private val labelTr: String) {
    DOCUMENT("Belge"), ID_CARD("Kimlik kartı"), BOOK("Kitap");

    val label: String get() = tr(labelTr)
}

/** One captured photo awaiting crop/filter review. Held only in memory. */
class Capture(original: Bitmap, quad: Quad, filter: Filter) {
    var original by mutableStateOf(original)
    var quad by mutableStateOf(quad)
    var filter by mutableStateOf(filter)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val c = app.container

    val backStack = mutableStateListOf<Screen>(Screen.Library)
    val current: Screen get() = backStack.last()

    /** Bumped after every vault mutation so screens reload. */
    var dataVersion by mutableIntStateOf(0)
        private set

    var busy by mutableStateOf<String?>(null)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    // --- scan session ---
    val captures = mutableStateListOf<Capture>()
    var scanMode by mutableStateOf(ScanMode.DOCUMENT)
    var scanTargetDoc: String? = null
        private set

    // --- incoming files ---
    private var pendingIncoming: Uri? = null
    var importPasswordRequest by mutableStateOf<Uri?>(null)
        private set

    private var ocrJob: Job? = null
    var ocrProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var homeTab by mutableStateOf(HomeTab.SCANS)

    fun navigate(s: Screen) { backStack.add(s) }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val removed = backStack.removeAt(backStack.lastIndex)
        if (removed == Screen.PdfViewer && Screen.PdfViewer !in backStack) closePdf()
        return true
    }

    fun replaceTop(s: Screen) {
        backStack[backStack.lastIndex] = s
    }

    fun popTo(predicate: (Screen) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) backStack.removeAt(backStack.lastIndex)
    }

    fun changed() { dataVersion++ }

    fun toast(msg: String) { _messages.tryEmit(msg) }

    /** Runs [block] off the main thread with a blocking progress indicator and error reporting. */
    fun work(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = label
            try {
                withContext(Dispatchers.Default) { block() }
            } catch (e: Throwable) {
                toast(e.message ?: tr("Beklenmeyen hata"))
            } finally {
                busy = null
            }
        }
    }

    fun defaultName(prefix: String = tr("Tarama")): String =
        "${prefix} " + SimpleDateFormat("d MMM yyyy HH.mm", L.locale).format(Date())

    // --- scanning ---

    fun startScan(targetDocId: String? = null) {
        captures.clear()
        scanTargetDoc = targetDocId
        navigate(Screen.Camera(targetDocId))
    }

    /**
     * [hint] is the outline the live preview showed when the shutter fired; it is used when the
     * still photo alone gives no (or only a much smaller, e.g. a table inside the page) outline.
     */
    private fun makeCapture(bitmap: Bitmap, hint: Quad? = null): Capture {
        val img = Bitmaps.capEdge(bitmap, Bitmaps.MAX_PAGE_EDGE)
        val found = runCatching { DocumentDetector.detect(img) }.getOrNull()
        val quad = when {
            found == null -> hint ?: Quad.FULL
            hint != null && found.area() < hint.area() * 0.6f -> hint
            else -> found
        }
        return Capture(img, quad, Filter.of(c.settings.defaultFilter))
    }

    fun addCapture(bitmap: Bitmap, hint: Quad? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val cap = makeCapture(bitmap, hint)
            withContext(Dispatchers.Main) { captures += cap }
        }
    }

    fun addGalleryImages(uris: List<Uri>) = work(tr("Görüntüler yükleniyor")) {
        uris.forEach { uri ->
            val cap = makeCapture(c.importer.image(uri))
            withContext(Dispatchers.Main) { captures += cap }
        }
    }

    fun processCapture(cap: Capture): Bitmap =
        Filters.apply(DocumentDetector.warp(cap.original, cap.quad), cap.filter)

    fun saveScan() = work(tr("Kaydediliyor")) {
        val processed = captures.map { processCapture(it) }
        val pages: List<NewPage> = when (scanMode) {
            ScanMode.DOCUMENT -> captures.zip(processed).map { (cap, img) -> NewPage(img, cap.original, cap.filter.id, cap.quad.encode()) }
            ScanMode.BOOK -> processed.flatMap { PageComposer.splitSpread(it) }.map { NewPage(it) }
            ScanMode.ID_CARD -> processed.chunked(2).map { NewPage(PageComposer.idCardSheet(it[0], it.getOrNull(1))) }
        }
        if (pages.isEmpty()) return@work
        val target = scanTargetDoc
        val docId = if (target != null) {
            c.repo.addPages(target, pages); target
        } else {
            c.repo.createDocument(defaultName(if (scanMode == ScanMode.ID_CARD) tr("Kimlik") else tr("Tarama")), pages)
        }
        withContext(Dispatchers.Main) {
            captures.clear()
            changed()
            popTo { it is Screen.Library || (it is Screen.Document && it.id == docId) }
            if (current !is Screen.Document) navigate(Screen.Document(docId))
        }
        if (c.settings.autoOcr) runOcr(docId, onlyMissing = true)
    }

    // --- OCR ---

    fun runOcr(docId: String, onlyMissing: Boolean, pageIds: List<String>? = null) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch(Dispatchers.Default) {
            val pages = c.repo.pages(docId).filter { (pageIds == null || it.id in pageIds) && (!onlyMissing || it.ocrText.isBlank()) }
            try {
                pages.forEachIndexed { i, p ->
                    ocrProgress = i to pages.size
                    val r = c.ocr.recognize(c.repo.pageBitmap(p.id), c.settings.ocrLanguages)
                    c.repo.setOcr(p.id, r.text, r.words)
                    withContext(Dispatchers.Main) { changed() }
                }
                if (pages.isNotEmpty()) toast(tr("Metin tanıma tamamlandı"))
            } catch (e: Exception) {
                toast(trf("Metin tanıma hatası: %s", e.message))
            } finally {
                ocrProgress = null
            }
        }
    }

    // --- import ---

    fun onIncoming(uri: Uri) {
        if (c.lock.locked.value) { pendingIncoming = uri; return }
        handleIncoming(uri)
    }

    fun onUnlocked() {
        pendingIncoming?.let { pendingIncoming = null; handleIncoming(it) }
    }

    /** A file opened from another app: packages and PDFs open in their viewers, others import as scans. */
    private fun handleIncoming(uri: Uri) = when (c.importer.kindOf(uri)) {
        Importer.Kind.PDF -> openPdfUri(uri)
        else -> openFiles(listOf(uri))
    }

    /** Files picked or received: `.btz` packages open in the viewer, everything else is imported. */
    fun openFiles(uris: List<Uri>, targetDocId: String? = null) {
        val (packages, files) = uris.partition { c.importer.kindOf(it) == Importer.Kind.BTZ }
        packages.firstOrNull()?.let { navigate(Screen.Receive(it)) }
        if (files.isNotEmpty()) importFiles(files, targetDocId)
    }

    // --- PDF reader/editor ---

    var pdf by mutableStateOf<PdfSession?>(null)
        private set

    /** A PDF waiting for its password before it can be shown. */
    class PendingPdf(val id: String?, val name: String, val bytes: ByteArray, val readOnly: Boolean)

    var pdfPasswordRequest by mutableStateOf<PendingPdf?>(null)
        private set

    fun openPdf(id: String) = work(tr("PDF açılıyor")) {
        val info = c.pdfs.get(id) ?: error(tr("PDF bulunamadı"))
        showPdf(PendingPdf(id, info.name, c.pdfs.bytes(id), readOnly = false), password = null)
    }

    /** Opens a file from another app read into memory only; it reaches the vault on first save. */
    fun openPdfUri(uri: Uri) = work(tr("PDF açılıyor")) {
        val bytes = c.contentResolver().openInputStream(uri)?.use { it.readBytes() } ?: error(tr("Dosya açılamadı"))
        showPdf(PendingPdf(null, c.importer.displayName(uri).substringBeforeLast('.'), bytes, readOnly = false), password = null)
    }

    fun openPdfBytes(name: String, bytes: ByteArray, readOnly: Boolean) = work(tr("PDF açılıyor")) {
        showPdf(PendingPdf(null, name, bytes, readOnly), password = null)
    }

    private suspend fun showPdf(p: PendingPdf, password: String?) {
        val session = try {
            PdfSession(getApplication(), p.id, p.name, p.bytes, password, p.readOnly)
        } catch (_: PdfTools.NeedsPassword) {
            withContext(Dispatchers.Main) { pdfPasswordRequest = p }
            if (password != null) toast(tr("Parola yanlış"))
            return
        }
        // Protected files imported without a password get their page count and text indexed now.
        if (p.id != null && password != null) runCatching { c.pdfs.save(p.id, p.name, p.bytes, password) }
        withContext(Dispatchers.Main) {
            // Screens of the previous PDF would otherwise act on this one (e.g. sharing a view-only file).
            backStack.removeAll { it.isPdfScreen() }
            if (backStack.isEmpty()) backStack.add(Screen.Library)
            pdf?.close()
            pdf = session
            navigate(Screen.PdfViewer)
        }
    }

    fun answerPdfPassword(password: String?) {
        val p = pdfPasswordRequest ?: return
        pdfPasswordRequest = null
        if (password != null) work(tr("PDF açılıyor")) { showPdf(p, password) }
    }

    private fun closePdf() {
        pdf?.close()
        pdf = null
    }

    /** Applies a PdfBox edit to the open PDF and stores the result in the vault. */
    fun editPdf(label: String, onDone: (() -> Unit)? = null, block: (com.tom_roush.pdfbox.pdmodel.PDDocument) -> Unit) {
        val s = pdf ?: return
        if (s.readOnly) { toast(tr("Bu belge salt görüntülemek için paylaşıldı")); return }
        work(label) {
            val updated = PdfTools.edit(s.bytes, s.password, block)
            savePdf(s, updated)
            withContext(Dispatchers.Main) { onDone?.invoke() }
        }
    }

    /** Stores new bytes for the session (first save of an external file creates the vault copy). */
    suspend fun savePdf(s: PdfSession, bytes: ByteArray) {
        check(!s.readOnly) { tr("Bu belge salt görüntülemek için paylaşıldı") }
        val wasExternal = s.id == null
        s.id = c.pdfs.save(s.id, s.name, bytes, s.password)
        withContext(Dispatchers.Main) {
            s.replace(bytes)
            changed()
        }
        if (wasExternal) toast(tr("Şifreli kasaya kaydedildi"))
    }

    fun savePdfToVault() {
        val s = pdf ?: return
        work(tr("Kasaya kaydediliyor")) { savePdf(s, s.bytes) }
    }

    fun importPdfs(uris: List<Uri>) = work(tr("PDF içe aktarılıyor")) {
        var last: String? = null
        for (uri in uris) {
            val bytes = c.contentResolver().openInputStream(uri)?.use { it.readBytes() } ?: continue
            last = c.pdfs.save(null, c.importer.displayName(uri).substringBeforeLast('.'), bytes, null)
        }
        withContext(Dispatchers.Main) { changed() }
        if (uris.size == 1 && last != null) openPdf(last)
    }

    /**
     * "Recognise text": pages without their own text are rendered, OCR'd and get an
     * invisible text layer, so the PDF becomes searchable and selectable everywhere.
     */
    fun ocrPdf(id: String) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val info = c.pdfs.get(id) ?: return@launch
                val password = pdf?.takeIf { it.id == id }?.password
                val bytes = c.pdfs.bytes(id)
                val missing = PdfTools.pageTexts(bytes, password).withIndex().filter { it.value.isBlank() }.map { it.index }
                if (missing.isEmpty()) { toast(tr("Bu PDF'de zaten metin var")); return@launch }
                val results = HashMap<Int, com.belgetarayici.ocr.OcrEngine.Result>()
                var done = 0
                PdfExport.renderPages(getApplication(), bytes, password, 2000, missing) { i, bmp ->
                    ocrProgress = done to missing.size
                    results[i] = c.ocr.recognize(bmp, c.settings.ocrLanguages)
                    done++
                }
                val updated = PdfTools.edit(bytes, password) { doc ->
                    val font = com.belgetarayici.io.TextLayer.font(getApplication(), doc)
                    results.forEach { (i, r) -> if (r.text.isNotBlank()) PdfTools.addTextLayer(doc, i, r.text, r.words, font) }
                }
                c.pdfs.save(id, info.name, updated, password)
                withContext(Dispatchers.Main) {
                    pdf?.takeIf { it.id == id }?.replace(updated)
                    changed()
                }
                toast(trf("Metin tanındı: %s sayfa", results.count { it.value.text.isNotBlank() }))
            } catch (e: Exception) {
                toast(trf("Metin tanıma hatası: %s", e.message))
            } finally {
                ocrProgress = null
            }
        }
    }

    /** Creates a new vault PDF from [bytes] and opens it. */
    suspend fun createPdf(name: String, bytes: ByteArray) {
        val id = c.pdfs.save(null, name, bytes, null)
        withContext(Dispatchers.Main) { changed(); homeTab = HomeTab.PDF }
        showPdf(PendingPdf(id, name, bytes, readOnly = false), password = null)
    }

    fun importFiles(uris: List<Uri>, targetDocId: String?, password: String? = null) = work(tr("İçe aktarılıyor")) {
        var docId = targetDocId
        for (uri in uris) {
            when (val r = c.importer.import(uri, password)) {
                is Importer.Result.Ok -> {
                    if (docId == null) docId = c.repo.createDocument(r.name.ifBlank { defaultName(tr("Belge")) }, r.pages)
                    else c.repo.addPages(docId!!, r.pages)
                }
                Importer.Result.NeedsPassword -> {
                    withContext(Dispatchers.Main) { importPasswordRequest = uri; pendingImportTarget = docId }
                    if (password != null) toast(tr("Parola yanlış"))
                }
                is Importer.Result.Failed -> toast(r.message)
            }
        }
        val id = docId ?: return@work
        withContext(Dispatchers.Main) {
            changed()
            if (!(current is Screen.Document && (current as Screen.Document).id == id)) navigate(Screen.Document(id))
            // Pages that came with text (PDF text layer, DOCX) are skipped; images get recognised.
            if (c.settings.autoOcr) runOcr(id, onlyMissing = true)
        }
    }

    private var pendingImportTarget: String? = null

    fun answerImportPassword(password: String?) {
        val uri = importPasswordRequest ?: return
        importPasswordRequest = null
        if (password != null) importFiles(listOf(uri), pendingImportTarget, password)
    }
}
