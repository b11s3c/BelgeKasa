package com.belgetarayici.ocr

import com.belgetarayici.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import com.belgetarayici.imaging.Bitmaps
import com.googlecode.tesseract.android.TessBaseAPI
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.max

/**
 * Offline OCR with Tesseract (LSTM). Language data ships in the APK (`assets/tessdata`: Turkish
 * from tessdata_best, which reads serif book fonts and diacritics far better than the fast model;
 * English from tessdata_fast), so no network access is ever needed.
 */
class OcrEngine(private val context: Context) {
    data class Result(val text: String, val words: String)

    private val dataDir = File(context.noBackupFilesDir, "ocr")
    private val lock = Any()
    private var api: TessBaseAPI? = null
    private var loadedLanguages: String? = null

    val availableLanguages: List<String> by lazy {
        context.assets.list("tessdata").orEmpty().filter { it.endsWith(".traineddata") }.map { it.removeSuffix(".traineddata") }
    }

    private fun ensureData() {
        val dir = File(dataDir, "tessdata").apply { mkdirs() }
        // Bump when the shipped models change so installed copies are replaced.
        val stamp = File(dir, "version")
        val fresh = stamp.takeIf { it.exists() }?.readText() != DATA_VERSION
        for (lang in availableLanguages) {
            val f = File(dir, "$lang.traineddata")
            if (fresh || !f.exists() || f.length() == 0L) {
                context.assets.open("tessdata/$lang.traineddata").use { input -> f.outputStream().use(input::copyTo) }
            }
        }
        if (fresh) stamp.writeText(DATA_VERSION)
    }

    private fun apiFor(languages: String): TessBaseAPI {
        api?.let { if (loadedLanguages == languages) return it }
        api?.recycle()
        ensureData()
        val a = TessBaseAPI()
        check(a.init(dataDir.absolutePath, languages, TessBaseAPI.OEM_LSTM_ONLY)) { tr("OCR başlatılamadı") }
        a.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        // Sauvola binarisation copes with uneven light on phone photos better than global Otsu.
        a.setVariable("thresholding_method", "2")
        a.setVariable("user_defined_dpi", "300")
        api = a
        loadedLanguages = languages
        return a
    }

    /**
     * Recognises [bitmap]. Word boxes are returned normalised to the page (`l t r b word` per line)
     * so they can be placed as an invisible, selectable text layer in exported PDFs.
     */
    fun recognize(bitmap: Bitmap, languages: String): Result = synchronized(lock) {
        val input = prepare(Bitmaps.onWhite(Bitmaps.capEdge(bitmap, 2600)))
        val a = apiFor(languages)
        a.setImage(input)
        val text = a.getUTF8Text().orEmpty().trim()
        val words = StringBuilder()
        val it = a.resultIterator
        if (it != null) {
            it.begin()
            val w = input.width.toFloat()
            val h = input.height.toFloat()
            do {
                val word = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                if (word.isEmpty()) continue
                val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                words.append("%.4f %.4f %.4f %.4f ".format(java.util.Locale.ROOT, r.left / w, r.top / h, r.right / w, r.bottom / h))
                    .append(word.replace('\n', ' ')).append('\n')
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            it.delete()
        }
        a.clear()
        Result(text, words.toString())
    }

    fun stop() = api?.stop()

    companion object {
        private const val DATA_VERSION = "2"

        /**
         * Evens out lighting (shadows, page curl, flash hot spots) by dividing by the estimated
         * paper brightness, and enlarges small images so letters reach the size the models read best.
         */
        fun prepare(bitmap: Bitmap): Bitmap {
            val rgba = Mat()
            Utils.bitmapToMat(bitmap, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val longest = max(gray.cols(), gray.rows())
            if (longest < MIN_EDGE) {
                val f = MIN_EDGE.toDouble() / longest
                Imgproc.resize(gray, gray, Size(gray.cols() * f, gray.rows() * f), 0.0, 0.0, Imgproc.INTER_CUBIC)
            }
            // Paper brightness: close away the (dark) text on a small copy, then blur and scale back.
            val small = Mat()
            val q = 4.0
            Imgproc.resize(gray, small, Size(gray.cols() / q, gray.rows() / q), 0.0, 0.0, Imgproc.INTER_AREA)
            val k = (max(small.cols(), small.rows()) / 40).coerceAtLeast(7) or 1
            Imgproc.morphologyEx(small, small, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
            Imgproc.GaussianBlur(small, small, Size(k.toDouble(), k.toDouble()), 0.0)
            val background = Mat()
            Imgproc.resize(small, background, gray.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            val even = Mat()
            Core.divide(gray, background, even, 255.0)
            val out = Bitmap.createBitmap(even.cols(), even.rows(), Bitmap.Config.ARGB_8888)
            Imgproc.cvtColor(even, rgba, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgba, out)
            listOf(rgba, gray, small, background, even).forEach { it.release() }
            return out
        }

        private const val MIN_EDGE = 2000
    }
}
