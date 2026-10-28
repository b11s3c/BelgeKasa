@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.DocumentDetector
import com.belgetarayici.imaging.Filter
import com.belgetarayici.imaging.Filters
import com.belgetarayici.imaging.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun ReviewScreen(vm: AppViewModel) {
    var index by remember { mutableIntStateOf(0) }
    if (vm.captures.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    index = index.coerceIn(0, vm.captures.lastIndex)
    val cap = vm.captures[index]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trf("Sayfa %s / %s", index + 1, vm.captures.size)) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    IconButton(onClick = { vm.captures.removeAt(index) }) { Icon(Icons.Default.Delete, tr("Bu sayfayı at")) }
                    IconButton(onClick = { vm.back() }) { Icon(Icons.Default.AddAPhoto, tr("Daha fazla çek")) }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(vertical = 8.dp)) {
                // Every page as a thumbnail: tap one to crop/filter it on its own.
                if (vm.captures.size > 1) LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(vm.captures) { i, c ->
                        val thumb = remember(c.original) { Bitmaps.capEdge(c.original, 200).asImageBitmap() }
                        Box(
                            Modifier.size(56.dp, 72.dp).clip(RoundedCornerShape(6.dp))
                                .border(if (i == index) 3.dp else 1.dp, if (i == index) MaterialTheme.colorScheme.primary else Color.Gray, RoundedCornerShape(6.dp))
                                .clickable { index = i },
                        ) {
                            Image(thumb, trf("Sayfa %s", i + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text("${i + 1}", Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000)).padding(horizontal = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { index-- }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Önceki")) }
                    IconButton(onClick = { index++ }, enabled = index < vm.captures.lastIndex) { Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Sonraki")) }
                    if (vm.captures.size > 1) TextButton(onClick = { vm.captures.forEach { it.filter = cap.filter }; vm.toast(tr("Filtre tüm sayfalara uygulandı")) }) { Text(tr("Filtre → tümü")) }
                    Box(Modifier.weight(1f))
                    Button(onClick = { vm.saveScan() }, Modifier.padding(end = 8.dp)) {
                        Text(if (vm.scanMode == ScanMode.ID_CARD) tr("Kartı kaydet") else trf("Kaydet (%s)", vm.captures.size))
                    }
                }
            }
        },
    ) { padding ->
        CaptureEditor(cap, Modifier.padding(padding))
    }
}

/** Re-edit crop/filter of an already stored page, starting from its original photo. */
@Composable
fun RecropScreen(vm: AppViewModel, pageId: String) {
    val cap by produceState<Capture?>(null, pageId) {
        value = withContext(Dispatchers.IO) {
            val info = vm.c.repo.page(pageId) ?: return@withContext null
            val original = vm.c.repo.originalBitmap(pageId)
            if (original != null) Capture(original, Quad.decode(info.quad) ?: Quad.FULL, Filter.of(info.filter))
            else Capture(vm.c.repo.pageBitmap(pageId), Quad.FULL, Filter.ORIGINAL)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Kırp ve filtrele")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    val c = cap
                    TextButton(enabled = c != null, onClick = {
                        c ?: return@TextButton
                        vm.work(tr("Kaydediliyor")) {
                            val processed = vm.processCapture(c)
                            val info = vm.c.repo.page(pageId)
                            vm.c.repo.replacePageImage(pageId, processed, c.filter.id, c.quad.encode(), c.original)
                            withContext(Dispatchers.Main) {
                                vm.changed(); vm.back()
                                if (info != null && info.ocrText.isNotBlank()) vm.runOcr(info.docId, onlyMissing = false, pageIds = listOf(pageId))
                            }
                        }
                    }) { Text(tr("Kaydet")) }
                },
            )
        },
    ) { padding ->
        val c = cap
        if (c == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else CaptureEditor(c, Modifier.padding(padding))
    }
}

@Composable
fun CaptureEditor(cap: Capture, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(tr("Kırp")) })
            Tab(tab == 1, { tab = 1 }, text = { Text(tr("Filtre")) })
        }
        // Side padding + gesture exclusion keep corner handles clear of the system back gesture.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp).systemGestureExclusion()) {
            if (tab == 0) {
                CropArea(cap)
            } else {
                val preview by produceState<ImageBitmap?>(null, cap.original, cap.quad, cap.filter) {
                    value = null
                    value = withContext(Dispatchers.Default) {
                        val small = Bitmaps.capEdge(cap.original, 1400)
                        Filters.apply(DocumentDetector.warp(small, cap.quad), cap.filter).asImageBitmap()
                    }
                }
                val p = preview
                if (p == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (tab == 0) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { cap.quad = runCatching { DocumentDetector.detect(cap.original) }.getOrNull() ?: Quad.FULL }) {
                    Icon(Icons.Default.AutoFixHigh, null); Text(tr(" Otomatik"))
                }
                TextButton(onClick = { cap.quad = Quad.FULL }) { Icon(Icons.Default.CropFree, null); Text(tr(" Tümü")) }
                TextButton(onClick = {
                    cap.original = Bitmaps.rotate(cap.original, 90)
                    cap.quad = Quad.ordered(cap.quad.points.map { PointF(1 - it.y, it.x) })
                }) { Icon(Icons.AutoMirrored.Filled.RotateRight, null); Text(tr(" Döndür")) }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
                Filter.entries.forEach { f ->
                    FilterChip(cap.filter == f, { cap.filter = f }, label = { Text(f.label) }, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** Shows the photo with four draggable corner handles. */
@Composable
private fun CropArea(cap: Capture) {
    val image = remember(cap.original) { Bitmaps.capEdge(cap.original, 1600).asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableIntStateOf(-1) }

    fun imageRect(): Rect {
        if (box == IntSize.Zero) return Rect.Zero
        val s = min(box.width.toFloat() / image.width, box.height.toFloat() / image.height)
        val w = image.width * s
        val h = image.height * s
        val l = (box.width - w) / 2
        val t = (box.height - h) / 2
        return Rect(l, t, l + w, t + h)
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(cap) {
            detectDragGestures(
                onDragStart = { start ->
                    val r = imageRect()
                    val pts = cap.quad.points.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
                    val nearest = pts.indices.minBy { hypot(pts[it].x - start.x, pts[it].y - start.y) }
                    dragging = if (hypot(pts[nearest].x - start.x, pts[nearest].y - start.y) < 60.dp.toPx()) nearest else -1
                },
                onDragEnd = { dragging = -1 },
                onDragCancel = { dragging = -1 },
                onDrag = { change, amount ->
                    val i = dragging
                    if (i < 0) return@detectDragGestures
                    change.consume()
                    val r = imageRect()
                    val p = cap.quad.points[i]
                    val np = PointF((p.x + amount.x / r.width).coerceIn(0f, 1f), (p.y + amount.y / r.height).coerceIn(0f, 1f))
                    cap.quad = Quad(cap.quad.points.toMutableList().also { it[i] = np })
                },
            )
        },
    ) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val r = imageRect()
            val pts = cap.quad.points.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
            }
            drawPath(path, Color(0x332196F3))
            drawPath(path, Color(0xFF2196F3), style = Stroke(3.dp.toPx()))
            pts.forEachIndexed { i, o ->
                drawCircle(Color.White, radius = 14.dp.toPx(), center = o)
                drawCircle(if (i == dragging) Color(0xFFFF9800) else Color(0xFF2196F3), radius = 10.dp.toPx(), center = o)
            }
        }
    }
}
