package com.belgetarayici.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads and decrypts an image off the main thread. */
@Composable
fun rememberDecrypted(vararg keys: Any?, load: () -> Bitmap?): ImageBitmap? {
    val state by produceState<ImageBitmap?>(null, *keys) {
        value = withContext(Dispatchers.IO) { runCatching { load()?.asImageBitmap() }.getOrNull() }
    }
    return state
}

@Composable
fun VaultThumb(vm: AppViewModel, pageId: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val img = rememberDecrypted(pageId, vm.dataVersion) { pageId?.let(vm.c.repo::thumbnail) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

/** Pinch-to-zoom / pan image. */
@Composable
fun ZoomableImage(image: ImageBitmap?, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator()
        } else {
            Image(
                image, null,
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
