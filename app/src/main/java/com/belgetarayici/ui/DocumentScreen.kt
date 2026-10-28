@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.belgetarayici.data.DocumentInfo
import com.belgetarayici.data.PageInfo
import com.belgetarayici.io.ExportFormat
import com.belgetarayici.io.ExportOptions
import com.belgetarayici.io.PageSize
import com.belgetarayici.io.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentScreen(vm: AppViewModel, docId: String) {
    val context = LocalContext.current
    val data by produceState<Pair<DocumentInfo?, List<PageInfo>>?>(null, docId, vm.dataVersion) {
        value = withContext(Dispatchers.IO) { vm.c.repo.document(docId) to vm.c.repo.pages(docId) }
    }
    val doc = data?.first
    val pages = data?.second.orEmpty()
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    val order = remember { mutableStateListOf<String>() }
    val selected = remember { mutableStateListOf<String>() }
    var pendingExport by remember { mutableStateOf<ExportOptions?>(null) }

    androidx.compose.runtime.LaunchedEffect(data) { if (data != null && doc == null) vm.back() }

    val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris, docId)
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val opts = pendingExport
        pendingExport = null
        if (uri != null && opts != null) vm.work(tr("Dışa aktarılıyor")) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { vm.c.exporter.export(vm.c.repo.pages(docId), opts, it) }
            vm.toast(tr("Kaydedildi"))
        }
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(trf("%s sayfa seçili", selected.size)) },
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, tr("Kapat")) } },
                    actions = {
                        IconButton(onClick = {
                            val ids = pages.map { it.id }.filter { it in selected }
                            selected.clear()
                            vm.work(tr("Ayıklanıyor")) {
                                val id = vm.c.repo.copyPagesToNewDocument(trf("%s (ayıklanan)", doc?.name), ids)
                                withContext(Dispatchers.Main) { vm.changed(); vm.navigate(Screen.Document(id)) }
                            }
                        }) { Icon(Icons.Default.ContentCut, tr("Yeni belgeye ayıkla")) }
                        IconButton(onClick = {
                            val ids = selected.toList()
                            selected.clear()
                            vm.work(tr("Siliniyor")) { ids.forEach(vm.c.repo::deletePage); withContext(Dispatchers.Main) { vm.changed() } }
                        }) { Icon(Icons.Default.Delete, tr("Sil")) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(doc?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                    actions = {
                        if (reorder) {
                            IconButton(onClick = {
                                val ids = order.toList()
                                reorder = false
                                vm.work(tr("Kaydediliyor")) { vm.c.repo.reorder(docId, ids); withContext(Dispatchers.Main) { vm.changed() } }
                            }) { Icon(Icons.Default.Done, tr("Sıralamayı kaydet")) }
                        } else {
                            IconButton(onClick = { vm.navigate(Screen.Share(ShareTarget.Scan(docId))) }) { Icon(Icons.Default.Share, tr("Paylaş")) }
                            IconButton(onClick = { showExport = true }) { Icon(Icons.Default.SaveAlt, tr("Farklı kaydet")) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Diğer")) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text(tr("Yeniden adlandır")) }, { menu = false; rename = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem({ Text(tr("Dosyadan sayfa ekle")) }, {
                                        menu = false; vm.c.lock.expectExternalActivity(); addFiles.launch(IMPORT_MIME_TYPES)
                                    }, leadingIcon = { Icon(Icons.Default.FileOpen, null) })
                                    DropdownMenuItem({ Text(tr("Metni tanı (OCR)")) }, { menu = false; vm.runOcr(docId, onlyMissing = false) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.TextSnippet, null) })
                                    DropdownMenuItem({ Text(tr("Tüm metni göster")) }, { menu = false; showText = true }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                    DropdownMenuItem({ Text(tr("Sayfaları sırala")) }, {
                                        menu = false; order.clear(); order.addAll(pages.map { it.id }); reorder = true
                                    }, leadingIcon = { Icon(Icons.Default.SwapVert, null) })
                                    DropdownMenuItem({ Text(tr("Belgeyi sil"), color = MaterialTheme.colorScheme.error) }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!reorder && selected.isEmpty()) BottomAppBar {
                TextButton(onClick = { vm.startScan(docId) }) { Icon(Icons.Default.AddAPhoto, null); Text(tr("  Sayfa ekle")) }
                Spacer(Modifier.weight(1f))
                Text("${pages.size} sayfa", Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            vm.ocrProgress?.let { (i, n) ->
                LinearProgressIndicator(progress = { (i + 1f) / n }, modifier = Modifier.fillMaxWidth())
                Text(trf("Metin tanınıyor: %s/%s", i + 1, n), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
            val shown = if (reorder) order.mapNotNull { id -> pages.firstOrNull { it.id == id } } else pages
            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                    Column {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(8.dp)).combinedClickable(
                                onClick = {
                                    when {
                                        reorder -> Unit
                                        selected.isNotEmpty() -> if (p.id in selected) selected.remove(p.id) else selected.add(p.id)
                                        else -> vm.navigate(Screen.Page(p.id))
                                    }
                                },
                                onLongClick = { if (!reorder && p.id !in selected) selected.add(p.id) },
                            ),
                        ) {
                            VaultThumb(vm, p.id, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Fit)
                            if (p.id in selected) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            if (p.ocrText.isNotBlank()) Text(tr("metin ✓"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        // Own row so both buttons keep full 48dp touch targets on narrow cells.
                        if (reorder) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilledTonalIconButton(onClick = { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Öne al")) }
                            FilledTonalIconButton(onClick = { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.lastIndex) { Icon(Icons.AutoMirrored.Filled.ArrowForward, tr("Geriye al")) }
                        }
                    }
                }
            }
        }
    }

    if (rename && doc != null) {
        TextInputDialog(tr("Yeniden adlandır"), doc.name, tr("Belge adı"), onDismiss = { rename = false }) { name ->
            rename = false
            vm.work(tr("Kaydediliyor")) { vm.c.repo.rename(docId, name); withContext(Dispatchers.Main) { vm.changed() } }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(tr("Belgeyi sil"), trf("“%s” ve tüm sayfaları kalıcı olarak silinecek.", doc?.name), onDismiss = { confirmDelete = false }) {
            vm.work(tr("Siliniyor")) { vm.c.repo.deleteDocument(docId); withContext(Dispatchers.Main) { vm.changed(); vm.back() } }
        }
    }
    if (showText) {
        val all = pages.mapIndexed { i, p -> trf("— Sayfa %s —\n%s", i + 1, p.ocrText.ifBlank { tr("(metin yok)") }) }.joinToString("\n\n")
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text(tr("Belge metni")) },
            text = { SelectionContainer { Text(all, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { copy(context, all); vm.toast(tr("Panoya kopyalandı")); showText = false }) { Text(tr("Kopyala")) } },
            dismissButton = { TextButton(onClick = { showText = false }) { Text(tr("Kapat")) } },
        )
    }
    if (showExport && doc != null) {
        ExportDialog(
            title = tr("Farklı kaydet"),
            note = tr("Dosya, seçeceğiniz konuma şifresiz (veya parolalı PDF olarak) kaydedilir."),
            onDismiss = { showExport = false },
        ) { opts ->
            showExport = false
            pendingExport = opts
            vm.c.lock.expectExternalActivity()
            saveAs.launch("${doc.name}.${vm.c.exporter.extensionFor(opts, pages.size)}")
        }
    }
}

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText(tr("BelgeKasa"), text).apply {
        description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    })
}

@Composable
fun ExportDialog(title: String, note: String, allowWatermark: Boolean = true, onDismiss: () -> Unit, onConfirm: (ExportOptions) -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var size by remember { mutableStateOf(PageSize.A4) }
    var quality by remember { mutableStateOf(Quality.HIGH) }
    var textLayer by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var watermark by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(note, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(tr("Biçim"), fontWeight = FontWeight.Bold)
                ExportFormat.entries.forEach { f -> RadioRow(f.label, format == f) { format = f } }
                if (format == ExportFormat.PDF) {
                    Text(tr("Sayfa boyutu"), fontWeight = FontWeight.Bold)
                    PageSize.entries.forEach { s -> RadioRow(s.label, size == s) { size = s } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(textLayer, { textLayer = it }); Text(tr("Aranabilir metin katmanı (OCR)"))
                    }
                    OutlinedTextField(password, { password = it }, label = { Text(tr("PDF parolası (isteğe bağlı)")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
                if (format != ExportFormat.TXT) {
                    Text(tr("Kalite / boyut"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Quality.entries.forEach { q -> RadioRow(q.label, quality == q) { quality = q } }
                    if (allowWatermark) OutlinedTextField(watermark, { watermark = it }, label = { Text(tr("Filigran metni (isteğe bağlı)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ExportOptions(format, size, quality, textLayer, password.ifEmpty { null }.takeIf { format == ExportFormat.PDF }, watermark.ifBlank { null }))
            }) { Text(tr("Devam")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}
