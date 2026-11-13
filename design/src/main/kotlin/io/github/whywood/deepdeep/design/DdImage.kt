package io.github.whywood.deepdeep.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DdImageLoad {
    data object Loading : DdImageLoad
    data object Failed : DdImageLoad
    data class Ready(val bitmap: ImageBitmap) : DdImageLoad
}

@Composable
fun DdImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = DdShapes.Medium,
    maxLongEdge: Int = DdImageScaling.MAX_EDGE,
    failure: (@Composable BoxScope.() -> Unit)? = null,
) {
    val load by produceState<DdImageLoad>(
        initialValue = DdImageLoad.Loading,
        key1 = path,
        key2 = maxLongEdge,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledFile(path, maxLongEdge)?.let { bitmap ->
                DdImageLoad.Ready(bitmap.asImageBitmap())
            } ?: DdImageLoad.Failed
        }
    }
    DdImageFrame(load, contentDescription, modifier, contentScale, shape, failure)
}

@Composable
fun DdImage(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = DdShapes.Medium,
    maxLongEdge: Int = DdImageScaling.MAX_EDGE,
    failure: (@Composable BoxScope.() -> Unit)? = null,
) {
    val key = remember(bytes) { bytes.contentHashCode() to bytes.size }
    val load by produceState<DdImageLoad>(
        initialValue = DdImageLoad.Loading,
        key1 = key,
        key2 = maxLongEdge,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBytes(bytes, maxLongEdge)?.let { bitmap ->
                DdImageLoad.Ready(bitmap.asImageBitmap())
            } ?: DdImageLoad.Failed
        }
    }
    DdImageFrame(load, contentDescription, modifier, contentScale, shape, failure)
}

@Composable
fun DdImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = DdShapes.Medium,
) {
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}

@Composable
private fun DdImageFrame(
    load: DdImageLoad,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    shape: Shape,
    failure: (@Composable BoxScope.() -> Unit)?,
) {
    val colors = deepDeepColors()
    when (val current = load) {
        DdImageLoad.Loading -> Box(modifier.clip(shape).background(colors.wash))

        DdImageLoad.Failed -> Box(modifier.clip(shape).background(colors.wash), contentAlignment = Alignment.Center) {
            if (failure != null) {
                failure()
            } else {
                DdIcon(
                    icon = DdIcons.Image,
                    tint = colors.inkTertiary,
                    modifier = Modifier.padding(DdSpacing.Lg),
                    size = 22.dp,
                    contentDescription = contentDescription,
                )
            }
        }

        is DdImageLoad.Ready -> Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

internal fun decodeSampledFile(path: String, maxLongEdge: Int): Bitmap? {
    val file = when {
        path.startsWith("file://", ignoreCase = true) -> Uri.parse(path).path?.let(::File)
        else -> File(path)
    } ?: return null
    if (!file.isFile) return null
    return runCatching { decodeSampled(file.absolutePath, maxLongEdge) { options -> BitmapFactory.decodeFile(file.absolutePath, options) } }
        .getOrNull()
}

internal fun decodeSampledBytes(bytes: ByteArray, maxLongEdge: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    return runCatching { decodeSampled(null, maxLongEdge) { options -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) } }
        .getOrNull()
}

private fun decodeSampled(
    path: String?,
    maxLongEdge: Int,
    decode: (BitmapFactory.Options) -> Bitmap?,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (path != null) BitmapFactory.decodeFile(path, bounds) else decode(bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = sampleSizeForLongEdge(bounds.outWidth, bounds.outHeight, maxLongEdge.coerceAtLeast(1))
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = decode(options) ?: return null
    val longEdge = maxOf(decoded.width, decoded.height)
    if (longEdge <= maxLongEdge) return decoded
    val scaled = DdImageScaling.scaledSizeFor(decoded.width, decoded.height, maxLongEdge)
    val result = Bitmap.createScaledBitmap(decoded, scaled.width, scaled.height, true)
    if (result !== decoded) decoded.recycle()
    return result
}

private fun sampleSizeForLongEdge(width: Int, height: Int, maxLongEdge: Int): Int {
    val longEdge = maxOf(width, height)
    var sample = 1
    while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
    return sample
}
