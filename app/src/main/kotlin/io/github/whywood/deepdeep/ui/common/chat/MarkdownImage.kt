package io.github.whywood.deepdeep.ui.common.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.whywood.deepdeep.data.guessMime
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.ui.common.chat.FileViewerDialog
import io.github.whywood.deepdeep.ui.common.chat.SaveToDownloadsSheet
import io.github.whywood.deepdeep.ui.common.chat.saveMediaToDownloads
import io.github.whywood.deepdeep.ui.common.chat.shareMediaFile
import io.github.whywood.deepdeep.ui.common.chat.shareMediaUrl
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
    val animated = remember(source) { animatedImageFile(source) }
    if (animated != null) {
        AnimatedMarkdownImage(file = animated, alt = alt, title = title, colors = colors)
        return
    }
    val context = LocalContext.current
    val shape = DdShapes.Medium
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
private fun AnimatedMarkdownImage(
    file: File,
    alt: String,
    title: String?,
    colors: DeepDeepColors,
) {
    var previewVisible by remember(file.absolutePath) { mutableStateOf(false) }
    val label = alt.ifBlank { title.orEmpty() }
    val bounds by produceState<Pair<Int, Int>?>(
        initialValue = null,
        file.absolutePath,
        file.lastModified(),
    ) {
        value = withContext(Dispatchers.IO) { imageBounds(file) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val containerWidthPx = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            with(density) { 360.dp.roundToPx() }
        }
        val maxHeightPx = with(density) { 420.dp.roundToPx() }
        val dimensions = bounds?.let { (width, height) ->
            imageDisplaySize(containerWidthPx, width, height, maxHeightPx)
        } ?: imageDisplaySize(containerWidthPx, 3, 2, maxHeightPx)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedImage(
                file = file,
                modifier = Modifier
                    .size(
                        width = with(density) { dimensions.first.toDp() },
                        height = with(density) { dimensions.second.toDp() },
                    )
                    .clip(DdShapes.Medium)
                    .testTag("markdown_animated_image_${file.name.hashCode()}"),
                onClick = { previewVisible = true },
            )
        }
    }
    if (previewVisible) {
        FileViewerDialog(
            file = file,
            name = label.ifBlank { file.name },
            mime = guessMime(file.name),
            onDismiss = { previewVisible = false },
            onShare = null,
        )
    }
}

private fun imageDisplaySize(
    containerWidthPx: Int,
    width: Int,
    height: Int,
    maxHeightPx: Int,
): Pair<Int, Int> {
    val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
    var targetHeight = (containerWidthPx / aspect).toInt().coerceAtLeast(1)
    var targetWidth = containerWidthPx
    if (targetHeight > maxHeightPx) {
        targetHeight = maxHeightPx
        targetWidth = (targetHeight * aspect).toInt().coerceAtLeast(1)
    }
    return targetWidth to targetHeight
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
    val context = LocalContext.current
    val saveFile = remember(source) {
        when {
            source.startsWith("file://") -> Uri.parse(source).path?.let(::File)?.takeIf { it.isFile }
            source.startsWith("http://", ignoreCase = true) ||
                source.startsWith("https://", ignoreCase = true) -> null
            else -> File(source).takeIf { it.isFile }
        }
    }
    val share: (() -> Unit)? = when {
        saveFile != null -> ({ shareMediaFile(context, saveFile, guessMime(saveFile.name)) })
        source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) -> ({ shareMediaUrl(context, source) })
        else -> null
    }
    val qrTarget = remember(source, saveFile) { markdownImageQrTarget(context, source, saveFile) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = imageBitmap,
            contentDescription = if (label.isBlank()) {
                stringResource(R.string.assistant_image_preview)
            } else {
                "$label · ${stringResource(R.string.assistant_image_preview)}"
            },
            modifier = Modifier
                .size(width = with(density) { widthPx.toDp() }, height = with(density) { heightPx.toDp() })
                .clip(DdShapes.Medium)
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
            onSave = saveFile?.let { file ->
                { saveMediaToDownloads(context, file, guessMime(file.name)) }
            },
            onShare = share,
            qrTarget = qrTarget,
            saveName = saveFile?.name ?: label.ifBlank { stringResource(R.string.assistant_image_preview) },
        )
    }
}

@Composable
private fun MarkdownImagePreview(
    bitmap: ImageBitmap,
    label: String,
    colors: DeepDeepColors,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)?,
    onShare: (() -> Unit)?,
    qrTarget: QrScanTarget?,
    saveName: String,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var revealed by remember { mutableStateOf(false) }
        var saveSheet by rememberSaveable { mutableStateOf(false) }
        var qrSheet by rememberSaveable { mutableStateOf(false) }
        val qrScanner = rememberQrScanner(qrTarget)
        LaunchedEffect(Unit) { revealed = true }
        val fade by animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = DdMotion.FAST, easing = DdMotion.EaseOut),
            label = "markdown-image-preview",
        )
        val surface = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .graphicsLayer { alpha = fade }
        Box(
            surface.pointerInput(qrTarget) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onLongPress = {
                        saveSheet = true
                        if (qrTarget != null) qrScanner.requestScan(qrTarget)
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = label.ifBlank { null },
                modifier = Modifier.fillMaxSize().padding(DdSpacing.Xl),
                contentScale = ContentScale.Fit,
            )
        }
        if (saveSheet) {
            SaveToDownloadsSheet(
                name = saveName,
                onDismiss = { saveSheet = false },
                onSave = onSave?.let { save ->
                    {
                        saveSheet = false
                        save()
                    }
                },
                onShare = onShare,
                qrStatus = qrScanner.state.status,
                onOpenQr = {
                    saveSheet = false
                    qrSheet = true
                },
                onRetryQr = qrScanner::retry,
            )
        }
        if (qrSheet) {
            QrCodeResultSheet(codes = qrScanner.state.codes, onDismiss = { qrSheet = false })
        }
    }
}

private fun markdownImageQrTarget(context: Context, source: String, saveFile: File?): QrScanTarget? {
    if (saveFile != null) return QrScanTarget.LocalFile(saveFile.absolutePath)
    val remote = remoteImageUrl(source) ?: return null
    val cached = MarkdownImageLoader.cachedFile(context, source)
    return cached?.let { QrScanTarget.LocalFile(it.absolutePath) } ?: QrScanTarget.RemoteUrl(remote)
}

@Composable
private fun ImageFailure(alt: String, title: String?, colors: DeepDeepColors) {
    val fallback = stringResource(R.string.assistant_image_failed)
    val label = alt.ifBlank { title.orEmpty() }.ifBlank { fallback }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(DdShapes.Medium)
            .background(colors.wash)
            .padding(horizontal = DdSpacing.Lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
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

    fun cachedFile(context: Context, source: String): File? =
        diskFile(context, source).takeIf { it.isFile && it.length() > 0L }

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