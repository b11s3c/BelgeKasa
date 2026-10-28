@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.belgetarayici.pdf.PdfExport
import com.belgetarayici.pdf.PdfSession
import com.belgetarayici.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pages render one at a time: the platform renderer is not thread-safe. */
@OptIn(ExperimentalCoroutinesApi::class)
private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)

private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

@Composable
fun rememberPdfPage(s: PdfSession, index: Int, widthPx: Int): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, s.version, index, widthPx) {
        value = withContext(renderDispatcher) { runCatching { s.view.render(index, widthPx).asImageBitmap() }.getOrNull() }
    }
    return img
}

@Composable
fun PdfViewerScreen(vm: AppViewModel) {
    val s = vm.pdf
    if (s == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var night by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PdfTools.Hit>>(emptyList()) }
    var hitIndex by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val pageCount = s.view.pageCount
    val currentPage by remember { derivedStateOf { list.firstVisibleItemIndex } }
    val editable = !s.readOnly

    var exportFormat by remember { mutableStateOf("pdf") }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) vm.work(tr("Kaydediliyor")) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                when (exportFormat) {
                    "docx" -> PdfExport.toDocx(context, s.bytes, s.password, out)
                    "txt" -> out.write(s.pageTexts.mapIndexed { i, t -> trf("— Sayfa %s —\n%s", i + 1, t) }.joinToString("\n\n").toByteArray())
                    else -> out.write(PdfTools.unprotect(s.bytes, s.password))
                }
            }
            vm.toast(tr("Kaydedildi"))
        }
    }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            hits = withContext(Dispatchers.Default) { runCatching { PdfTools.search(s.bytes, s.password, query) }.getOrDefault(emptyList()) }
            hitIndex = 0
            searching = false
            if (hits.isEmpty()) vm.toast(trf("“%s” bulunamadı", query)) else list.animateScrollToItem(hits[0].page)
        }
    }

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { searchOpen = false; hits = emptyList(); query = "" }) { Icon(Icons.Default.Close, tr("Aramayı kapat")) } },
                    title = {
                        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(tr("Belgede ara")) }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }))
                    },
                    actions = {
                        val total = hits.sumOf { it.rects.size }
                        if (searching) CircularProgressIndicator(Modifier.padding(8.dp)) else if (hits.isNotEmpty()) Text(trf("%s/%s sf · %s", hitIndex + 1, hits.size, total))
                        IconButton(onClick = { if (hits.isNotEmpty()) { hitIndex = (hitIndex - 1 + hits.size) % hits.size; scope.launch { list.animateScrollToItem(hits[hitIndex].page) } } }) { Icon(Icons.Default.KeyboardArrowUp, tr("Önceki")) }
                        IconButton(onClick = { if (hits.isNotEmpty()) { hitIndex = (hitIndex + 1) % hits.size; scope.launch { list.animateScrollToItem(hits[hitIndex].page) } } else runSearch() }) { Icon(Icons.Default.KeyboardArrowDown, tr("Sonraki")) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, tr("Ara")) }
                        IconButton(onClick = { showOutline = true }) { Icon(Icons.AutoMirrored.Filled.List, tr("İçindekiler")) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Diğer")) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(if (night) tr("Gündüz modu") else tr("Gece modu")) }, { menu = false; night = !night }, leadingIcon = { Icon(Icons.Default.DarkMode, null) })
                                if (editable) {
                                    DropdownMenuItem({ Text(tr("Metni tanı (OCR)")) }, {
                                        menu = false
                                        val id = s.id
                                        if (id == null) vm.toast(tr("Önce kasaya kaydedin")) else vm.ocrPdf(id)
                                    }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.TextSnippet, null) })
                                    DropdownMenuItem({ Text(tr("Farklı kaydet")) }, { menu = false; exporting = true }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) })
                                    if (s.id != null) {
                                        DropdownMenuItem({ Text(tr("Yeniden adlandır")) }, { menu = false; renaming = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                        DropdownMenuItem({ Text(tr("Sil"), color = MaterialTheme.colorScheme.error) }, { menu = false; deleting = true }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            BottomAppBar {
                if (editable) {
                    PdfAction(Icons.Default.EditNote, tr("Yorum")) { vm.navigate(Screen.PdfAnnotate(currentPage)) }
                    PdfAction(Icons.Default.Edit, tr("Doldur")) { vm.navigate(Screen.PdfForm) }
                    PdfAction(Icons.Default.GridView, tr("Sayfalar")) { vm.navigate(Screen.PdfPages) }
                }
                PdfAction(Icons.AutoMirrored.Filled.MenuBook, tr("Okuma")) { vm.navigate(Screen.PdfReflow(currentPage)) }
                if (editable) PdfAction(Icons.Default.Share, tr("Paylaş")) { vm.navigate(Screen.Share(ShareTarget.Pdf)) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (s.readOnly) Banner(Icons.Default.Visibility, tr("Salt görüntüleme: bu belge kaydedilemez, düzenlenemez, dışa aktarılamaz."))
            else if (s.id == null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Bu PDF yalnızca açık; telefona da kasaya da yazılmadı."), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.savePdfToVault() }) { Icon(Icons.Default.Shield, null); Text(tr(" Kasaya kaydet")) }
            }
            vm.ocrProgress?.let { (i, n) -> LinearProgressIndicator(progress = { (i + 1f) / n }, modifier = Modifier.fillMaxWidth()) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF525659))) {
                val widthPx = with(LocalDensity.current) { (maxWidth - 16.dp).roundToPx() }
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                        // Two-finger pinch zooms; one finger scrolls the list and, when zoomed in,
                        // also pans sideways (the list itself only scrolls vertically).
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val limit = size.width * (scale - 1) / 2
                                    if (event.changes.count { it.pressed } >= 2) {
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                        panX = if (scale == 1f) 0f else (panX + event.calculatePan().x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                                        event.changes.forEach { it.consume() }
                                    } else if (scale > 1f) {
                                        val dx = event.changes.first().positionChangeIgnoreConsumed().x
                                        panX = (panX + dx).coerceIn(-limit, limit)
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX),
                ) {
                    items(pageCount) { i ->
                        val size = s.view.sizes[i]
                        val img = rememberPdfPage(s, i, (widthPx * 1.5f).toInt())
                        Box(Modifier.fillMaxWidth().aspectRatio(size.width.toFloat() / size.height).background(Color.White)) {
                            if (img != null) Image(img, trf("Sayfa %s", i + 1), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = if (night) INVERT else null)
                            else CircularProgressIndicator(Modifier.align(Alignment.Center))
                            val pageHits = hits.firstOrNull { it.page == i }
                            if (pageHits != null) Canvas(Modifier.fillMaxSize()) {
                                val current = hits.getOrNull(hitIndex)?.page == i
                                pageHits.rects.forEach { r -> drawHit(r, if (current) Color(0x88FF9800) else Color(0x66FFEB3B)) }
                            }
                        }
                    }
                }
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).clickable { goTo = true },
                    shape = RoundedCornerShape(16.dp), color = Color(0xCC000000),
                ) { Text("${currentPage + 1} / ${pageCount}", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
            }
        }
    }

    if (showOutline) {
        val outline by produceState<List<PdfTools.OutlineEntry>?>(null) {
            value = withContext(Dispatchers.Default) { runCatching { PdfTools.outline(s.bytes, s.password) }.getOrDefault(emptyList()) }
        }
        AlertDialog(
            onDismissRequest = { showOutline = false },
            title = { Text(tr("İçindekiler")) },
            text = {
                val o = outline
                when {
                    o == null -> CircularProgressIndicator()
                    o.isEmpty() -> Text(tr("Bu PDF'de içindekiler yok. Sayfaya gitmek için alttaki sayfa numarasına dokunun."))
                    else -> Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        o.forEach { e ->
                            Text(
                                e.title + if (e.page >= 0) "  ·  ${e.page + 1}" else "",
                                Modifier.fillMaxWidth().clickable(enabled = e.page >= 0) {
                                    showOutline = false
                                    scope.launch { list.scrollToItem(e.page.coerceIn(0, pageCount - 1)) }
                                }.padding(start = (e.level * 16).dp, top = 8.dp, bottom = 8.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOutline = false }) { Text(tr("Kapat")) } },
        )
    }
    if (goTo) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text(tr("Sayfaya git")) },
            text = { OutlinedTextField(value, { value = it.filter(Char::isDigit).take(5) }, label = { Text("1 – ${pageCount}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = {
                TextButton(onClick = {
                    goTo = false
                    value.toIntOrNull()?.let { n -> scope.launch { list.scrollToItem((n - 1).coerceIn(0, pageCount - 1)) } }
                }) { Text(tr("Git")) }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text(tr("Vazgeç")) } },
        )
    }
    if (renaming) {
        TextInputDialog(tr("Yeniden adlandır"), s.name, tr("PDF adı"), onDismiss = { renaming = false }) { name ->
            renaming = false
            s.name = name
            vm.work(tr("Kaydediliyor")) { s.id?.let { vm.c.pdfs.rename(it, name) }; withContext(Dispatchers.Main) { vm.changed() } }
        }
    }
    if (deleting) {
        ConfirmDialog(tr("PDF'yi sil"), trf("“%s” kasadan kalıcı olarak silinecek.", s.name), onDismiss = { deleting = false }) {
            vm.work(tr("Siliniyor")) {
                s.id?.let(vm.c.pdfs::delete)
                withContext(Dispatchers.Main) { vm.changed(); vm.back() }
            }
        }
    }
    if (exporting) {
        AlertDialog(
            onDismissRequest = { exporting = false },
            title = { Text(tr("Farklı kaydet")) },
            text = {
                Column {
                    Text(tr("Seçeceğiniz konuma kasanın dışına kaydedilir."), style = MaterialTheme.typography.bodySmall)
                    RadioRow(tr("PDF (parolasız kopya)"), exportFormat == "pdf") { exportFormat = "pdf" }
                    RadioRow(tr("Word (DOCX)"), exportFormat == "docx") { exportFormat = "docx" }
                    RadioRow(tr("Metin (TXT)"), exportFormat == "txt") { exportFormat = "txt" }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exporting = false
                    vm.c.lock.expectExternalActivity()
                    saveAs.launch("${s.name}.${exportFormat}")
                }) { Text(tr("Devam")) }
            },
            dismissButton = { TextButton(onClick = { exporting = false }) { Text(tr("Vazgeç")) } },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHit(r: RectF, color: Color) {
    drawRect(color, androidx.compose.ui.geometry.Offset(r.left * size.width, r.top * size.height),
        androidx.compose.ui.geometry.Size(r.width() * size.width, r.height() * size.height))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PdfAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Text("  ${text}", style = MaterialTheme.typography.bodySmall)
    }
}
