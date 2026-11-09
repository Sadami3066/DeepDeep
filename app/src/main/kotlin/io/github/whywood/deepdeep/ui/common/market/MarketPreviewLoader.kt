package io.github.whywood.deepdeep.ui.common.market

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.deepDeepColors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val previewHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(null)
        .build()
}

private val previewMemory = object : android.util.LruCache<String, ImageBitmap>(8) {}

internal suspend fun loadMarketPreview(url: String): ImageBitmap? {
    previewMemory.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            previewHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()?.also { previewMemory.put(url, it) }
            }
        }.getOrNull()
    }
}

@Composable
internal fun MarketPreviewCard(url: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = deepDeepColors()
    val bitmap by produceState<ImageBitmap?>(initialValue = previewMemory.get(url), key1 = url) {
        value = loadMarketPreview(url)
    }
    Box(
        modifier = modifier
            .background(colors.wash, DdShapes.Row)
            .clickable(enabled = bitmap != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            DdSpinner(size = 20.dp, strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun MarketPreviewViewer(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val bitmap by produceState<ImageBitmap?>(initialValue = previewMemory.get(url), key1 = url) {
            value = loadMarketPreview(url)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                DdSpinner(size = 28.dp, strokeWidth = 2.5.dp)
            }
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.market_preview_close),
                tint = Color.White,
                iconSize = 22.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp),
            )
        }
    }
}
