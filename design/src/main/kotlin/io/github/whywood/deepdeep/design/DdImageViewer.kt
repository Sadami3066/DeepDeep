package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DdImageViewer(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    DdImageViewerDialog(onDismiss = onDismiss, modifier = modifier) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().padding(DdSpacing.Xl),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun DdImageViewer(
    path: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxLongEdge: Int = DdImageScaling.MAX_EDGE,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxLongEdge) {
        value = withContext(Dispatchers.IO) {
            decodeSampledFile(path, maxLongEdge)?.asImageBitmap()
        }
    }
    DdImageViewerDialog(onDismiss = onDismiss, modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(DdSpacing.Xl),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun DdImageViewer(
    bytes: ByteArray,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxLongEdge: Int = DdImageScaling.MAX_EDGE,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, bytes, maxLongEdge) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBytes(bytes, maxLongEdge)?.asImageBitmap()
        }
    }
    DdImageViewerDialog(onDismiss = onDismiss, modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(DdSpacing.Xl),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun DdImageViewerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = deepDeepColors()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var revealed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { revealed = true }
        val fade by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = DdMotion.FAST, easing = DdMotion.EaseOut),
            label = "dd-image-viewer",
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.scrim)
                .graphicsLayer { alpha = fade }
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
