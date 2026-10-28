package com.belgetarayici

import com.belgetarayici.i18n.L
import android.app.Application
import android.content.Context
import com.belgetarayici.crypto.KeyManager
import com.belgetarayici.data.DocumentRepository
import com.belgetarayici.data.VaultDb
import com.belgetarayici.io.Exporter
import com.belgetarayici.io.Importer
import com.belgetarayici.ocr.OcrEngine
import com.belgetarayici.pdf.PdfStore
import com.belgetarayici.security.AppLock
import com.belgetarayici.share.ShareService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.opencv.android.OpenCVLoader
import java.io.File

class BelgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        OpenCVLoader.initLocal()
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
        L.apply(container.settings.uiLanguage, java.util.Locale.getDefault())
        // Leftover share files from a previous run; done directly so the vault stays closed until unlock.
        File(cacheDir, "outbox").deleteRecursively()
    }
}

/** Manual dependency container; heavy objects are created on first use. */
class AppContainer(private val context: Context) {
    val keys by lazy { KeyManager(context) }
    val lock by lazy { AppLock(context) }
    private val db by lazy { VaultDb(File(context.noBackupFilesDir, "vault.db"), keys.databasePassphrase()) }
    val repo by lazy { DocumentRepository(File(context.filesDir, "vault"), db, keys.vault) }
    val pdfs by lazy { PdfStore(File(context.filesDir, "vault"), db, keys.vault) }
    val ocr by lazy { OcrEngine(context) }
    val importer by lazy { Importer(context) }
    val exporter by lazy { Exporter(context, repo) }
    val share by lazy { ShareService(context, repo, pdfs, keys, exporter) }
    val settings by lazy { Settings(context) }

    fun contentResolver() = context.contentResolver

    /** Irreversibly destroys the vault, database, keys and settings. */
    fun wipeEverything() {
        runCatching { db.close() }
        File(context.filesDir, "vault").deleteRecursively()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("vault.db") }?.forEach { it.delete() }
        context.cacheDir.deleteRecursively()
        listOf("belgetarayici_keys", "belgetarayici_lock", "belgetarayici_settings").forEach { context.deleteSharedPreferences(it) }
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("belgetarayici_master")
        }
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("belgetarayici_settings", Context.MODE_PRIVATE)

    var ocrLanguages: String
        get() = prefs.getString("ocr_langs", "tur")!!
        set(v) { prefs.edit().putString("ocr_langs", v).apply() }

    var autoOcr: Boolean
        get() = prefs.getBoolean("auto_ocr", true)
        set(v) { prefs.edit().putBoolean("auto_ocr", v).apply() }

    var defaultFilter: String
        get() = prefs.getString("default_filter", "magic")!!
        set(v) { prefs.edit().putString("default_filter", v).apply() }

    /** Interface language: "system", "tr" or "en" (see [L]). */
    var uiLanguage: String
        get() = prefs.getString("ui_lang", "system")!!
        set(v) { prefs.edit().putString("ui_lang", v).apply() }

    /** Read-aloud voice: "auto" (guessed from the text), "tr" or "en". */
    var speechLanguage: String
        get() = prefs.getString("speech_lang", "auto")!!
        set(v) { prefs.edit().putString("speech_lang", v).apply() }

    var autoCapture: Boolean
        get() = prefs.getBoolean("auto_capture", true)
        set(v) { prefs.edit().putBoolean("auto_capture", v).apply() }
}

val Context.container: AppContainer get() = (applicationContext as BelgeApp).container
