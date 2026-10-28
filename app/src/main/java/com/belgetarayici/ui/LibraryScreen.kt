@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.L
import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.belgetarayici.data.DocumentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val IMPORT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/*",
    "application/octet-stream",
)

@Composable
fun LibraryScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var mergeName by remember { mutableStateOf<String?>(null) }

    val docs by produceState<List<DocumentInfo>?>(null, query, vm.dataVersion) {
        if (query.isNotEmpty()) delay(200)
        value = withContext(Dispatchers.IO) { runCatching { vm.c.repo.documents(query.ifBlank { null }) }.getOrDefault(emptyList()) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.openFiles(uris)
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(trf("%s seçili", selected.size)) },
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, tr("Kapat")) } },
                    actions = {
                        if (selected.size >= 2) IconButton(onClick = { mergeName = vm.defaultName(tr("Birleştirilmiş")) }) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, tr("Birleştir"))
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, tr("Sil")) }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        if (searching) {
                            OutlinedTextField(
                                query, { query = it }, singleLine = true, placeholder = { Text(tr("Ad veya belge içinde ara")) },
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            )
                        } else {
                            Text(tr("Taramalar"), fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, tr("Ara"))
                        }
                        IconButton(onClick = { vm.c.lock.lockNow() }) { Icon(Icons.Default.Lock, tr("Kilitle")) }
                    },
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    vm.c.lock.expectExternalActivity()
                    importLauncher.launch(IMPORT_MIME_TYPES)
                }) { Icon(Icons.Default.FileOpen, tr("İçe aktar")) }
                ExtendedFloatingActionButton(
                    onClick = { vm.startScan() },
                    icon = { Icon(Icons.Default.CameraAlt, null) },
                    text = { Text(tr("Tara")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            vm.ocrProgress?.let { (i, n) ->
                Text(trf("Metin tanınıyor: %s/%s", i + 1, n), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
                LinearProgressIndicator(progress = { (i + 1f) / n }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val list = docs
            when {
                list == null -> Unit
                list.isEmpty() && query.isBlank() -> EmptyLibrary()
                list.isEmpty() -> Text(trf("“%s” için sonuç yok", query), Modifier.padding(24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { doc ->
                        val isSel = doc.id in selected
                        DocumentRow(vm, doc, isSel,
                            onClick = {
                                if (selected.isNotEmpty()) { if (isSel) selected.remove(doc.id) else selected.add(doc.id) }
                                else vm.navigate(Screen.Document(doc.id))
                            },
                            onLongClick = { if (!isSel) selected.add(doc.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(tr("Belgeleri sil"), trf("%s belge kalıcı olarak silinecek. Bu işlem geri alınamaz.", selected.size), onDismiss = { confirmDelete = false }) {
            val ids = selected.toList()
            selected.clear()
            vm.work(tr("Siliniyor")) { ids.forEach(vm.c.repo::deleteDocument); withContext(Dispatchers.Main) { vm.changed() } }
        }
    }
    mergeName?.let { initial ->
        TextInputDialog(tr("Belgeleri birleştir"), initial, tr("Yeni belge adı"), onDismiss = { mergeName = null }) { name ->
            mergeName = null
            val ids = docs.orEmpty().map { it.id }.filter { it in selected }.reversed() // oldest first
            selected.clear()
            vm.work(tr("Birleştiriliyor")) {
                val id = vm.c.repo.mergeDocuments(name, ids)
                withContext(Dispatchers.Main) { vm.changed(); vm.navigate(Screen.Document(id)) }
            }
        }
    }
}

@Composable
private fun DocumentRow(vm: AppViewModel, doc: DocumentInfo, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val date = remember(doc.updated) { SimpleDateFormat("d MMM yyyy, HH:mm", L.locale).format(Date(doc.updated)) }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                VaultThumb(vm, doc.coverPageId, Modifier.size(64.dp, 84.dp).clip(RoundedCornerShape(6.dp)))
                if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(2.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(trf("%s sayfa · %s", doc.pageCount, date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Shield, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(tr("Şifreli kasanız boş"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Taradığınız ve içe aktardığınız belgeler yalnızca bu uygulamanın içinde, şifreli olarak saklanır. ") +
                tr("Galeriye veya telefon hafızasına kaydedilmez. Uygulamanın internet izni yoktur."),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
