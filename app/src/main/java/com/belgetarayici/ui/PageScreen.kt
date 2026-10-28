@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.belgetarayici.data.PageInfo
import com.belgetarayici.imaging.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PageScreen(vm: AppViewModel, pageId: String) {
    val context = LocalContext.current
    val info by produceState<Pair<PageInfo, List<String>>?>(null, pageId, vm.dataVersion) {
        value = withContext(Dispatchers.IO) {
            val p = vm.c.repo.page(pageId) ?: return@withContext null
            p to vm.c.repo.pages(p.docId).map { it.id }
        }
    }
    val image = rememberDecrypted(pageId, vm.dataVersion) { vm.c.repo.pageBitmap(pageId) }
    var showText by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val siblings = info?.second.orEmpty()
    val pos = siblings.indexOf(pageId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pos >= 0) trf("Sayfa %s / %s", pos + 1, siblings.size) else tr("Sayfa")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
                actions = {
                    IconButton(onClick = { vm.replaceTop(Screen.Page(siblings[pos - 1])) }, enabled = pos > 0) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, tr("Önceki"))
                    }
                    IconButton(onClick = { vm.replaceTop(Screen.Page(siblings[pos + 1])) }, enabled = pos in 0 until siblings.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, tr("Sonraki"))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { vm.navigate(Screen.Recrop(pageId)) }) { Icon(Icons.Default.Crop, tr("Kırp / filtre")) }
                IconButton(onClick = {
                    vm.work(tr("Döndürülüyor")) {
                        vm.c.repo.replacePageImageBakingEdits(pageId, Bitmaps.rotate(vm.c.repo.pageBitmap(pageId), 90))
                        withContext(Dispatchers.Main) { vm.changed() }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.RotateRight, tr("Döndür")) }
                IconButton(onClick = { vm.navigate(Screen.Sign(pageId)) }) { Icon(Icons.Default.Draw, tr("İmza")) }
                IconButton(onClick = { vm.navigate(Screen.Annotate(pageId)) }) { Icon(Icons.Default.Gesture, tr("Kalem")) }
                IconButton(onClick = { showText = true }) { Icon(Icons.AutoMirrored.Filled.TextSnippet, tr("Metin")) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, tr("Sil")) }
            }
        },
    ) { padding ->
        ZoomableImage(image, Modifier.padding(padding).fillMaxSize())
    }

    if (showText) {
        val p = info?.first
        val text = p?.ocrText.orEmpty()
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text(tr("Sayfa metni")) },
            text = {
                Column {
                    if (text.isBlank()) Text(tr("Bu sayfada henüz tanınmış metin yok."))
                    else SelectionContainer { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }
                }
            },
            confirmButton = {
                if (text.isBlank()) TextButton(onClick = {
                    showText = false
                    p?.let { vm.runOcr(it.docId, onlyMissing = false, pageIds = listOf(pageId)) }
                }) { Text(tr("Metni tanı")) }
                else TextButton(onClick = { copy(context, text); vm.toast(tr("Panoya kopyalandı")); showText = false }) { Text(tr("Kopyala")) }
            },
            dismissButton = { TextButton(onClick = { showText = false }) { Text(tr("Kapat")) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(tr("Sayfayı sil"), tr("Bu sayfa kalıcı olarak silinecek."), onDismiss = { confirmDelete = false }) {
            vm.work(tr("Siliniyor")) { vm.c.repo.deletePage(pageId); withContext(Dispatchers.Main) { vm.changed(); vm.back() } }
        }
    }
}
