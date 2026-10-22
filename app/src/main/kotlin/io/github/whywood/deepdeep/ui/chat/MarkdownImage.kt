package io.github.whywood.deepdeep.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface MarkdownImageState {
    data object Loading : MarkdownImageState
    data class Ready(val bitmap: Bitmap) : MarkdownImageState
    data object Failed : MarkdownImageState
}

@Composable
internal fun MarkdownImage(alt: String, source: String, title: String?, colors: DeepDeepColors) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape)) {
        val density = LocalDensity.current
        val widthPx = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            with(density) { 360.dp.roundToPx() }
        }
        val targetWidthPx = (widthPx * 2).coerceAtLeast(1)
        val state by produceState<MarkdownImageState>(
            initialValue = MarkdownImageState.Loading,
            key1 = source,
            key2 = targetWidthPx,
        ) {
            value = withContext(Dispatchers.IO) {
                MarkdownImageLoader.load(context.applicationContext, source, targetWidthPx)
            }
        }
        when (val current = state) {
            MarkdownImageState.Loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(shape)
                    .background(colors.wash),
            )

            MarkdownImageState.Failed -> ImageFailure(alt, title, colors)

            is MarkdownImageState.Ready -> LoadedImage(current.bitmap, alt, title, source, colors, widthPx)
        }
    }
}

@Composable
private fun LoadedImage(
    bitmap: Bitmap,
    alt: String,
    title: String?,
    source: String,
    colors: DeepDeepColors,
    containerWidthPx: Int,
) {
    var revealed by remember(bitmap) { mutableStateOf(false) }
    var previewVisible by remember(bitmap) { mutableStateOf(false) }
    LaunchedEffect(bitmap) { revealed = true }
    val fade by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "markdown-image",
    )
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val maxHeightPx = with(density) { 420.dp.roundToPx() }
    val aspect = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat() }
    var heightPx = (containerWidthPx / aspect).toInt().coerceAtLeast(1)
    var widthPx = containerWidthPx
    if (heightPx > maxHeightPx) {
        heightPx = maxHeightPx
        widthPx = (heightPx * aspect).toInt().coerceAtLeast(1)
    }
    val label = alt.ifBlank { title.orEmpty() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = imageBitmap,
            contentDescription = if (label.isBlank()) "预览" else "$label，预览",
            modifier = Modifier
                .size(width = with(density) { widthPx.toDp() }, height = with(density) { heightPx.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .clickable { previewVisible = true }
                .graphicsLayer { alpha = fade }
                .testTag("markdown_image_${source.hashCode()}"),
            contentScale = ContentScale.Fit,
        )
    }
    if (previewVisible) {
        MarkdownImagePreview(
            bitmap = imageBitmap,
            label = label,
            colors = colors,
            onDismiss = { previewVisible = false },
        )
    }
}

@Composable
private fun MarkdownImagePreview(
    bitmap: ImageBitmap,
    label: String,
    colors: DeepDeepColors,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var revealed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { revealed = true }
        val fade by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = DdMotion.FAST, easing = DdMotion.EaseOut),
            label = "markdown-image-preview",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .graphicsLayer { alpha = fade }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = label.ifBlank { null },
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ImageFailure(alt: String, title: String?, colors: DeepDeepColors) {
    val fallback = stringResource(R.string.assistant_image_failed)
    val label = alt.ifBlank { title.orEmpty() }.ifBlank { fallback }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 18.dp)
            Text(
                text = label,
                style = DdType.bodySmall,
                color = colors.inkTertiary.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private object MarkdownImageLoader {
    private const val MemoryCacheBytes = 24 * 1024 * 1024
    private const val MaxDownloadBytes = 40L * 1024 * 1024

    private val memory = object : LruCache<String, Bitmap>(MemoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, source: String, targetWidthPx: Int): MarkdownImageState {
        memory.get(source)?.let { return MarkdownImageState.Ready(it) }
        val remote = source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true)
        return try {
            val file = when {
                remote -> download(context, source)

                source.startsWith("file://", ignoreCase = true) -> {
                    val path = Uri.parse(source).path ?: return MarkdownImageState.Failed
                    File(path)
                }

                else -> File(source)
            }
            if (!file.isFile) return MarkdownImageState.Failed
            val bitmap = decode(file, targetWidthPx)
            if (bitmap == null) {
                if (remote) file.delete()
                return MarkdownImageState.Failed
            }
            memory.put(source, bitmap)
            MarkdownImageState.Ready(bitmap)
        } catch (_: OutOfMemoryError) {
            MarkdownImageState.Failed
        } catch (_: Exception) {
            MarkdownImageState.Failed
        }
    }

    private fun download(context: Context, source: String): File {
        val file = diskFile(context, source)
        if (file.isFile && file.length() > 0L) return file
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DeepDeep")
            setRequestProperty("Accept", "image/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            file.parentFile?.mkdirs()
            val temporary = File.createTempFile("markdown-image", ".tmp", file.parentFile)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MaxDownloadBytes) throw IOException("image too large")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (temporary.length() <= 0L) throw IOException("empty image")
                if (file.exists()) file.delete()
                if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
                return file
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun diskFile(context: Context, source: String): File {
        val directory = File(context.cacheDir, "markdown-images")
        return File(directory, sha256(source) + ".img")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString {
            digest.forEach { byte ->
                append("%02x".format(byte.toInt() and 0xFF))
            }
        }
    }

    private fun decode(file: File, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidthPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int, targetWidthPx: Int): Int {
        val targetWidth = targetWidthPx.coerceAtLeast(1)
        val targetHeight = (targetWidthPx * 2).coerceAtLeast(1)
        var sample = 1
        while (width / (sample * 2) >= targetWidth || height / (sample * 2) >= targetHeight) {
            if (sample >= 16) break
            sample *= 2
        }
        return sample
    }
}
