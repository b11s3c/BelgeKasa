package com.belgetarayici.pdf

import com.belgetarayici.i18n.tr
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.belgetarayici.io.TextLayer
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.belgetarayici.data.TextNorm
import java.io.ByteArrayOutputStream
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * PDF operations on whole-file byte arrays (reader/editor feature set, offline, PdfBox).
 * Coordinates handed in/out of the UI are normalised to the page as displayed: (0,0) top-left,
 * (1,1) bottom-right, after the page's /Rotate is applied.
 */
object PdfTools {
    class NeedsPassword : Exception(tr("Bu PDF parola ile korunuyor"))

    data class Info(val pages: Int, val text: String, val encrypted: Boolean)
    data class OutlineEntry(val title: String, val page: Int, val level: Int)
    data class Hit(val page: Int, val rects: List<RectF>)

    sealed interface FormField {
        val name: String
        val label: String
        data class Text(override val name: String, override val label: String, val value: String, val multiline: Boolean) : FormField
        data class Check(override val name: String, override val label: String, val checked: Boolean) : FormField
        data class Choice(override val name: String, override val label: String, val value: String, val options: List<String>) : FormField
    }

    fun load(bytes: ByteArray, password: String?): PDDocument = try {
        PDDocument.load(bytes, password.orEmpty())
    } catch (_: InvalidPasswordException) {
        throw NeedsPassword()
    }

    fun inspect(bytes: ByteArray, password: String?): Info = load(bytes, password).use { doc ->
        Info(doc.numberOfPages, runCatching { textOf(doc) }.getOrDefault(""), doc.isEncrypted)
    }

    /** Unencrypted copy (the platform renderer cannot open protected files). */
    fun decryptedForView(bytes: ByteArray, password: String?): ByteArray = load(bytes, password).use { doc ->
        if (!doc.isEncrypted) return bytes
        doc.isAllSecurityToBeRemoved = true
        save(doc)
    }

    /**
     * In-memory copy for on-screen rendering. Android's PdfRenderer does not draw annotations
     * (comments made here or in other readers would be invisible), so each annotation's appearance stream
     * is drawn into its page and the annotation removed — in this copy only; the stored file keeps
     * real, editable annotations.
     */
    fun viewCopy(bytes: ByteArray, password: String?): ByteArray = load(bytes, password).use { doc ->
        var changed = doc.isEncrypted
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
        for (page in doc.pages) {
            val annotations = page.annotations
            val flat = annotations.filter { a ->
                a.subtype != "Link" && a.subtype != "Popup" && !a.isHidden && !a.isNoView && a.normalAppearanceStream != null
            }
            if (flat.isEmpty()) continue
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                flat.forEach { runCatching { drawAppearance(cs, it) } }
            }
            page.annotations = annotations - flat.toSet()
            changed = true
        }
        if (changed) save(doc) else bytes
    }

    /** Places an annotation's normal appearance into its rectangle (PDF 32000 §12.5.5). */
    private fun drawAppearance(cs: PDPageContentStream, a: com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation) {
        val ap = a.normalAppearanceStream ?: return
        val rect = a.rectangle ?: return
        val box = ap.bBox ?: return
        val m = ap.matrix
        val xs = ArrayList<Float>(4)
        val ys = ArrayList<Float>(4)
        for ((x, y) in listOf(box.lowerLeftX to box.lowerLeftY, box.upperRightX to box.lowerLeftY, box.lowerLeftX to box.upperRightY, box.upperRightX to box.upperRightY)) {
            xs += m.getValue(0, 0) * x + m.getValue(1, 0) * y + m.getValue(2, 0)
            ys += m.getValue(0, 1) * x + m.getValue(1, 1) * y + m.getValue(2, 1)
        }
        val bw = xs.max() - xs.min()
        val bh = ys.max() - ys.min()
        if (bw <= 0f || bh <= 0f) return
        val sx = rect.width / bw
        val sy = rect.height / bh
        cs.saveGraphicsState()
        cs.transform(com.tom_roush.pdfbox.util.Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - xs.min() * sx, rect.lowerLeftY - ys.min() * sy))
        cs.drawForm(ap)
        cs.restoreGraphicsState()
    }

    /** Loads, applies [block] and saves. A protected file stays protected with the same password. */
    fun edit(bytes: ByteArray, password: String?, block: (PDDocument) -> Unit): ByteArray = load(bytes, password).use { doc ->
        val wasEncrypted = doc.isEncrypted
        if (wasEncrypted) doc.isAllSecurityToBeRemoved = true
        block(doc)
        if (wasEncrypted && !password.isNullOrEmpty()) protectDoc(doc, password)
        save(doc)
    }

    private fun save(doc: PDDocument): ByteArray = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()

    fun textOf(doc: PDDocument): String = PDFTextStripper().getText(doc)

    fun pageTexts(bytes: ByteArray, password: String?): List<String> = load(bytes, password).use { doc ->
        val stripper = PDFTextStripper()
        (1..doc.numberOfPages).map { n ->
            stripper.startPage = n
            stripper.endPage = n
            runCatching { stripper.getText(doc).trim() }.getOrDefault("")
        }
    }

    // --- navigation ---

    fun outline(bytes: ByteArray, password: String?): List<OutlineEntry> = load(bytes, password).use { doc ->
        val root = doc.documentCatalog.documentOutline ?: return emptyList()
        val out = ArrayList<OutlineEntry>()
        fun walk(node: PDOutlineNode, level: Int) {
            for (item in node.children()) {
                val page = runCatching {
                    val dest = item.destination ?: (item.action as? com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo)?.destination
                    when (dest) {
                        is PDPageDestination -> dest.page?.let(doc.pages::indexOf) ?: dest.pageNumber
                        is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination ->
                            doc.documentCatalog.findNamedDestinationPage(dest)?.let { it.page?.let(doc.pages::indexOf) ?: it.pageNumber }
                        else -> null
                    }
                }.getOrNull() ?: -1
                out += OutlineEntry(item.title.orEmpty(), page, level)
                if (out.size < 2000) walk(item, level + 1)
            }
        }
        walk(root, 0)
        out
    }

    /** Diacritic- and case-insensitive search returning match rectangles per page. */
    fun search(bytes: ByteArray, password: String?, query: String): List<Hit> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        return load(bytes, password).use { doc ->
            val hits = ArrayList<Hit>()
            for (i in 0 until doc.numberOfPages) {
                // null separates text runs (becomes a space in the searchable string)
                val positions = ArrayList<TextPosition?>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                        positions += textPositions
                        positions += null
                    }
                }
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                runCatching { stripper.getText(doc) }
                val folded = positions.joinToString("") { tp -> tp?.unicode?.let { fold(it).take(1) }?.ifEmpty { " " } ?: " " }
                val rects = ArrayList<RectF>()
                var from = folded.indexOf(q)
                while (from >= 0 && rects.size < 500) {
                    val span = positions.subList(from, from + q.length).filterNotNull()
                    if (span.isNotEmpty()) rects += displayRect(doc.getPage(i), span)
                    from = folded.indexOf(q, from + q.length)
                }
                if (rects.isNotEmpty()) hits += Hit(i, rects)
            }
            hits
        }
    }

    /**
     * Glyph run → displayed-page rectangle. Built in user space from the text rendering matrix and
     * then mapped through the page's /Rotate, so highlights also land right on rotated pages.
     */
    private fun displayRect(page: PDPage, span: List<TextPosition>): RectF {
        // Each glyph box follows its own baseline direction (OCR text on rotated pages runs along y).
        val corners = span.flatMap { tp ->
            val m = tp.textMatrix
            val (dx, dy) = unit(m.getValue(0, 0), m.getValue(0, 1))
            val (ux, uy) = unit(m.getValue(1, 0), m.getValue(1, 1))
            val ox = m.translateX
            val oy = m.translateY
            val w = tp.widthDirAdj
            val h = max(tp.heightDir, tp.fontSizeInPt * 0.7f)
            listOf(
                PointF(ox - ux * 2f, oy - uy * 2f), PointF(ox + dx * w - ux * 2f, oy + dy * w - uy * 2f),
                PointF(ox + ux * h, oy + uy * h), PointF(ox + dx * w + ux * h, oy + dy * w + uy * h),
            ).map { fromUser(page, it) }
        }
        return RectF(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
    }

    private fun unit(x: Float, y: Float): Pair<Float, Float> {
        val len = kotlin.math.sqrt(x * x + y * y)
        return if (len == 0f) 1f to 0f else x / len to y / len
    }

    /** PDF user space → displayed-page normalised point (inverse of [toUser]). */
    fun fromUser(page: PDPage, u: PointF): PointF {
        val box = page.cropBox
        val fx = (u.x - box.lowerLeftX) / box.width
        val fy = (u.y - box.lowerLeftY) / box.height
        return when (rotation(page)) {
            90 -> PointF(fy, fx)
            180 -> PointF(1 - fx, fy)
            270 -> PointF(1 - fy, 1 - fx)
            else -> PointF(fx, 1 - fy)
        }
    }

    /** Per-character fold that keeps string length (needed to map matches back to glyphs). */
    private fun fold(s: String): String = buildString(s.length) {
        for (c in s) append(TextNorm.normalize(c.toString()).firstOrNull() ?: c)
    }

    // --- coordinates ---

    /** Displayed-page normalised point → PDF user space of [page]. */
    fun toUser(page: PDPage, p: PointF): PointF {
        val box = page.cropBox
        val w = box.width
        val h = box.height
        return when (rotation(page)) {
            90 -> PointF(box.lowerLeftX + p.y * w, box.lowerLeftY + p.x * h)
            180 -> PointF(box.lowerLeftX + (1 - p.x) * w, box.lowerLeftY + p.y * h)
            270 -> PointF(box.upperRightX - p.y * w, box.upperRightY - p.x * h)
            else -> PointF(box.lowerLeftX + p.x * w, box.lowerLeftY + (1 - p.y) * h)
        }
    }

    private fun toUser(page: PDPage, r: RectF): PDRectangle {
        val a = toUser(page, PointF(r.left, r.top))
        val b = toUser(page, PointF(r.right, r.bottom))
        return PDRectangle(min(a.x, b.x), min(a.y, b.y), kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y))
    }

    private fun color(argb: Int) = PDColor(
        floatArrayOf(((argb shr 16) and 0xff) / 255f, ((argb shr 8) and 0xff) / 255f, (argb and 0xff) / 255f),
        PDDeviceRGB.INSTANCE,
    )

    // --- annotations (kept as standard PDF annotations, editable in other readers) ---

    enum class MarkupKind(val subtype: String) {
        HIGHLIGHT(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT),
        UNDERLINE(PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE),
        STRIKEOUT(PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT),
    }

    fun addMarkup(doc: PDDocument, pageIndex: Int, kind: MarkupKind, rect: RectF, argb: Int) {
        val page = doc.getPage(pageIndex)
        val r = toUser(page, rect)
        val a = PDAnnotationTextMarkup(kind.subtype)
        a.rectangle = r
        // Quad order per spec as most readers expect: top-left, top-right, bottom-left, bottom-right.
        a.quadPoints = floatArrayOf(r.lowerLeftX, r.upperRightY, r.upperRightX, r.upperRightY, r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.lowerLeftY)
        a.color = color(argb)
        if (kind == MarkupKind.HIGHLIGHT) a.constantOpacity = 0.4f
        stamp(a)
        page.annotations.add(a)
        a.constructAppearances(doc)
    }

    /** Freehand ink; [strokes] are normalised point lists, [width] a fraction of page width. */
    fun addInk(doc: PDDocument, pageIndex: Int, strokes: List<List<PointF>>, argb: Int, width: Float, opacity: Float) {
        if (strokes.isEmpty()) return
        val page = doc.getPage(pageIndex)
        val user = strokes.map { s -> s.map { toUser(page, it) } }
        val a = PDAnnotationMarkup()
        a.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        a.inkList = user.map { s -> s.flatMap { listOf(it.x, it.y) }.toFloatArray() }.toTypedArray()
        val all = user.flatten()
        val lw = width * page.cropBox.width
        a.rectangle = PDRectangle(all.minOf { it.x } - lw, all.minOf { it.y } - lw, all.maxOf { it.x } - all.minOf { it.x } + 2 * lw, all.maxOf { it.y } - all.minOf { it.y } + 2 * lw)
        a.color = color(argb)
        a.constantOpacity = opacity
        a.borderStyle = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply { this.width = lw }
        stamp(a)
        page.annotations.add(a)
        a.constructAppearances(doc)
    }

    /** Sticky note (comment) icon with text, shown as a popup in PDF readers. */
    fun addNote(doc: PDDocument, pageIndex: Int, at: PointF, text: String) {
        val page = doc.getPage(pageIndex)
        val p = toUser(page, at)
        val a = PDAnnotationText()
        a.rectangle = PDRectangle(p.x, p.y - 20f, 20f, 20f)
        a.contents = text
        a.name = PDAnnotationText.NAME_COMMENT
        a.color = color(0xFFFFC107.toInt())
        stamp(a)
        page.annotations.add(a)
        a.constructAppearances(doc)
    }

    private fun stamp(a: PDAnnotationMarkup) {
        a.titlePopup = "BelgeKasa"
        a.creationDate = Calendar.getInstance()
        a.isPrinted = true
    }

    /** Typed text written onto the page ("fill & sign" text), upright on the displayed page. */
    fun addText(doc: PDDocument, pageIndex: Int, at: PointF, text: String, sizeFraction: Float, argb: Int, font: PDFont) {
        val page = doc.getPage(pageIndex)
        val rot = rotation(page)
        val box = page.cropBox
        val (dispW, dispH) = if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
        val size = sizeFraction * dispW
        val (a, b, c, d) = upright(rot)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setNonStrokingColor(color(argb))
            cs.beginText()
            cs.setFont(font, size)
            text.lines().forEachIndexed { i, line ->
                val baseline = toUser(page, PointF(at.x, at.y + (size + i * size * 1.25f) / dispH))
                TextLayer.encodable(font, line)?.let {
                    cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix(a, b, c, d, baseline.x, baseline.y))
                    cs.showText(it)
                }
            }
            cs.endText()
        }
    }

    private fun rotation(page: PDPage) = ((page.rotation % 360) + 360) % 360

    /** Draws an image (signature, stamp) into the page at a displayed-page normalised rectangle. */
    fun addImage(doc: PDDocument, pageIndex: Int, image: Bitmap, rect: RectF) {
        val page = doc.getPage(pageIndex)
        val r = toUser(page, rect)
        val x = LosslessFactory.createFromImage(doc, image)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            val rot = rotation(page)
            if (rot == 0) {
                cs.drawImage(x, r.lowerLeftX, r.lowerLeftY, r.width, r.height)
            } else {
                // Counter-rotate so the image appears upright on the displayed page.
                val cx = r.lowerLeftX + r.width / 2
                val cy = r.lowerLeftY + r.height / 2
                val (w, h) = if (rot == 90 || rot == 270) r.height to r.width else r.width to r.height
                cs.saveGraphicsState()
                cs.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(cx, cy))
                cs.transform(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), 0f, 0f))
                cs.drawImage(x, -w / 2, -h / 2, w, h)
                cs.restoreGraphicsState()
            }
        }
    }

    // --- forms (AcroForm) ---

    fun formFields(bytes: ByteArray, password: String?): List<FormField> = load(bytes, password).use { doc ->
        val form = doc.documentCatalog.acroForm ?: return emptyList()
        form.fieldTree.mapNotNull { f ->
            val label = f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: f.fullyQualifiedName
            when (f) {
                is PDTextField -> FormField.Text(f.fullyQualifiedName, label, f.value.orEmpty(), f.isMultiline)
                is PDCheckBox -> FormField.Check(f.fullyQualifiedName, label, f.isChecked)
                is PDRadioButton -> FormField.Choice(f.fullyQualifiedName, label, f.value.orEmpty(), f.onValues.toList())
                is PDChoice -> FormField.Choice(f.fullyQualifiedName, label, f.value.firstOrNull().orEmpty(), f.optionsDisplayValues)
                else -> null
            }
        }
    }

    fun fillForm(doc: PDDocument, values: Map<String, String>, font: PDFont) {
        val form = doc.documentCatalog.acroForm ?: return
        // Turkish characters need an embedded Unicode font for the field appearances.
        val resources = form.defaultResources ?: PDResources().also { form.defaultResources = it }
        val fontName = resources.add(font)
        for ((name, value) in values) {
            val f = form.getField(name) ?: continue
            runCatching {
                when (f) {
                    is PDCheckBox -> if (value == "true") f.check() else f.unCheck()
                    is PDTextField -> {
                        f.defaultAppearance = "/${fontName.name} 12 Tf 0 g"
                        f.value = value
                    }
                    is PDRadioButton, is PDChoice -> f.setValue(value)
                }
            }
        }
    }

    // --- pages ---

    /** Rebuilds the page list: [order] holds original page indices, [rotations] extra degrees. */
    fun arrangePages(doc: PDDocument, order: List<Int>, rotations: Map<Int, Int>) {
        val pages = (0 until doc.numberOfPages).map(doc::getPage)
        order.forEach { i -> rotations[i]?.let { pages[i].rotation = ((pages[i].rotation + it) % 360 + 360) % 360 } }
        val tree = doc.pages
        pages.forEach { tree.remove(it) }
        order.forEach { tree.add(pages[it]) }
    }

    fun extractPages(bytes: ByteArray, password: String?, indices: List<Int>): ByteArray = load(bytes, password).use { src ->
        src.isAllSecurityToBeRemoved = true
        PDDocument().use { out ->
            indices.forEach { out.importPage(src.getPage(it)) }
            save(out)
        }
    }

    /** Appends documents (each `bytes to password`) into one new PDF. */
    fun combine(parts: List<Pair<ByteArray, String?>>): ByteArray {
        val sources = parts.map { (b, pw) -> load(b, pw).also { it.isAllSecurityToBeRemoved = true } }
        try {
            PDDocument().use { out ->
                for (src in sources) for (page in src.pages) out.importPage(page)
                return save(out)
            }
        } finally {
            sources.forEach { it.close() }
        }
    }

    /** Re-encodes large images as JPEG; vector content and text are untouched. Returns saved bytes. */
    fun compress(bytes: ByteArray, password: String?, maxEdge: Int, quality: Float): ByteArray = edit(bytes, password) { doc ->
        val seen = HashSet<Any>()
        fun shrink(res: PDResources?, depth: Int) {
            if (res == null || depth > 4) return
            for (name in res.xObjectNames) {
                val x = runCatching { res.getXObject(name) }.getOrNull() ?: continue
                if (!seen.add(x.cosObject)) continue
                when (x) {
                    is PDImageXObject -> runCatching {
                        if (x.width * x.height < 200_000 || x.isStencil) return@runCatching
                        val img = x.image ?: return@runCatching
                        val s = min(1f, maxEdge.toFloat() / max(img.width, img.height))
                        val scaled = if (s < 1f) Bitmap.createScaledBitmap(img, (img.width * s).toInt(), (img.height * s).toInt(), true) else img
                        res.put(name, JPEGFactory.createFromImage(doc, scaled, quality))
                    }
                    is PDFormXObject -> shrink(x.resources, depth + 1)
                }
            }
        }
        doc.pages.forEach { shrink(it.resources, 0) }
    }

    private fun protectDoc(doc: PDDocument, password: String) {
        val policy = StandardProtectionPolicy(password, password, AccessPermission())
        policy.encryptionKeyLength = 256
        doc.protect(policy)
    }

    fun protect(bytes: ByteArray, currentPassword: String?, newPassword: String): ByteArray = load(bytes, currentPassword).use { doc ->
        doc.isAllSecurityToBeRemoved = true
        protectDoc(doc, newPassword)
        save(doc)
    }

    fun unprotect(bytes: ByteArray, password: String?): ByteArray = decryptedForView(bytes, password)

    /**
     * Invisible OCR text over a page that has no text of its own. Word boxes come from OCR of the
     * displayed (rotated) page, so each word is placed and turned upright for the page's /Rotate.
     */
    fun addTextLayer(doc: PDDocument, pageIndex: Int, text: String, words: String, font: PDFont) {
        val page = doc.getPage(pageIndex)
        val box = page.cropBox
        val rot = rotation(page)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            if (rot == 0) {
                TextLayer.write(cs, font, text, words, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
                return
            }
            val (dispW, dispH) = if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
            val (a, b, c, d) = upright(rot)
            cs.beginText()
            cs.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
            for (w in TextLayer.words(words)) {
                val word = TextLayer.encodable(font, w.text) ?: continue
                val size = ((w.b - w.t) * dispH).coerceIn(2f, 72f)
                val natural = font.getStringWidth(word) / 1000f * size
                val origin = toUser(page, PointF(w.l, w.b))
                cs.setFont(font, size)
                if (natural > 0) cs.setHorizontalScaling(((w.r - w.l) * dispW / natural * 100f).coerceIn(10f, 500f))
                cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix(a, b, c, d, origin.x, origin.y))
                cs.showText(TextLayer.encodable(font, "$word ") ?: word)
            }
            cs.endText()
        }
    }

    /** Text-space x axis and "up" as user-space unit vectors, so text reads upright on screen. */
    private fun upright(rot: Int): List<Float> = when (rot) {
        90 -> listOf(0f, 1f, -1f, 0f)
        180 -> listOf(-1f, 0f, 0f, -1f)
        270 -> listOf(0f, -1f, 1f, 0f)
        else -> listOf(1f, 0f, 0f, 1f)
    }

    fun pageCount(bytes: ByteArray, password: String?) = load(bytes, password).use { it.numberOfPages }
}
