package io.github.whywood.deepdeep.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

sealed interface DdRemoteImageState {
    data object Loading : DdRemoteImageState

    data object Failed : DdRemoteImageState

    data class Ready(val bitmap: ImageBitmap) : DdRemoteImageState
}

@Composable
fun DdRemoteImage(
    url: String,
    load: suspend (String) -> ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = DdShapes.Small,
    loading: (@Composable BoxScope.() -> Unit)? = null,
    failure: (@Composable BoxScope.() -> Unit)? = null,
) {
    val currentLoad by rememberUpdatedState(load)
    val state by produceState<DdRemoteImageState>(
        initialValue = DdRemoteImageState.Loading,
        key1 = url,
    ) {
        value = if (url.isBlank()) {
            DdRemoteImageState.Failed
        } else {
            val bitmap = runCatching { currentLoad(url) }.getOrNull()
            if (bitmap == null) DdRemoteImageState.Failed else DdRemoteImageState.Ready(bitmap)
        }
    }
    val colors = deepDeepColors()
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        when (val current = state) {
            DdRemoteImageState.Loading -> Box(
                modifier = Modifier.fillMaxSize().background(colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                if (loading != null) loading() else DdSpinner(size = 16.dp, strokeWidth = 2.dp)
            }

            DdRemoteImageState.Failed -> Box(
                modifier = Modifier.fillMaxSize().background(colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                if (failure != null) {
                    failure()
                } else {
                    DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 18.dp)
                }
            }

            is DdRemoteImageState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
