@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.graphics.PointF
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belgetarayici.io.TextLayer
import com.belgetarayici.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// --- Comments (annotations) -------------------------------------------------------------------

private enum class PdfTool(private val labelTr: String) {
    HIGHLIGHT("Vurgula"), UNDERLINE("Altını çiz"), STRIKEOUT("Üstünü çiz"), PEN("Kalem"), NOTE("Not"), TEXT("Metin"), SIGN("İmza");

    val label: String get() = tr(labelTr)
}

/** One pending change, in normalised displayed-page coordinates. */
private sealed interface PendingMark {
    class Markup(val kind: PdfTools.MarkupKind, val rect: RectF, val color: Color) : PendingMark
    class Ink(val stroke: Stroke2) : PendingMark
    class Note(val at: PointF, val text: String) : PendingMark
    class Text(val at: PointF, val text: String, val size: Float, val color: Color) : PendingMark
    class Sign(val id: String, val bitmap: android.graphics.Bitmap, val rect: RectF) : PendingMark
}

@Composable
fun PdfAnnotateScreen(vm: AppViewModel, startPage: Int) {
    val s = vm.pdf ?: run { LaunchedEffect(Unit) { vm.back() }; return }
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, s.view.pageCount - 1)) }
    var tool by remember { mutableStateOf(PdfTool.HIGHLIGHT) }
    val palette = listOf(Color(0xFFFFEB3B), Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color.Black)
    var color by remember { mutableStateOf(palette[0]) }
    val marks = remember { mutableStateListOf<PendingMark>() }
    var dragRect by remember { mutableStateOf<RectF?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var askText by remember { mutableStateOf<Pair<PdfTool, PointF>?>(null) }
    var askSign by remember { mutableStateOf<PointF?>(null) }
    val pageSize = s.view.sizes[page]
    val img = rememberPdfPage(s, page, 1600)
    val leave = DiscardGuard(marks.isNotEmpty()) { vm.back() }
    var zoom by remember(page) { mutableFloatStateOf(1f) }
    var pan by remember(page) { mutableStateOf(Offset.Zero) }
    val textPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    /** Screen-space box of a placed text, note or signature (for moving it), or null for others. */
    fun bounds(m: PendingMark, r: Rect, notePx: Float): Rect? = when (m) {
        is PendingMark.Text -> {
            val ts = m.size * r.width
            textPaint.textSize = ts
            val lines = m.text.lines()
            val w = lines.maxOf { textPaint.measureText(it) }
            val l = r.left + m.at.x * r.width
            val t = r.top + m.at.y * r.height
            Rect(l, t, l + w, t + ts * (1 + 1.25f * (lines.size - 1)) + ts * 0.3f)
        }
        is PendingMark.Note -> Offset(r.left + m.at.x * r.width, r.top + m.at.y * r.height).let { Rect(it, notePx) }
        is PendingMark.Sign -> Rect(r.left + m.rect.left * r.width, r.top + m.rect.top * r.height, r.left + m.rect.right * r.width, r.top + m.rect.bottom * r.height)
        else -> null
    }

    fun moved(m: PendingMark, dx: Float, dy: Float): PendingMark = when (m) {
        is PendingMark.Text -> PendingMark.Text(PointF((m.at.x + dx).coerceIn(0f, 0.98f), (m.at.y + dy).coerceIn(0f, 0.98f)), m.text, m.size, m.color)
        is PendingMark.Note -> PendingMark.Note(PointF((m.at.x + dx).coerceIn(0f, 1f), (m.at.y + dy).coerceIn(0f, 1f)), m.text)
        is PendingMark.Sign -> {
            val ddx = dx.coerceIn(-m.rect.left, 1 - m.rect.right)
            val ddy = dy.coerceIn(-m.rect.top, 1 - m.rect.bottom)
            PendingMark.Sign(m.id, m.bitmap, RectF(m.rect.left + ddx, m.rect.top + ddy, m.rect.right + ddx, m.rect.bottom + ddy))
        }
        else -> m
    }

    fun area(): Rect {
        if (box == IntSize.Zero) return Rect.Zero
        val sc = min(box.width.toFloat() / pageSize.width, box.height.toFloat() / pageSize.height)
        val w = pageSize.width * sc
        val h = pageSize.height * sc
        val l = (box.width - w) / 2
        val t = (box.height - h) / 2
        return Rect(l, t, l + w, t + h)
    }
    fun norm(o: Offset): PointF { val r = area(); return PointF(((o.x - r.left) / r.width).coerceIn(0f, 1f), ((o.y - r.top) / r.height).coerceIn(0f, 1f)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trf("Yorum · sayfa %s/%s", page + 1, s.view.pageCount)) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    IconButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, tr("Geri al")) }
                    TextButton(enabled = marks.isNotEmpty(), onClick = {
                        val pending = marks.toList()
                        val pageIndex = page
                        vm.editPdf(tr("Yorumlar kaydediliyor"), onDone = { marks.clear() }) { doc ->
                            val font by lazy { TextLayer.font(vm.getApplication(), doc) }
                            pending.forEach { m ->
                                when (m) {
                                    is PendingMark.Markup -> PdfTools.addMarkup(doc, pageIndex, m.kind, m.rect, m.color.toArgb())
                                    is PendingMark.Ink -> PdfTools.addInk(doc, pageIndex, listOf(m.stroke.points.map { PointF(it.x, it.y) }), m.stroke.color.toArgb(), m.stroke.width, m.stroke.color.alpha)
                                    is PendingMark.Note -> PdfTools.addNote(doc, pageIndex, m.at, m.text)
                                    is PendingMark.Text -> PdfTools.addText(doc, pageIndex, m.at, m.text, m.size, m.color.toArgb(), font)
                                    is PendingMark.Sign -> PdfTools.addImage(doc, pageIndex, m.bitmap, m.rect)
                                }
                            }
                        }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PdfTool.entries.forEach { t ->
                        FilterChip(tool == t, {
                            tool = t
                            if (t == PdfTool.HIGHLIGHT && color != palette[0]) color = palette[0]
                            if ((t == PdfTool.UNDERLINE || t == PdfTool.STRIKEOUT) && color == palette[0]) color = palette[1]
                        }, label = { Text(t.label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    palette.forEach { c ->
                        Box(Modifier.padding(4.dp).size(30.dp).background(c, CircleShape).border(if (c == color) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { color = c })
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { page-- }, enabled = page > 0 && marks.isEmpty()) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Önceki sayfa")) }
                    IconButton(onClick = { page++ }, enabled = page < s.view.pageCount - 1 && marks.isEmpty()) { Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Sonraki sayfa")) }
                }
                Text(
                    when (tool) {
                        PdfTool.HIGHLIGHT, PdfTool.UNDERLINE, PdfTool.STRIKEOUT -> tr("İşaretlenecek alanı sürükleyerek seçin")
                        PdfTool.PEN -> tr("Parmağınızla çizin")
                        PdfTool.NOTE -> tr("Not bırakılacak yere dokunun; eklediğiniz notu sürükleyerek taşıyın")
                        PdfTool.TEXT -> tr("Metin eklenecek yere dokunun; eklediğiniz metni sürükleyerek taşıyın")
                        PdfTool.SIGN -> tr("İmzanın konacağı yere dokunun; imzayı sürükleyerek taşıyın")
                    } + tr(" · İki parmakla yakınlaştırın"),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).padding(horizontal = 24.dp, vertical = 8.dp).fillMaxSize().systemGestureExclusion().clipToBounds()
                // Two fingers zoom and pan the page; this runs before the tools see the touch.
                .pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                val limX = size.width * (zoom - 1) / 2
                                val limY = size.height * (zoom - 1) / 2
                                val p = pan + event.calculatePan()
                                pan = Offset(p.x.coerceIn(-limX, limX), p.y.coerceIn(-limY, limY))
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
                .onSizeChanged { box = it }
                // Placed text, notes and signatures can be dragged to a new position.
                .pointerInput(tool, page) {
                    if (tool != PdfTool.NOTE && tool != PdfTool.TEXT && tool != PdfTool.SIGN) return@pointerInput
                    var moving = -1
                    detectDragGestures(
                        onDragStart = { o ->
                            val r = area()
                            moving = marks.indices.reversed().firstOrNull { i -> bounds(marks[i], r, 24.dp.toPx())?.inflate(12.dp.toPx())?.contains(o) == true } ?: -1
                        },
                        onDrag = { change, amount ->
                            if (moving < 0) return@detectDragGestures
                            change.consume()
                            val r = area()
                            marks[moving] = moved(marks[moving], amount.x / r.width, amount.y / r.height)
                        },
                        onDragEnd = { moving = -1 },
                        onDragCancel = { moving = -1 },
                    )
                }
                .pointerInput(tool, color, page) {
                    if (tool == PdfTool.NOTE || tool == PdfTool.TEXT || tool == PdfTool.SIGN) {
                        detectTapGestures { o ->
                            if (!area().contains(o)) return@detectTapGestures
                            if (tool == PdfTool.SIGN) askSign = norm(o) else askText = tool to norm(o)
                        }
                    } else {
                        var start = PointF()
                        detectDragGestures(
                            onDragCancel = {
                                if (tool == PdfTool.PEN && marks.lastOrNull() is PendingMark.Ink) marks.removeAt(marks.lastIndex)
                                dragRect = null
                            },
                            onDragStart = { o ->
                                start = norm(o)
                                if (tool == PdfTool.PEN) marks += PendingMark.Ink(Stroke2(color, 0.004f).also { it.points.add(Offset(start.x, start.y)) })
                            },
                            onDrag = { change, _ ->
                                val p = norm(change.position)
                                if (tool == PdfTool.PEN) (marks.lastOrNull() as? PendingMark.Ink)?.stroke?.points?.add(Offset(p.x, p.y))
                                else dragRect = RectF(min(start.x, p.x), min(start.y, p.y), maxOf(start.x, p.x), maxOf(start.y, p.y))
                                change.consume()
                            },
                            onDragEnd = {
                                dragRect?.takeIf { it.width() > 0.005f && it.height() > 0.003f }?.let { r ->
                                    val kind = when (tool) {
                                        PdfTool.UNDERLINE -> PdfTools.MarkupKind.UNDERLINE
                                        PdfTool.STRIKEOUT -> PdfTools.MarkupKind.STRIKEOUT
                                        else -> PdfTools.MarkupKind.HIGHLIGHT
                                    }
                                    marks += PendingMark.Markup(kind, r, color)
                                }
                                dragRect = null
                            },
                        )
                    }
                },
        ) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else CircularProgressIndicator(Modifier.align(Alignment.Center))
            Canvas(Modifier.fillMaxSize()) {
                val r = area()
                fun x(v: Float) = r.left + v * r.width
                fun y(v: Float) = r.top + v * r.height
                fun drawMarkup(kind: PdfTools.MarkupKind, rf: RectF, c: Color) {
                    when (kind) {
                        PdfTools.MarkupKind.HIGHLIGHT -> drawRect(c.copy(alpha = 0.4f), Offset(x(rf.left), y(rf.top)), androidx.compose.ui.geometry.Size(rf.width() * r.width, rf.height() * r.height))
                        PdfTools.MarkupKind.UNDERLINE -> drawLine(c, Offset(x(rf.left), y(rf.bottom)), Offset(x(rf.right), y(rf.bottom)), 3f)
                        PdfTools.MarkupKind.STRIKEOUT -> drawLine(c, Offset(x(rf.left), y(rf.centerY())), Offset(x(rf.right), y(rf.centerY())), 3f)
                    }
                }
                marks.forEach { m ->
                    when (m) {
                        is PendingMark.Markup -> drawMarkup(m.kind, m.rect, m.color)
                        is PendingMark.Ink -> {
                            val path = Path()
                            m.stroke.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.x), y(p.y)) else path.lineTo(x(p.x), y(p.y)) }
                            drawPath(path, m.stroke.color, style = Stroke(m.stroke.width * r.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        is PendingMark.Note -> drawCircle(Color(0xFFFFC107), 14.dp.toPx(), Offset(x(m.at.x), y(m.at.y)))
                        is PendingMark.Text -> {
                            val ts = m.size * r.width
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = ts; this.color = m.color.toArgb() }
                            m.text.lines().forEachIndexed { i, line -> drawContext.canvas.nativeCanvas.drawText(line, x(m.at.x), y(m.at.y) + ts * (1 + 1.25f * i), paint) }
                        }
                        is PendingMark.Sign -> drawImage(m.bitmap.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(x(m.rect.left).toInt(), y(m.rect.top).toInt()),
                            dstSize = IntSize((m.rect.width() * r.width).toInt().coerceAtLeast(1), (m.rect.height() * r.height).toInt().coerceAtLeast(1)))
                    }
                }
                dragRect?.let { drawMarkup(when (tool) { PdfTool.UNDERLINE -> PdfTools.MarkupKind.UNDERLINE; PdfTool.STRIKEOUT -> PdfTools.MarkupKind.STRIKEOUT; else -> PdfTools.MarkupKind.HIGHLIGHT }, it, color) }
            }
        }
    }

    askText?.let { (t, at) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askText = null },
            title = { Text(if (t == PdfTool.NOTE) tr("Not") else tr("Metin ekle")) },
            text = { OutlinedTextField(text, { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    askText = null
                    marks += if (t == PdfTool.NOTE) PendingMark.Note(at, text) else PendingMark.Text(at, text, 0.025f, if (color == palette[0]) Color.Black else color)
                }) { Text(tr("Ekle")) }
            },
            dismissButton = { TextButton(onClick = { askText = null }) { Text(tr("Vazgeç")) } },
        )
    }
    askSign?.let { at ->
        val sigs by produceState<List<Pair<String, android.graphics.Bitmap>>?>(null) {
            value = withContext(Dispatchers.IO) { vm.c.repo.signatures().map { it to vm.c.repo.signature(it) } }
        }
        AlertDialog(
            onDismissRequest = { askSign = null },
            title = { Text(tr("İmza seçin")) },
            text = {
                val list = sigs
                when {
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text(tr("Kayıtlı imza yok. Taramalar sekmesinde bir sayfanın İmza aracıyla imza çizebilirsiniz."))
                    else -> Column {
                        list.forEach { (id, bmp) ->
                            Image(bmp.asImageBitmap(), tr("İmza"), Modifier.fillMaxWidth().height(70.dp).background(Color.White).clickable {
                                askSign = null
                                val w = 0.3f
                                val h = w * bmp.height / bmp.width * pageSize.width / pageSize.height
                                marks += PendingMark.Sign(id, bmp, RectF((at.x - w / 2).coerceIn(0f, 1 - w), (at.y - h / 2).coerceIn(0f, 1 - h), 0f, 0f).let { RectF(it.left, it.top, it.left + w, it.top + h) })
                            }.padding(6.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askSign = null }) { Text(tr("Vazgeç")) } },
        )
    }
}

// --- Form filling -----------------------------------------------------------------------------

@Composable
fun PdfFormScreen(vm: AppViewModel) {
    val s = vm.pdf ?: run { LaunchedEffect(Unit) { vm.back() }; return }
    val fields by produceState<List<PdfTools.FormField>?>(null, s.version) {
        value = withContext(Dispatchers.Default) { runCatching { PdfTools.formFields(s.bytes, s.password) }.getOrDefault(emptyList()) }
    }
    val values = remember { mutableStateMapOf<String, String>() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Formu doldur")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    TextButton(enabled = values.isNotEmpty(), onClick = {
                        val v = values.toMap()
                        vm.editPdf(tr("Form kaydediliyor"), onDone = { vm.back() }) { doc -> PdfTools.fillForm(doc, v, TextLayer.font(vm.getApplication(), doc, full = true)) }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
    ) { padding ->
        val list = fields
        when {
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                tr("Bu PDF'de doldurulabilir form alanı yok. Formu doldurmak için Yorum ekranındaki “Metin” aracıyla istediğiniz yere yazı ve “İmza” ile imza ekleyebilirsiniz."),
                Modifier.padding(padding).padding(24.dp),
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(list) { _, f ->
                    when (f) {
                        is PdfTools.FormField.Text -> OutlinedTextField(values[f.name] ?: f.value, { values[f.name] = it }, label = { Text(f.label) },
                            singleLine = !f.multiline, minLines = if (f.multiline) 3 else 1, modifier = Modifier.fillMaxWidth())
                        is PdfTools.FormField.Check -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox((values[f.name] ?: f.checked.toString()) == "true", { values[f.name] = it.toString() })
                            Text(f.label)
                        }
                        is PdfTools.FormField.Choice -> Column {
                            Text(f.label, fontWeight = FontWeight.Bold)
                            f.options.forEach { o -> RadioRow(o, (values[f.name] ?: f.value) == o) { values[f.name] = o } }
                        }
                    }
                }
            }
        }
    }
}

// --- Page organizer ---------------------------------------------------------------------------

@Composable
fun PdfPagesScreen(vm: AppViewModel) {
    val s = vm.pdf ?: run { LaunchedEffect(Unit) { vm.back() }; return }
    val order = remember(s.version) { mutableStateListOf<Int>().apply { addAll(0 until s.view.pageCount) } }
    val rotations = remember(s.version) { mutableStateMapOf<Int, Int>() }
    val selected = remember(s.version) { mutableStateListOf<Int>() }
    var inserting by remember { mutableStateOf(false) }
    val dirty = order != (0 until s.view.pageCount).toList() || rotations.values.any { it % 360 != 0 }
    val leave = DiscardGuard(dirty) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) tr("Sayfaları düzenle") else trf("%s sayfa seçili", selected.size)) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected.forEach { rotations[it] = (rotations[it] ?: 0) + 90 } }) { Icon(Icons.AutoMirrored.Filled.RotateRight, tr("Döndür")) }
                        IconButton(onClick = {
                            val pick = order.filter { it in selected }
                            vm.work(tr("Ayıklanıyor")) { vm.createPdf(trf("%s (ayıklanan)", s.name), PdfTools.extractPages(s.bytes, s.password, pick)) }
                        }) { Icon(Icons.Default.ContentCut, tr("Yeni PDF'ye ayıkla")) }
                        IconButton(onClick = {
                            if (selected.size >= order.size) vm.toast(tr("En az bir sayfa kalmalı")) else { order.removeAll(selected); selected.clear() }
                        }) { Icon(Icons.Default.Delete, tr("Sil")) }
                    } else {
                        IconButton(onClick = { inserting = true }) { Icon(Icons.Default.Add, tr("PDF ekle")) }
                    }
                    TextButton(enabled = dirty, onClick = {
                        val o = order.toList()
                        val r = rotations.toMap()
                        vm.editPdf(tr("Sayfalar kaydediliyor"), onDone = { vm.back() }) { doc -> PdfTools.arrangePages(doc, o, r) }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.padding(padding), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(order, key = { _, p -> p }) { i, p ->
                Column {
                    val size = s.view.sizes[p]
                    val img = rememberPdfPage(s, p, 320)
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clickable { if (p in selected) selected.remove(p) else selected.add(p) }, contentAlignment = Alignment.Center) {
                        if (img != null) Image(img, trf("Sayfa %s", p + 1), Modifier.fillMaxSize().padding(4.dp).graphicsLayer(
                            rotationZ = (rotations[p] ?: 0).toFloat(),
                            // A quarter turn swaps width and height; shrink so it stays in the cell.
                            scaleX = if ((rotations[p] ?: 0) % 180 != 0) 0.75f else 1f,
                            scaleY = if ((rotations[p] ?: 0) % 180 != 0) 0.75f else 1f,
                        ), contentScale = ContentScale.Fit)
                        if (p in selected) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilledTonalIconButton(onClick = { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Öne al")) }
                        FilledTonalIconButton(onClick = { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.lastIndex) { Icon(Icons.AutoMirrored.Filled.ArrowForward, tr("Geriye al")) }
                    }
                    if (size.width > size.height) Text(tr("yatay"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }

    if (inserting) {
        PdfPickerDialog(vm, multiple = false, title = tr("Sona eklenecek PDF"), onDismiss = { inserting = false }) { chosen ->
            inserting = false
            if (dirty) { vm.toast(tr("Önce sıralamayı kaydedin")); return@PdfPickerDialog }
            val other = chosen.first()
            vm.work(tr("Sayfalar ekleniyor")) {
                val merged = PdfTools.combine(listOf(s.bytes to s.password, vm.c.pdfs.bytes(other.id) to null))
                // Keep the current file's protection, if any.
                val out = if (s.password.isNullOrEmpty()) merged else PdfTools.protect(merged, null, s.password)
                vm.savePdf(s, out)
            }
        }
    }
}

// --- Reading mode + read aloud ----------------------------------------------------------------

@Composable
fun PdfReflowScreen(vm: AppViewModel, startPage: Int) {
    val s = vm.pdf ?: run { LaunchedEffect(Unit) { vm.back() }; return }
    val context = LocalContext.current
    val texts by produceState<List<String>?>(null, s.version) { value = withContext(Dispatchers.Default) { runCatching { s.pageTexts }.getOrDefault(emptyList()) } }
    var fontSize by remember { mutableStateOf(18f) }
    val list = rememberLazyListState()
    var speaking by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var speechLang by remember { mutableStateOf(vm.c.settings.speechLanguage) }

    DisposableEffect(Unit) {
        // System text-to-speech; voices are on-device, the app itself has no network access.
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) ttsReady = true
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }
    LaunchedEffect(texts) { if (texts != null && startPage > 0) list.scrollToItem(startPage.coerceAtMost((texts?.size ?: 1) - 1)) }

    fun speakFrom(page: Int) {
        val t = tts ?: return
        val pages = texts ?: return
        t.stop()
        val locale = speechLocale(speechLang, pages.drop(page).take(3).joinToString(" ").take(4000))
        if (t.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            vm.toast(tr("Bu dil için cihazda ses yok. Telefon ayarları → Metin okuma çıkışı'ndan ses paketini indirin."))
            return
        }
        pages.drop(page).forEachIndexed { i, text ->
            text.split(Regex("(?<=[.!?])\\s+|\\n{2,}")).filter { it.isNotBlank() }.forEach { sentence ->
                t.speak(sentence.replace('\n', ' '), TextToSpeech.QUEUE_ADD, null, "p${page + i}")
            }
        }
        speaking = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Okuma modu")) },
                navigationIcon = { IconButton(onClick = { tts?.stop(); vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    // Voice language: automatic / Turkish / English (remembered in Settings).
                    TextButton(onClick = {
                        speechLang = when (speechLang) { "auto" -> "tr"; "tr" -> "en"; else -> "auto" }
                        vm.c.settings.speechLanguage = speechLang
                        if (speaking) speakFrom(list.firstVisibleItemIndex)
                    }) { Text(tr(SPEECH_LANGS.getValue(speechLang))) }
                    IconButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12f) }) { Icon(Icons.Default.TextDecrease, tr("Küçült")) }
                    IconButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(36f) }) { Icon(Icons.Default.TextIncrease, tr("Büyüt")) }
                    IconButton(enabled = ttsReady, onClick = { if (speaking) { tts?.stop(); speaking = false } else speakFrom(list.firstVisibleItemIndex) }) {
                        Icon(if (speaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp, if (speaking) tr("Durdur") else tr("Sesli oku"))
                    }
                },
            )
        },
    ) { padding ->
        val pages = texts
        when {
            pages == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            pages.all { it.isBlank() } -> Text(tr("Bu PDF'de metin yok (taranmış olabilir). Önce ⋮ → “Metni tanı (OCR)” ile metin çıkarın."), Modifier.padding(padding).padding(24.dp))
            else -> SelectionContainer(Modifier.padding(padding)) {
                LazyColumn(state = list, contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(pages) { i, text ->
                        Text(trf("Sayfa %s", i + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(text.ifBlank { tr("(bu sayfada metin yok)") }, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    }
                }
            }
        }
    }
}

/** Read-aloud voice choices; labels are Turkish message keys (show them through [tr]). */
val SPEECH_LANGS = linkedMapOf("auto" to "Otomatik", "tr" to "Türkçe", "en" to "English")

/** Voice for [setting]; "auto" picks Turkish when the text has Turkish letters, English when it reads like English. */
fun speechLocale(setting: String, sample: String): Locale = when (setting) {
    "tr" -> Locale.forLanguageTag("tr-TR")
    "en" -> Locale.forLanguageTag("en-US")
    else -> {
        val letters = sample.count { it.isLetter() }.coerceAtLeast(1)
        val turkish = sample.count { it in "çğıİöşüÇĞÖŞÜ" }
        val english = Regex("\\b(the|and|of|to|is|that|with|for)\\b", RegexOption.IGNORE_CASE).findAll(sample).count()
        if (turkish * 200 < letters && english >= 3) Locale.forLanguageTag("en-US") else Locale.forLanguageTag("tr-TR")
    }
}
