@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.belgetarayici.crypto.KeyManager
import com.belgetarayici.data.Contact
import com.belgetarayici.io.QrCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QrCodes.encode(text, size)
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun KeysScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val key = remember { vm.c.keys.publicKeyText }
    val qr = remember(key) { qrBitmap(key).asImageBitmap() }
    var version by remember { mutableStateOf(0) }
    val contacts by produceState<List<Contact>>(emptyList(), version) { value = withContext(Dispatchers.IO) { vm.c.repo.contacts() } }
    var scanning by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf<String?>(null) }

    if (scanning) {
        QrScannerScreen(onCancel = { scanning = false }) { text ->
            scanning = false
            if (KeyManager.isValidPublicKey(text)) pendingKey = text else vm.toast(tr("Bu QR bir BelgeKasa anahtarı değil"))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Paylaşım anahtarım")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tr("Size parolasız şifreli belge gönderebilmeleri için bu QR kodu kişilerinize okutun. ") +
                    tr("Bu yalnızca açık anahtarınızdır; gizli anahtarınız cihazınızdan hiç çıkmaz."),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Image(qr, tr("Anahtar QR kodu"), Modifier.size(260.dp).background(Color.White))
            Spacer(Modifier.height(8.dp))
            Text(tr("Parmak izi"), style = MaterialTheme.typography.labelMedium)
            Text(vm.c.keys.publicKeyFingerprint, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { copy(context, key); vm.toast(tr("Anahtar kopyalandı")) }) { Icon(Icons.Default.ContentCopy, null); Text(tr(" Kopyala")) }
                TextButton(onClick = {
                    vm.c.lock.expectExternalActivity()
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, key), tr("Anahtarı gönder")))
                }) { Icon(Icons.Default.Share, null); Text(tr(" Gönder")) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(tr("Kişiler"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            contacts.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(KeyManager.fingerprint(c.key), fontFamily = FontFamily.Monospace) },
                    trailingContent = {
                        IconButton(onClick = { vm.work(tr("Siliniyor")) { vm.c.repo.deleteContact(c.id); withContext(Dispatchers.Main) { version++ } } }) {
                            Icon(Icons.Default.Delete, tr("Sil"))
                        }
                    },
                )
            }
            Row {
                OutlinedButton(onClick = { scanning = true }) { Icon(Icons.Default.QrCodeScanner, null); Text(tr("  QR okut")) }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { pasting = true }) { Text(tr("Metinden ekle")) }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Parmak izini kişiyle yüz yüze ya da telefonla karşılaştırarak doğru anahtarı eklediğinizden emin olun."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(tr("Anahtarı yapıştır")) },
            text = { OutlinedTextField(text, { text = it }, label = { Text(tr("BTKEY1:…")) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    pasting = false
                    if (KeyManager.isValidPublicKey(text)) pendingKey = text.trim() else vm.toast(tr("Geçersiz anahtar"))
                }) { Text(tr("Devam")) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(tr("Vazgeç")) } },
        )
    }
    pendingKey?.let { k ->
        TextInputDialog(trf("Kişiyi kaydet (%s)", KeyManager.fingerprint(k).take(9)), "", tr("Ad"), onDismiss = { pendingKey = null }) { name ->
            pendingKey = null
            vm.work(tr("Kaydediliyor")) { vm.c.repo.saveContact(name, k); withContext(Dispatchers.Main) { version++ } }
        }
    }
}

/** Full-screen QR reader (CameraX + ZXing), entirely on-device. */
@Composable
fun QrScannerScreen(onCancel: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { if (it) granted = true else onCancel() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) permission.launch(android.Manifest.permission.CAMERA) }

    if (granted) DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image ->
                val plane = image.planes[0]
                val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                val text = QrCodes.decode(bytes, plane.rowStride, image.width, image.height)
                image.close()
                if (text != null) previewView.post { if (!done) { done = true; onResult(text) } }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Text(tr("Anahtar QR kodunu çerçeveye alın"), color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(48.dp))
        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 8.dp)) {
            Icon(Icons.Default.Close, tr("Kapat"), tint = Color.White)
        }
    }
}
