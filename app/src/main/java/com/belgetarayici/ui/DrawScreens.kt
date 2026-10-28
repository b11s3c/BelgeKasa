@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.PageComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** A freehand stroke in normalised [0,1] coordinates of the drawing surface. */
class Stroke2(val color: Color, val width: Float, val points: MutableList<Offset> = mutableStateListOf())

private fun fitRect(box: IntSize, w: Int, h: Int): Rect {
    if (box == IntSize.Zero || w == 0 || h == 0) return Rect.Zero
    val s = min(box.width.toFloat() / w, box.height.toFloat() / h)
    val l = (box.width - w * s) / 2
    val t = (box.height - h * s) / 2
    return Rect(l, t, l + w * s, t + h * s)
}

/** Renders strokes onto a bitmap; [widthScale] converts normalised widths to pixels. */
private fun renderStrokes(target: Bitmap, strokes: List<Stroke2>) {
    val c = android.graphics.Canvas(target)
    for (s in strokes) {
        if (s.points.isEmpty()) continue
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = s.width * target.width
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        s.points.forEachIndexed { i, p ->
            val x = p.x * target.width
            val y = p.y * target.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (s.points.size == 1) path.lineTo(s.points[0].x * target.width + 0.5f, s.points[0].y * target.height)
        c.drawPath(path, paint)
    }
}

/** Drawing surface: strokes are captured relative to [area] (the visible image rectangle). */
@Composable
private fun StrokeLayer(strokes: MutableList<Stroke2>, area: () -> Rect, newStroke: () -> Stroke2, modifier: Modifier = Modifier) {
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o ->
                    val r = area()
                    if (r.width > 0) strokes.add(newStroke().also { it.points.add(Offset((o.x - r.left) / r.width, (o.y - r.top) / r.height)) })
                },
                onDrag = { change, _ ->
                    val r = area()
                    val o = change.position
                    strokes.lastOrNull()?.points?.add(Offset((o.x - r.left) / r.width, (o.y - r.top) / r.height))
                    change.consume()
                },
            )
        },
    ) {
        val r = area()
        for (s in strokes) {
            if (s.points.isEmpty()) continue
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val x = r.left + p.x * r.width
                val y = r.top + p.y * r.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, s.color, style = Stroke(width = s.width * r.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Back with unsaved strokes asks first; an accidental edge swipe must not silently discard work. */
@Composable
fun DiscardGuard(dirty: Boolean, onDiscard: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    BackHandler { if (dirty) asking = true else onDiscard() }
    if (asking) {
        ConfirmDialog(tr("Değişiklikler kaydedilmedi"), tr("Çizdikleriniz silinecek."), confirm = tr("Çık"), onDismiss = { asking = false }, onConfirm = onDiscard)
    }
    return { if (dirty) asking = true else onDiscard() }
}

@Composable
fun AnnotateScreen(vm: AppViewModel, pageId: String) {
    val bitmap by produceState<Bitmap?>(null, pageId) { value = withContext(Dispatchers.IO) { vm.c.repo.pageBitmap(pageId) } }
    val strokes = remember { mutableStateListOf<Stroke2>() }
    val palette = listOf(Color.Black, Color(0xFF1565C0), Color(0xFFC62828), Color(0x88FFEB3B))
    var color by remember { mutableStateOf(palette[2]) }
    var width by remember { mutableFloatStateOf(0.006f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val leave = DiscardGuard(strokes.isNotEmpty()) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Kalem")) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Icon(Icons.AutoMirrored.Filled.Undo, tr("Geri al")) }
                    TextButton(enabled = strokes.isNotEmpty() && bitmap != null, onClick = {
                        val src = bitmap ?: return@TextButton
                        val list = strokes.toList()
                        vm.work(tr("Kaydediliyor")) {
                            val out = Bitmaps.mutableCopy(src)
                            renderStrokes(out, list)
                            vm.c.repo.replacePageImageBakingEdits(pageId, out)
                            withContext(Dispatchers.Main) { vm.changed(); vm.back() }
                        }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { c ->
                    Box(Modifier.size(36.dp).background(c, CircleShape).border(if (c == color) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable {
                        color = c
                        if (c.alpha < 1f) width = max(width, 0.03f)
                    })
                }
                Slider(width, { width = it }, valueRange = 0.002f..0.04f, modifier = Modifier.weight(1f))
            }
        },
    ) { padding ->
        val b = bitmap
        if (b == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val img = remember(b) { Bitmaps.capEdge(b, 1800).asImageBitmap() }
            // Side margin + gesture exclusion keep strokes from triggering the system back gesture.
            Box(Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxSize().systemGestureExclusion().onSizeChanged { box = it }) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                StrokeLayer(strokes, { fitRect(box, b.width, b.height) }, { Stroke2(color, width) }, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun SignScreen(vm: AppViewModel, pageId: String) {
    var drawing by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    val signatures by produceState<List<Pair<String, ImageBitmap>>?>(null, version) {
        value = withContext(Dispatchers.IO) { vm.c.repo.signatures().map { it to vm.c.repo.signature(it).asImageBitmap() } }
    }

    when {
        placing != null -> PlaceSignature(vm, pageId, placing!!) { placing = null }
        drawing -> DrawSignature(vm, onCancel = { drawing = false }) { id -> drawing = false; version++; placing = id }
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tr("İmza ekle")) },
                    navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text(tr("İmzalarınız kasada şifreli saklanır."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { drawing = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Yeni imza çiz")) }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(signatures.orEmpty(), key = { it.first }) { (id, img) ->
                        Card(Modifier.fillMaxWidth().clickable { placing = id }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(img, tr("İmza"), Modifier.weight(1f).height(90.dp).background(Color.White).padding(8.dp), contentScale = ContentScale.Fit)
                                IconButton(onClick = {
                                    vm.work(tr("Siliniyor")) { vm.c.repo.deleteSignature(id); withContext(Dispatchers.Main) { version++ } }
                                }) { Icon(Icons.Default.Delete, tr("Sil")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawSignature(vm: AppViewModel, onCancel: () -> Unit, onSaved: (String) -> Unit) {
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var color by remember { mutableStateOf(Color(0xFF0D2A6B)) }
    val leave = DiscardGuard(strokes.isNotEmpty(), onCancel)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("İmzanızı çizin")) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    TextButton(onClick = { strokes.clear() }) { Text(tr("Temizle")) }
                    TextButton(enabled = strokes.isNotEmpty(), onClick = {
                        val list = strokes.toList()
                        vm.work(tr("Kaydediliyor")) {
                            // Render at 1200x600, then crop to the ink bounds with a small margin.
                            val full = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
                            renderStrokes(full, list)
                            val pts = list.flatMap { it.points }
                            val pad = 0.03f
                            val l = ((pts.minOf { it.x } - pad).coerceIn(0f, 1f) * full.width).toInt()
                            val t = ((pts.minOf { it.y } - pad * 2).coerceIn(0f, 1f) * full.height).toInt()
                            val r = ((pts.maxOf { it.x } + pad).coerceIn(0f, 1f) * full.width).toInt()
                            val b = ((pts.maxOf { it.y } + pad * 2).coerceIn(0f, 1f) * full.height).toInt()
                            val cropped = Bitmap.createBitmap(full, l, t, max(1, r - l), max(1, b - t))
                            val id = vm.c.repo.saveSignature(cropped)
                            withContext(Dispatchers.Main) { onSaved(id) }
                        }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Color(0xFF0D2A6B), Color.Black).forEach { c ->
                    FilterChip(color == c, { color = c }, label = { Text(if (c == Color.Black) tr("Siyah") else tr("Lacivert"), color = c) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(2f).background(Color.White).border(1.dp, Color.LightGray).systemGestureExclusion()) {
                var size by remember { mutableStateOf(IntSize.Zero) }
                StrokeLayer(
                    strokes,
                    { Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()) },
                    { Stroke2(color, 0.008f) },
                    Modifier.fillMaxSize().onSizeChanged { size = it },
                )
            }
        }
    }
}

@Composable
private fun PlaceSignature(vm: AppViewModel, pageId: String, signatureId: String, onDone: () -> Unit) {
    val page by produceState<Bitmap?>(null, pageId) { value = withContext(Dispatchers.IO) { vm.c.repo.pageBitmap(pageId) } }
    val sig by produceState<Bitmap?>(null, signatureId) { value = withContext(Dispatchers.IO) { vm.c.repo.signature(signatureId) } }
    var center by remember { mutableStateOf(Offset(0.7f, 0.85f)) }
    var widthFrac by remember { mutableFloatStateOf(0.3f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    BackHandler(onBack = onDone)

    fun normRect(p: Bitmap, s: Bitmap): RectF {
        val h = widthFrac * p.width * s.height / s.width / p.height
        return RectF(center.x - widthFrac / 2, center.y - h / 2, center.x + widthFrac / 2, center.y + h / 2)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("İmzayı yerleştirin")) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    TextButton(enabled = page != null && sig != null, onClick = {
                        val p = page!!
                        val s = sig!!
                        val rect = normRect(p, s)
                        vm.work(tr("Kaydediliyor")) {
                            vm.c.repo.replacePageImageBakingEdits(pageId, PageComposer.overlay(p, s, rect))
                            withContext(Dispatchers.Main) { vm.changed(); vm.back() }
                        }
                    }) { Text(tr("Uygula")) }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Text(tr("Boyut"))
                Slider(widthFrac, { widthFrac = it }, valueRange = 0.08f..0.8f)
                Text(tr("İmzayı sürükleyerek konumlandırın."), style = MaterialTheme.typography.bodySmall)
            }
        },
    ) { padding ->
        val p = page
        val s = sig
        if (p == null || s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val img = remember(p) { Bitmaps.capEdge(p, 1800).asImageBitmap() }
            val sigImg = remember(s) { s.asImageBitmap() }
            Box(Modifier.padding(padding).fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    val r = fitRect(box, p.width, p.height)
                    center = Offset((center.x + amount.x / r.width).coerceIn(0f, 1f), (center.y + amount.y / r.height).coerceIn(0f, 1f))
                    change.consume()
                }
            }) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    val r = fitRect(box, p.width, p.height)
                    val n = normRect(p, s)
                    val dst = Rect(r.left + n.left * r.width, r.top + n.top * r.height, r.left + n.right * r.width, r.top + n.bottom * r.height)
                    drawImage(
                        sigImg,
                        dstOffset = androidx.compose.ui.unit.IntOffset(dst.left.toInt(), dst.top.toInt()),
                        dstSize = IntSize(dst.width.toInt().coerceAtLeast(1), dst.height.toInt().coerceAtLeast(1)),
                    )
                    drawRect(Color(0xFF2196F3), dst.topLeft, dst.size, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
