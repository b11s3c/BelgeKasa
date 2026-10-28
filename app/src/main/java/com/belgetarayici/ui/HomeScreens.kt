@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.L
import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.text.format.Formatter
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.belgetarayici.data.DocumentInfo
import com.belgetarayici.data.PdfInfo
import com.belgetarayici.io.ExportFormat
import com.belgetarayici.io.ExportOptions
import com.belgetarayici.io.Importer
import com.belgetarayici.pdf.PdfExport
import com.belgetarayici.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** BelgeKasa home: one app, three tool areas. */
@Composable
fun HomeScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Each tab's Scaffold must not pad for the system bar again; the NavigationBar does.
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
            when (vm.homeTab) {
                HomeTab.SCANS -> LibraryScreen(vm)
                HomeTab.PDF -> PdfLibraryScreen(vm)
                HomeTab.TOOLS -> ToolsScreen(vm)
            }
        }
        NavigationBar {
            HomeTab.entries.forEach { tab ->
                val icon = when (tab) {
                    HomeTab.SCANS -> Icons.Default.DocumentScanner
                    HomeTab.PDF -> Icons.Default.PictureAsPdf
                    HomeTab.TOOLS -> Icons.Default.Build
                }
                NavigationBarItem(vm.homeTab == tab, { vm.homeTab = tab }, icon = { Icon(icon, null) }, label = { Text(tab.label) })
            }
        }
    }
}

private fun dateText(ms: Long) = SimpleDateFormat("d MMM yyyy, HH:mm", L.locale).format(Date(ms))

@Composable
fun PdfLibraryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<PdfInfo?>(null) }
    var renaming by remember { mutableStateOf<PdfInfo?>(null) }
    var deleting by remember { mutableStateOf<PdfInfo?>(null) }
    val list by produceState<List<PdfInfo>?>(null, query, vm.dataVersion) {
        if (query.isNotEmpty()) delay(200)
        value = withContext(Dispatchers.IO) { runCatching { vm.c.pdfs.list(query.ifBlank { null }) }.getOrDefault(emptyList()) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importPdfs(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(tr("Ad veya PDF içinde ara")) },
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    else Text(tr("PDF'ler"), fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, tr("Ara"))
                    }
                    IconButton(onClick = { vm.c.lock.lockNow() }) { Icon(Icons.Default.Lock, tr("Kilitle")) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.c.lock.expectExternalActivity(); picker.launch(arrayOf("application/pdf")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("PDF ekle")) },
            )
        },
    ) { padding ->
        val items = list
        when {
            items == null -> Unit
            items.isEmpty() && query.isBlank() -> Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(tr("PDF kitaplığınız boş"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Eklediğiniz PDF'ler aslına uygun (metin, formlar, yorumlar korunarak) şifreli kasada saklanır. ") +
                        tr("Okuyabilir, işaretleyebilir, form doldurup imzalayabilir, sayfalarını düzenleyebilirsiniz."),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.isEmpty() -> Text(trf("“%s” için sonuç yok", query), Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().combinedClickable(onClick = { vm.openPdf(p.id) }, onLongClick = { menuFor = p })) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val pages = if (p.pages > 0) trf("%s sayfa · ", p.pages) else ""
                                Text(pages + Formatter.formatShortFileSize(context, p.size) + " · " + dateText(p.updated),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (p.protected) Icon(Icons.Default.Lock, tr("Parolalı"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { p ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { menuFor = null; renaming = p }) { Text(tr("Yeniden adlandır")) }
                    TextButton(onClick = { menuFor = null; deleting = p }) { Text(tr("Sil"), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(tr("Kapat")) } },
        )
    }
    renaming?.let { p ->
        TextInputDialog(tr("Yeniden adlandır"), p.name, tr("PDF adı"), onDismiss = { renaming = null }) { name ->
            renaming = null
            vm.work(tr("Kaydediliyor")) { vm.c.pdfs.rename(p.id, name); withContext(Dispatchers.Main) { vm.changed() } }
        }
    }
    deleting?.let { p ->
        ConfirmDialog(tr("PDF'yi sil"), trf("“%s” kasadan kalıcı olarak silinecek.", p.name), onDismiss = { deleting = null }) {
            vm.work(tr("Siliniyor")) { vm.c.pdfs.delete(p.id); withContext(Dispatchers.Main) { vm.changed() } }
        }
    }
}

private enum class Tool(private val titleTr: String, private val subtitleTr: String, val icon: ImageVector) {
    COMBINE("PDF birleştir", "Birden çok PDF'yi tek dosyada topla", Icons.AutoMirrored.Filled.MergeType),
    COMPRESS("PDF sıkıştır", "Görüntüleri küçülterek dosyayı hafiflet", Icons.Default.Compress),
    PROTECT("Parola ekle", "PDF'yi AES-256 ile parolala", Icons.Default.Lock),
    UNPROTECT("Parolayı kaldır", "Parolalı PDF'nin korumasını kaldır", Icons.Default.LockOpen),
    SCAN_TO_PDF("Taramayı PDF'ye çevir", "Taramalardan aranabilir PDF oluştur", Icons.Default.DocumentScanner),
    IMAGES_TO_PDF("Görüntülerden PDF", "Fotoğrafları tek PDF'de birleştir", Icons.Default.Image),
    WORD_TO_PDF("Word'den PDF", "DOCX dosyasını PDF'ye dönüştür", Icons.Default.Description),
    PDF_TO_WORD("PDF'yi Word'e çevir", "Metin ve sayfa görüntüleriyle DOCX", Icons.Default.Description),
    OCR("Metni tanı (OCR)", "Taranmış PDF'yi aranabilir yap", Icons.AutoMirrored.Filled.TextSnippet),
    KEYS("Paylaşım anahtarım", "Parolasız şifreli paylaşım için QR", Icons.Default.Key),
    SETTINGS("Ayarlar", "Kilit, OCR dili, tarama", Icons.Default.Settings);

    val title: String get() = tr(titleTr)
    val subtitle: String get() = tr(subtitleTr)
}

@Composable
fun ToolsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf<Tool?>(null) }
    var pickingScan by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<Pair<Tool, PdfInfo>?>(null) }
    var pendingWord by remember { mutableStateOf<PdfInfo?>(null) }

    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.work(tr("PDF oluşturuluyor")) {
            val pages = uris.map { com.belgetarayici.data.NewPage(vm.c.importer.image(it)) }
            vm.createPdf(vm.defaultName(tr("Görüntüler")), pagesToPdf(vm, pages))
        }
    }
    val word = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.work(tr("PDF oluşturuluyor")) {
            when (val r = vm.c.importer.import(uri)) {
                is Importer.Result.Ok -> vm.createPdf(r.name, pagesToPdf(vm, r.pages))
                is Importer.Result.Failed -> vm.toast(r.message)
                Importer.Result.NeedsPassword -> vm.toast(tr("Parolalı dosya desteklenmiyor"))
            }
        }
    }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mime)) { uri ->
        val p = pendingWord
        pendingWord = null
        if (uri != null && p != null) vm.work(tr("Word'e dönüştürülüyor")) {
            val bytes = vm.c.pdfs.bytes(p.id)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { PdfExport.toDocx(context, bytes, null, it) }
            vm.toast(tr("Kaydedildi"))
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(tr("Araçlar"), fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.padding(padding), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Tool.entries) { tool ->
                Card(Modifier.fillMaxWidth().heightIn(min = 120.dp).combinedClickable(onClick = {
                    when (tool) {
                        Tool.KEYS -> vm.navigate(Screen.Keys)
                        Tool.SETTINGS -> vm.navigate(Screen.Settings)
                        Tool.IMAGES_TO_PDF -> { vm.c.lock.expectExternalActivity(); images.launch(arrayOf("image/*")) }
                        Tool.WORD_TO_PDF -> { vm.c.lock.expectExternalActivity(); word.launch(arrayOf(ExportFormat.DOCX.mime)) }
                        Tool.SCAN_TO_PDF -> pickingScan = true
                        else -> picking = tool
                    }
                })) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(tool.title, style = MaterialTheme.typography.titleSmall)
                        Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    picking?.let { tool ->
        PdfPickerDialog(vm, multiple = tool == Tool.COMBINE, title = tool.title, onDismiss = { picking = null }) { chosen ->
            picking = null
            when (tool) {
                Tool.COMBINE -> vm.work(tr("Birleştiriliyor")) {
                    vm.createPdf(vm.defaultName(tr("Birleştirilmiş")), PdfTools.combine(chosen.map { vm.c.pdfs.bytes(it.id) to null }))
                }
                Tool.COMPRESS -> vm.work(tr("Sıkıştırılıyor")) {
                    val p = chosen.first()
                    val before = vm.c.pdfs.bytes(p.id)
                    val after = PdfTools.compress(before, null, maxEdge = 1600, quality = 0.6f)
                    if (after.size < before.size) {
                        vm.c.pdfs.save(p.id, p.name, after, null)
                        vm.toast("${Formatter.formatShortFileSize(context, before.size.toLong())} → ${Formatter.formatShortFileSize(context, after.size.toLong())}")
                    } else vm.toast(tr("Dosya zaten küçük, değişiklik yapılmadı"))
                    withContext(Dispatchers.Main) { vm.changed() }
                }
                Tool.PROTECT, Tool.UNPROTECT -> passwordFor = tool to chosen.first()
                Tool.PDF_TO_WORD -> { pendingWord = chosen.first(); vm.c.lock.expectExternalActivity(); saveDocx.launch(chosen.first().name + ".docx") }
                Tool.OCR -> vm.ocrPdf(chosen.first().id)
                else -> Unit
            }
        }
    }
    if (pickingScan) {
        ScanPickerDialog(vm, onDismiss = { pickingScan = false }) { doc ->
            pickingScan = false
            vm.work(tr("PDF oluşturuluyor")) {
                val out = ByteArrayOutputStream()
                vm.c.exporter.export(vm.c.repo.pages(doc.id), ExportOptions(ExportFormat.PDF), out)
                vm.createPdf(doc.name, out.toByteArray())
            }
        }
    }
    passwordFor?.let { (tool, p) ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        val protect = tool == Tool.PROTECT
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(tool.title) },
            text = {
                Column {
                    Text(p.name, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(pw, { pw = it }, label = { Text(if (protect) tr("Yeni parola (en az 6)") else tr("Mevcut parola")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (protect) OutlinedTextField(pw2, { pw2 = it }, label = { Text(tr("Parola (tekrar)")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = if (protect) pw.length >= 6 && pw == pw2 else pw.isNotEmpty(), onClick = {
                    passwordFor = null
                    vm.work(if (protect) tr("Parolalanıyor") else tr("Parola kaldırılıyor")) {
                        val bytes = vm.c.pdfs.bytes(p.id)
                        val out = if (protect) PdfTools.protect(bytes, null, pw) else PdfTools.unprotect(bytes, pw)
                        vm.c.pdfs.save(p.id, p.name, out, if (protect) pw else null)
                        withContext(Dispatchers.Main) { vm.changed() }
                        vm.toast(if (protect) tr("PDF parolalandı") else tr("Parola kaldırıldı"))
                    }
                }) { Text(tr("Uygula")) }
            },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text(tr("Vazgeç")) } },
        )
    }
}

/** Imported pages (images, rendered DOCX) → PDF bytes; known text becomes a searchable layer. */
private fun pagesToPdf(vm: AppViewModel, pages: List<com.belgetarayici.data.NewPage>): ByteArray {
    val out = ByteArrayOutputStream()
    PdfExport.fromBitmaps(vm.getApplication(), pages.map { it.image to it.text }, out)
    return out.toByteArray()
}

@Composable
fun PdfPickerDialog(vm: AppViewModel, multiple: Boolean, title: String, onDismiss: () -> Unit, onPick: (List<PdfInfo>) -> Unit) {
    val list by produceState<List<PdfInfo>>(emptyList(), vm.dataVersion) { value = withContext(Dispatchers.IO) { vm.c.pdfs.list() } }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (list.isEmpty()) Text(tr("Önce PDF sekmesinden PDF ekleyin."))
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                list.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (p.pages > 0) "${p.pages} sayfa" else tr("Parolalı")) },
                        leadingContent = { if (multiple) Checkbox(p.id in selected, { if (it) selected.add(p.id) else selected.remove(p.id) }) },
                        modifier = Modifier.combinedClickable(onClick = {
                            if (multiple) { if (p.id in selected) selected.remove(p.id) else selected.add(p.id) } else onPick(listOf(p))
                        }),
                    )
                }
            }
        },
        confirmButton = {
            if (multiple) TextButton(enabled = selected.size >= 2, onClick = { onPick(selected.mapNotNull { id -> list.firstOrNull { it.id == id } }) }) {
                Text(trf("Birleştir (%s)", selected.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
private fun ScanPickerDialog(vm: AppViewModel, onDismiss: () -> Unit, onPick: (DocumentInfo) -> Unit) {
    val list by produceState<List<DocumentInfo>>(emptyList(), vm.dataVersion) { value = withContext(Dispatchers.IO) { vm.c.repo.documents() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Tarama seçin")) },
        text = {
            if (list.isEmpty()) Text(tr("Henüz tarama yok."))
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                list.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${d.pageCount} sayfa") },
                        modifier = Modifier.combinedClickable(onClick = { onPick(d) }),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}
