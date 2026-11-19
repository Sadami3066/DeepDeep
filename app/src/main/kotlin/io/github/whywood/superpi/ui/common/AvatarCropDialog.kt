package io.github.whywood.superpi.ui.common

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.design.DdDialog
import io.github.whywood.superpi.design.DdSpinner
import io.github.whywood.superpi.design.DdTextAction
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DECODE_EDGE = 1536
private const val AVATAR_OUTPUT_EDGE = 256
private const val MAX_ZOOM = 4f
private val MaxViewportWidth = 312.dp
private val DialogHorizontalInsets = 104.dp

@Composable
internal fun AvatarCropDialog(
    source: Uri,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val colors = superPiColors()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val viewportDp = minOf(MaxViewportWidth, screenWidth - DialogHorizontalInsets).coerceAtLeast(160.dp)
    val viewport = with(density) { viewportDp.toPx() }
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var zoom by rememberSaveable(source) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(source) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(source) { mutableFloatStateOf(0f) }
    LaunchedEffect(source) {
        val decoded = withContext(Dispatchers.IO) { decodeCropSource(context.contentResolver, source) }
        bitmap = decoded
        failed = decoded == null
    }
    DisposableEffect(source) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("avatar_crop_dialog"),
        title = stringResource(R.string.bot_avatar_crop_title),
        content = {
            Column(Modifier.fillMaxWidth()) {
                val image = bitmap
                Box(
                    Modifier
                        .size(viewportDp)
                        .clip(CircleShape)
                        .background(colors.wash),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image == null) {
                        if (failed) {
                            Text(
                                text = stringResource(R.string.bot_avatar_crop_failed),
                                modifier = Modifier.padding(horizontal = 24.dp),
                                style = DdType.bodySmall,
                                color = colors.danger,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            DdSpinner(size = 22.dp, color = colors.inkTertiary)
                        }
                    } else {
                        val imageBitmap = remember(image) { image.asImageBitmap() }
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .testTag("avatar_crop_viewport")
                                .pointerInput(image, viewport) {
                                    detectCropGestures(
                                        imageWidth = image.width,
                                        imageHeight = image.height,
                                        viewport = viewport,
                                        zoom = { zoom },
                                        offset = { Offset(offsetX, offsetY) },
                                        onUpdate = { nextZoom, nextOffset ->
                                            zoom = nextZoom
                                            offsetX = nextOffset.x
                                            offsetY = nextOffset.y
                                        },
                                    )
                                },
                        ) {
                            drawCropPreview(
                                image = imageBitmap,
                                imageWidth = image.width,
                                imageHeight = image.height,
                                viewport = viewport,
                                zoom = zoom,
                                offset = Offset(offsetX, offsetY),
                            )
                        }
                    }
                }
                if (image != null) {
                    Text(
                        text = stringResource(R.string.bot_avatar_crop_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.bot_avatar_crop_confirm),
                onClick = {
                    val image = bitmap ?: return@DdTextAction
                    cropResult(
                        image = image,
                        viewport = viewport,
                        zoom = zoom,
                        offsetX = offsetX,
                        offsetY = offsetY,
                    )?.let(onCropped)
                },
                enabled = bitmap != null,
                modifier = Modifier.testTag("avatar_crop_confirm"),
            )
        },
    )
}

private suspend fun PointerInputScope.detectCropGestures(
    imageWidth: Int,
    imageHeight: Int,
    viewport: Float,
    zoom: () -> Float,
    offset: () -> Offset,
    onUpdate: (Float, Offset) -> Unit,
) {
    detectTransformGestures { _, pan, gestureZoom, _ ->
        val nextZoom = (zoom() * gestureZoom).coerceIn(1f, MAX_ZOOM)
        val (maxX, maxY) = avatarCropMaxOffset(imageWidth, imageHeight, viewport, nextZoom)
        val next = Offset(
            (offset().x + pan.x).coerceIn(-maxX, maxX),
            (offset().y + pan.y).coerceIn(-maxY, maxY),
        )
        onUpdate(nextZoom, next)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropPreview(
    image: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    viewport: Float,
    zoom: Float,
    offset: Offset,
) {
    val baseScale = viewport / min(imageWidth, imageHeight).toFloat()
    val scale = baseScale * zoom
    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(imageWidth, imageHeight),
        dstOffset = IntOffset(
            ((viewport - drawWidth) / 2f + offset.x).roundToInt(),
            ((viewport - drawHeight) / 2f + offset.y).roundToInt(),
        ),
        dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

private fun decodeCropSource(resolver: ContentResolver, uri: Uri): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(resolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val edge = max(width, height)
        if (edge > MAX_DECODE_EDGE) {
            val scale = MAX_DECODE_EDGE.toFloat() / edge
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}.getOrNull()

private fun cropResult(
    image: Bitmap,
    viewport: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): Bitmap? = runCatching {
    val rect = avatarCropRect(image.width, image.height, viewport, zoom, offsetX, offsetY)
    val square = Bitmap.createBitmap(image, rect.left, rect.top, rect.size, rect.size)
    val output = Bitmap.createBitmap(AVATAR_OUTPUT_EDGE, AVATAR_OUTPUT_EDGE, Bitmap.Config.ARGB_8888)
    AndroidCanvas(output).apply {
        drawColor(android.graphics.Color.WHITE)
        drawBitmap(
            square,
            null,
            Rect(0, 0, AVATAR_OUTPUT_EDGE, AVATAR_OUTPUT_EDGE),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
    }
    if (square !== image) square.recycle()
    output
}.getOrNull()
