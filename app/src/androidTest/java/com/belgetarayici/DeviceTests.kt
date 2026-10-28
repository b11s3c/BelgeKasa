package com.belgetarayici

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.belgetarayici.data.TextNorm
import com.belgetarayici.imaging.AutoCaptureTrigger
import com.belgetarayici.imaging.DocumentDetector
import com.belgetarayici.imaging.Filter
import com.belgetarayici.imaging.Filters
import com.belgetarayici.imaging.Quad
import com.belgetarayici.imaging.QuadTracker
import com.belgetarayici.io.QrCodes
import com.belgetarayici.io.TextLayer
import com.belgetarayici.ocr.OcrEngine
import com.belgetarayici.pdf.PdfTools
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import kotlin.math.hypot

/** On-device checks for the camera pipeline pieces, OCR and PDF geometry (no camera needed). */
@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp() {
        assertTrue(OpenCVLoader.initLocal())
    }

    // A tilted sheet of paper on a dark desk, as a camera would see it.
    private val corners = listOf(PointF(200f, 250f), PointF(1000f, 180f), PointF(1080f, 1400f), PointF(150f, 1350f))

    private fun photoOfDocument(desk: Int = Color.rgb(58, 52, 48), paperColor: Int = Color.rgb(238, 236, 230), shadow: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(desk)
        val paper = Path().apply {
            moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        c.drawPath(paper, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperColor })
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); strokeWidth = 6f }
        for (i in 0 until 12) c.drawLine(300f, 380f + i * 70f, 900f, 360f + i * 70f, ink)
        // A phone's shadow falling across the lower half of page and desk.
        if (shadow) c.drawRect(0f, 800f, 1200f, 1600f, Paint().apply {
            shader = LinearGradient(0f, 800f, 0f, 1300f, Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP)
        })
        return bmp
    }

    private fun assertNear(expected: List<PointF>, actual: Quad, tol: Float) {
        expected.forEach { e ->
            val best = actual.points.minOf { hypot(it.x - e.x, it.y - e.y) }
            assertTrue("corner $e not found in ${actual.points} (off by $best)", best < tol)
        }
    }

    @Test fun detectsTiltedDocument() {
        val q = DocumentDetector.detect(photoOfDocument())
        assertNotNull("document not detected", q)
        assertNear(corners.map { PointF(it.x / 1200f, it.y / 1600f) }, q!!, 0.03f)
    }

    @Test fun detectsWhitePaperOnLightDesk() {
        val q = DocumentDetector.detect(photoOfDocument(desk = Color.rgb(200, 198, 192), paperColor = Color.rgb(246, 245, 241)))
        assertNotNull("low-contrast page not detected", q)
        assertNear(corners.map { PointF(it.x / 1200f, it.y / 1600f) }, q!!, 0.03f)
    }

    @Test fun detectsPageUnderShadow() {
        val q = DocumentDetector.detect(photoOfDocument(shadow = true))
        assertNotNull("shadowed page not detected", q)
        assertNear(corners.map { PointF(it.x / 1200f, it.y / 1600f) }, q!!, 0.03f)
    }

    @Test fun ignoresEmptyDesk() {
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 80, 70)) }
        assertEquals(null, DocumentDetector.detect(bmp))
    }

    @Test fun orderedKeepsFourCornersOfDiamond() {
        val q = Quad.ordered(listOf(PointF(0.5f, 0.1f), PointF(0.9f, 0.5f), PointF(0.5f, 0.9f), PointF(0.1f, 0.5f)))
        assertEquals(4, q.points.toSet().size)
        assertTrue(q.area() > 0.3f)
    }

    @Test fun trackerBridgesMissedFramesAndIgnoresOneOffs() {
        var now = 0L
        val t = QuadTracker(graceMs = 700) { now }
        val q = Quad(listOf(PointF(0.1f, 0.1f), PointF(0.9f, 0.1f), PointF(0.9f, 0.9f), PointF(0.1f, 0.9f)))
        assertEquals("a single detection is not shown yet", null, t.update(q))
        now = 100; assertNotNull(t.update(q))
        now = 300; assertNotNull("one missed frame keeps the outline", t.update(null))
        now = 600; assertNotNull(t.update(null))
        now = 900; assertEquals("lost for longer than the grace period", null, t.update(null))
        // A stray outline elsewhere does not replace a steady one.
        now = 1000; t.update(q); now = 1100; t.update(q)
        val stray = Quad(listOf(PointF(0.6f, 0.6f), PointF(0.8f, 0.6f), PointF(0.8f, 0.8f), PointF(0.6f, 0.8f)))
        now = 1200; assertTrue(t.update(stray)!!.distanceTo(q) < 0.01f)
    }

    @Test fun detectsOnPaddedRotatedCameraFrame() {
        val bmp = photoOfDocument()
        val w = bmp.width
        val h = bmp.height
        val stride = w + 32 // camera planes often pad rows
        val pixels = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val luminance = ByteArray(stride * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = pixels[y * w + x]
            luminance[y * stride + x] = ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3).toByte()
        }
        val q = DocumentDetector.detectLuminance(luminance, stride, w, h, rotationDegrees = 90)
        assertNotNull(q)
        // Sensor frames are landscape; a 90° frame is shown turned clockwise.
        assertNear(corners.map { PointF(1 - it.y / h, it.x / w) }, q!!, 0.03f)
    }

    @Test fun warpProducesUprightPage() {
        val bmp = photoOfDocument()
        val q = DocumentDetector.detect(bmp)!!
        val page = DocumentDetector.warp(bmp, q)
        val aspect = page.width.toFloat() / page.height
        assertTrue("aspect $aspect", aspect in 0.6f..0.8f)
        // Centre of the warped page is paper/ink, not the dark desk.
        assertTrue(Color.red(page.getPixel(page.width / 2, page.height / 8)) > 150)
    }

    @Test fun autoCaptureFiresOnceWhenStill() {
        var now = 0L
        val trigger = AutoCaptureTrigger(stillMs = 1300) { now }
        val q = Quad(listOf(PointF(0.1f, 0.1f), PointF(0.9f, 0.1f), PointF(0.9f, 0.9f), PointF(0.1f, 0.9f)))
        assertFalse(trigger.update(q))
        now = 700; assertFalse(trigger.update(q))
        now = 1400; assertTrue(trigger.update(q))
        now = 3000; assertFalse("must not fire twice for the same page", trigger.update(q))
        // After a shot, a still page does not fire again until it moves.
        trigger.reset()
        now = 5000; assertFalse(trigger.update(q))
        now = 7000; assertFalse(trigger.update(q))
        val moved = Quad(q.points.map { PointF(it.x + 0.1f, it.y) })
        now = 7100; assertFalse(trigger.update(moved))
        now = 8500; assertTrue(trigger.update(moved))
        // Losing the document re-arms too.
        assertFalse(trigger.update(null))
    }

    @Test fun decodesQrFromPaddedLuminancePlane() {
        val key = "BTKEY1:CPeNkNwPEnAKZAo0dHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuSHBrZVB1YmxpY0tleRIoEgYIARABGAIaIOtdQ0vP2xY7mFq9sR3uWc1zK8bNfH5aJ6pL0tVgDyEo"
        val m = QrCodes.encode(key, 360)
        val w = 480
        val h = 480
        val stride = 512
        val lum = ByteArray(stride * h) { 200.toByte() }
        for (y in 0 until m.height) for (x in 0 until m.width) {
            lum[(y + 60) * stride + x + 60] = (if (m.get(x, y)) 25 else 235).toByte()
        }
        assertEquals(key, QrCodes.decode(lum, stride, w, h))
    }

    @Test fun blackWhiteFilterBinarises() {
        val out = Filters.apply(photoOfDocument(), Filter.BW)
        val px = IntArray(out.width * out.height).also { out.getPixels(it, 0, out.width, 0, 0, out.width, out.height) }
        val binary = px.count { Color.red(it) < 30 || Color.red(it) > 225 }
        assertTrue("binary ratio ${binary.toFloat() / px.size}", binary > px.size * 0.98)
    }

    @Test fun ocrReadsTurkishText() {
        val bmp = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f }
        c.drawText("KİRA SÖZLEŞMESİ", 60f, 170f, p)
        c.drawText("Şişli, İstanbul", 60f, 330f, p)
        val text = TextNorm.normalize(OcrEngine(context).recognize(bmp, "tur+eng").text)
        assertTrue(text, "sozlesmesi" in text)
        assertTrue(text, "sisli" in text)
    }

    @Test fun ocrReadsSerifBookTextUnderShadow() {
        val bmp = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(235, 230, 220))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 35, 30); textSize = 44f; typeface = Typeface.SERIF }
        listOf("Görüşmeler çığır açan bir döneme işaret ediyordu.", "Öğretmenler şehrin güneyindeki okulda buluştu.", "Işık ağaçların arasından süzülüyordu.")
            .forEachIndexed { i, line -> c.drawText(line, 60f, 160f + i * 110f, p) }
        c.drawRect(0f, 0f, 1600f, 900f, Paint().apply {
            shader = LinearGradient(0f, 0f, 1600f, 0f, Color.TRANSPARENT, Color.argb(130, 0, 0, 0), Shader.TileMode.CLAMP)
        })
        val text = OcrEngine(context).recognize(bmp, "tur").text
        listOf("Görüşmeler", "çığır", "işaret", "Öğretmenler", "şehrin", "güneyindeki", "ağaçların", "süzülüyordu").forEach {
            assertTrue("'$it' missing in: $text", it in text)
        }
    }

    private fun pdf(build: (PDDocument) -> Unit): ByteArray = PDDocument().use { doc ->
        build(doc)
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test fun searchHighlightsLandOnRotatedPage() {
        val bytes = pdf { doc ->
            val page = PDPage(PDRectangle.A4).apply { rotation = 90 }
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 24f); cs.newLineAtOffset(72f, 700f)
                cs.showText("Merhaba kira dunya"); cs.endText()
            }
        }
        val r = PdfTools.search(bytes, null, "kira").single().rects.single()
        // Displayed page is turned clockwise: user y=700 → x≈0.83, the line runs downwards.
        assertTrue("$r", r.centerX() in 0.78f..0.9f)
        assertTrue("$r", r.height() > r.width())
        assertTrue("$r", r.top in 0.15f..0.45f)
    }

    @Test fun ocrLayerOnRotatedPageIsFoundWhereTheWordIs() {
        val bytes = pdf { doc ->
            doc.addPage(PDPage(PDRectangle.A4).apply { rotation = 90 })
            PdfTools.addTextLayer(doc, 0, "kira", "0.1000 0.2000 0.3000 0.2400 kira", TextLayer.font(context, doc))
        }
        val r = PdfTools.search(bytes, null, "kira").single().rects.single()
        assertTrue("$r", r.left in 0.05f..0.15f && r.right in 0.2f..0.35f)
        assertTrue("$r", r.top in 0.15f..0.22f && r.bottom in 0.22f..0.3f)
    }

    @Test fun viewCopyBakesAnnotationsIntoPage() {
        val bytes = pdf { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            PdfTools.addMarkup(doc, 0, PdfTools.MarkupKind.HIGHLIGHT, RectF(0.1f, 0.1f, 0.5f, 0.15f), Color.YELLOW)
            PdfTools.addNote(doc, 0, PointF(0.8f, 0.2f), "not")
        }
        PdfTools.load(bytes, null).use { assertEquals(2, it.getPage(0).annotations.size) }
        PdfTools.load(PdfTools.viewCopy(bytes, null), null).use { assertEquals(0, it.getPage(0).annotations.size) }
    }
}
