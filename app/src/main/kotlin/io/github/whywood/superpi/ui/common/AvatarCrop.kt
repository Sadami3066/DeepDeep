package io.github.whywood.superpi.ui.common

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class AvatarCropRect(
    val left: Int,
    val top: Int,
    val size: Int,
)

internal fun avatarCropRect(
    imageWidth: Int,
    imageHeight: Int,
    viewport: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): AvatarCropRect {
    if (imageWidth <= 0 || imageHeight <= 0 || viewport <= 0f) return AvatarCropRect(0, 0, 1)
    val minEdge = min(imageWidth, imageHeight).toFloat()
    val safeZoom = zoom.coerceIn(1f, 8f)
    val sourceSide = minEdge / safeZoom
    val scale = viewport / sourceSide
    val half = sourceSide / 2f
    val centerX = (imageWidth / 2f - offsetX / scale).coerceIn(half, imageWidth - half)
    val centerY = (imageHeight / 2f - offsetY / scale).coerceIn(half, imageHeight - half)
    val size = sourceSide.roundToInt().coerceIn(1, min(imageWidth, imageHeight))
    val left = floor(centerX - half).toInt().coerceIn(0, imageWidth - size)
    val top = floor(centerY - half).toInt().coerceIn(0, imageHeight - size)
    return AvatarCropRect(left, top, size)
}

internal fun avatarCropMaxOffset(imageWidth: Int, imageHeight: Int, viewport: Float, zoom: Float): Pair<Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0 || viewport <= 0f) return 0f to 0f
    val baseScale = viewport / min(imageWidth, imageHeight).toFloat()
    val scale = baseScale * zoom.coerceIn(1f, 8f)
    val maxX = max(0f, (imageWidth * scale - viewport) / 2f)
    val maxY = max(0f, (imageHeight * scale - viewport) / 2f)
    return maxX to maxY
}
