package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.belgetarayici.imaging.AutoCaptureTrigger
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.imaging.DocumentDetector
import com.belgetarayici.imaging.Quad
import com.belgetarayici.imaging.QuadTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun CameraScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) { vm.c.lock.expectExternalActivity(); permission.launch(Manifest.permission.CAMERA) } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.addGalleryImages(uris)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(vm)
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Tarama için kamera izni gerekli. Dilerseniz galeriden görüntü de seçebilirsiniz."), color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(tr("İzin ver")) }
            }
        }

        // Top bar
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.captures.clear(); vm.back() }) { Icon(Icons.Default.Close, tr("Kapat"), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                vm.c.lock.expectExternalActivity()
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Icon(Icons.Default.PhotoLibrary, tr("Galeriden"), tint = Color.White) }
        }
    }
}

@Composable
private fun CameraPreview(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var quad by remember { mutableStateOf<Quad?>(null) }
    var torch by remember { mutableStateOf(false) }
    var autoCapture by remember { mutableStateOf(vm.c.settings.autoCapture) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val selector = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    }
    val imageCapture = remember {
        ImageCapture.Builder().setResolutionSelector(selector).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val tracker = remember { QuadTracker() }
    val stability = remember { AutoCaptureTrigger() }
    var countdown by remember { mutableFloatStateOf(0f) }
    val flash = remember { Animatable(0f) }
    var shotNotice by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    var zoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }

    fun capture() {
        if (capturing) return
        capturing = true
        val hint = tracker.current
        // Clear feedback that a page was taken: sound, vibration, a white flash and a notice.
        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scope.launch { flash.snapTo(0.7f); flash.animateTo(0f, tween(350)) }
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bmp = image.toBitmap()
                image.close()
                vm.addCapture(Bitmaps.rotate(Bitmaps.capEdge(bmp, Bitmaps.MAX_PAGE_EDGE), rotation), hint)
                stability.reset()
                countdown = 0f
                capturing = false
                val n = vm.captures.size + 1
                scope.launch { shotNotice = trf("Sayfa %s çekildi", n); delay(1500); if (shotNotice == trf("Sayfa %s çekildi", n)) shotNotice = null }
            }

            override fun onError(exception: ImageCaptureException) {
                capturing = false
                vm.toast(trf("Çekim başarısız: %s", exception.message))
            }
        })
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(Size(640, 480),
                            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { image ->
                val q = runCatching { detect(image) }.getOrNull()
                image.close()
                previewView.post {
                    val steady = tracker.update(q)
                    quad = steady
                    val fire = stability.update(steady)
                    countdown = if (autoCapture && !capturing) stability.progress else 0f
                    if (fire && autoCapture && !capturing) capture()
                }
            }
            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis).also { cam ->
                    // Only offer zoom steps the lens can do (some cameras, e.g. the emulator's, have none).
                    cam.cameraInfo.zoomState.observe(lifecycle) { maxZoom = it.maxZoomRatio }
                }
            }.onFailure { vm.toast(trf("Kamera açılamadı: %s", it.message)) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            analysisExecutor.shutdown()
            shutterSound.release()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f)) {
                androidx.compose.ui.viewinterop.AndroidView({ previewView }, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val q = quad ?: return@Canvas
                    val path = Path().apply {
                        q.points.forEachIndexed { i, p ->
                            val o = Offset(p.x * size.width, p.y * size.height)
                            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                        close()
                    }
                    // Turns green while the auto-capture countdown runs.
                    val steady = countdown > 0f
                    drawPath(path, if (steady) Color(0x5543A047) else Color(0x552196F3))
                    drawPath(path, if (steady) Color(0xFF66BB6A) else Color(0xFF42A5F5), style = Stroke(width = 4.dp.toPx()))
                }
                if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
                shotNotice?.let {
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 72.dp), shape = RoundedCornerShape(16.dp), color = Color(0xDD2E7D32)) {
                        Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // Bottom controls
        Column(Modifier.fillMaxWidth().background(Color(0xCC000000)).navigationBarsPadding().padding(bottom = 16.dp, top = 8.dp)) {
            // Zoom helps fill the frame with small documents (receipts, cards) from a distance.
            if (maxZoom >= 2f) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
                listOf(1f, 2f, 3f).filter { it <= maxZoom }.forEach { z ->
                    FilterChip(
                        selected = zoom == z,
                        onClick = {
                            zoom = z
                            camera?.cameraControl?.setZoomRatio(z)
                            stability.reset()
                        },
                        label = { Text("${z.toInt()}×") },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Color.White, selectedLabelColor = Color.Black),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.scanMode == mode,
                        onClick = { vm.scanMode = mode },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White,
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            val hint = when (vm.scanMode) {
                ScanMode.ID_CARD -> if (vm.captures.size % 2 == 0) tr("Kartın ön yüzünü çekin") else tr("Şimdi arka yüzü çekin")
                ScanMode.BOOK -> tr("Açık kitabın iki sayfasını birlikte çekin")
                ScanMode.DOCUMENT -> when {
                    capturing -> tr("Çekiliyor…")
                    quad == null -> tr("Belgeyi çerçeveye alın")
                    autoCapture -> tr("Belge algılandı — sabit tutun")
                    else -> tr("Belge algılandı")
                }
            }
            Text(hint, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) {
                        Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, tr("Flaş"), tint = Color.White)
                    }
                    IconButton(onClick = { autoCapture = !autoCapture; vm.c.settings.autoCapture = autoCapture }) {
                        Icon(Icons.Default.AutoMode, tr("Otomatik çekim"), tint = if (autoCapture) Color(0xFF42A5F5) else Color.Gray)
                    }
                }
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(if (capturing) Color.LightGray else Color.White)
                            .border(4.dp, Color.Gray, CircleShape).clickable(enabled = !capturing) { capture() },
                    )
                    if (capturing) CircularProgressIndicator(Modifier.size(40.dp), color = Color.DarkGray)
                    // Auto-capture countdown ring.
                    if (countdown > 0f) Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color(0xFF66BB6A), -90f, 360f * countdown, false, style = Stroke(width = 5.dp.toPx()))
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val last = vm.captures.lastOrNull()
                    if (last != null) {
                        BadgedBox(badge = { Badge { Text("${vm.captures.size}") } }) {
                            val thumb = remember(last.original) { Bitmaps.thumbnail(last.original).asImageBitmap() }
                            androidx.compose.foundation.Image(
                                thumb, tr("Gözden geçir"),
                                Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).clickable { vm.navigate(Screen.ScanReview) },
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
            // Always laid out (invisible until the first shot) so the shutter never jumps under the finger.
            Button(
                onClick = { vm.navigate(Screen.ScanReview) },
                enabled = vm.captures.isNotEmpty(),
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, top = 8.dp).alpha(if (vm.captures.isEmpty()) 0f else 1f),
            ) { Text(trf("Bitti (%s)", vm.captures.size)) }
        }
    }
}

/** Copies the luminance plane and runs document detection in display orientation. */
private fun detect(image: ImageProxy): Quad? {
    val plane = image.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    return DocumentDetector.detectLuminance(bytes, plane.rowStride, image.width, image.height, image.imageInfo.rotationDegrees)
}
