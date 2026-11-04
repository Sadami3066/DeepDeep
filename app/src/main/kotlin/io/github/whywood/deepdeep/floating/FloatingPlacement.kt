package io.github.whywood.deepdeep.floating

import kotlin.math.roundToInt

const val FLOATING_DEFAULT_BUBBLE_Y_RATIO = 0.78f

data class FloatingBubblePosition(
    val x: Int,
    val y: Int,
)

enum class FloatingArrowSide {
    TOP,
    BOTTOM,
}

data class FloatingSurfacePlacement(
    val x: Int,
    val y: Int,
    val anchorBottom: Boolean,
    val maxContentHeight: Int,
    val arrowSide: FloatingArrowSide,
)

fun floatingDefaultBubblePosition(
    bubbleSize: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
): FloatingBubblePosition = floatingClampBubblePosition(
    x = screenWidth - bubbleSize - edgeMargin,
    y = (screenHeight * FLOATING_DEFAULT_BUBBLE_Y_RATIO - bubbleSize / 2f).roundToInt(),
    bubbleSize = bubbleSize,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    edgeMargin = edgeMargin,
)

fun floatingClampBubblePosition(
    x: Int,
    y: Int,
    bubbleSize: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
): FloatingBubblePosition {
    val maxX = (screenWidth - bubbleSize - edgeMargin).coerceAtLeast(edgeMargin)
    val maxY = (screenHeight - bubbleSize - edgeMargin).coerceAtLeast(edgeMargin)
    return FloatingBubblePosition(
        x = x.coerceIn(edgeMargin, maxX),
        y = y.coerceIn(edgeMargin, maxY),
    )
}

fun floatingPlaceSurface(
    bubble: FloatingBubblePosition,
    bubbleSize: Int,
    windowWidth: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
    gap: Int,
    windowPadding: Int,
    maxHeightRatio: Float,
): FloatingSurfacePlacement {
    val rightX = screenWidth - windowWidth - edgeMargin
    val clampedX = (if (bubble.x + bubbleSize / 2 < screenWidth / 2) {
        edgeMargin
    } else {
        rightX
    }).coerceIn(edgeMargin, rightX.coerceAtLeast(edgeMargin))
    val windowBottom = bubble.y - gap
    val belowTop = bubble.y + bubbleSize + gap
    val aboveSpace = windowBottom - edgeMargin
    val belowSpace = screenHeight - edgeMargin - belowTop
    val useAbove = aboveSpace >= belowSpace
    val available = (if (useAbove) aboveSpace else belowSpace).coerceAtLeast(0)
    val maxContentHeight = minOf(
        (available - windowPadding * 2).coerceAtLeast(0),
        (screenHeight * maxHeightRatio).roundToInt(),
    )
    return if (useAbove) {
        FloatingSurfacePlacement(
            x = clampedX,
            y = screenHeight - windowBottom,
            anchorBottom = true,
            maxContentHeight = maxContentHeight,
            arrowSide = FloatingArrowSide.BOTTOM,
        )
    } else {
        FloatingSurfacePlacement(
            x = clampedX,
            y = belowTop,
            anchorBottom = false,
            maxContentHeight = maxContentHeight,
            arrowSide = FloatingArrowSide.TOP,
        )
    }
}

data class FloatingPreviewSize(
    val width: Int,
    val height: Int,
)

data class FloatingPreviewPlacement(
    val x: Int,
    val y: Int,
    val arrowSide: FloatingArrowSide,
)

fun floatingPreviewSize(
    width: Int,
    aspectRatio: Float,
    maxHeight: Int,
): FloatingPreviewSize {
    val ratio = aspectRatio.takeIf { it > 0f } ?: FLOATING_PREVIEW_DEFAULT_RATIO
    val height = (width / ratio).roundToInt().coerceAtLeast(1)
    return if (height <= maxHeight) {
        FloatingPreviewSize(width, height)
    } else {
        val cappedHeight = maxHeight.coerceAtLeast(1)
        FloatingPreviewSize((cappedHeight * ratio).roundToInt().coerceAtLeast(1), cappedHeight)
    }
}

fun floatingPlacePreview(
    bubble: FloatingBubblePosition,
    bubbleSize: Int,
    windowWidth: Int,
    windowHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
    gap: Int,
): FloatingPreviewPlacement {
    val maxX = (screenWidth - windowWidth - edgeMargin).coerceAtLeast(edgeMargin)
    val centeredX = bubble.x + bubbleSize / 2 - windowWidth / 2
    val aboveY = bubble.y - gap - windowHeight
    val belowY = bubble.y + bubbleSize + gap
    val canAbove = aboveY >= edgeMargin
    val canBelow = belowY + windowHeight <= screenHeight - edgeMargin
    val preferAbove = bubble.y + bubbleSize / 2 > screenHeight / 2
    val above = when {
        preferAbove && canAbove -> true
        !preferAbove && canBelow -> false
        canAbove -> true
        canBelow -> false
        else -> preferAbove
    }
    val y = when {
        above && canAbove -> aboveY
        !above && canBelow -> belowY
        else -> (screenHeight - windowHeight - edgeMargin).coerceAtLeast(edgeMargin)
    }
    return FloatingPreviewPlacement(
        x = centeredX.coerceIn(edgeMargin, maxX),
        y = y,
        arrowSide = if (above) FloatingArrowSide.BOTTOM else FloatingArrowSide.TOP,
    )
}
