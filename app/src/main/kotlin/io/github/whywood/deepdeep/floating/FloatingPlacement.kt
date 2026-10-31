package io.github.whywood.deepdeep.floating

import kotlin.math.abs
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
    val arrowOffset: Int,
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

fun floatingSnapBubbleToEdge(
    position: FloatingBubblePosition,
    bubbleSize: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
): FloatingBubblePosition {
    val clamped = floatingClampBubblePosition(
        x = position.x,
        y = position.y,
        bubbleSize = bubbleSize,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        edgeMargin = edgeMargin,
    )
    val leftX = edgeMargin
    val rightX = (screenWidth - bubbleSize - edgeMargin).coerceAtLeast(edgeMargin)
    val snappedX = if (abs(clamped.x - leftX) <= abs(clamped.x - rightX)) leftX else rightX
    return clamped.copy(x = snappedX)
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
    arrowInset: Int,
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
    val bubbleCenter = bubble.x + bubbleSize / 2
    val arrowOffset = (bubbleCenter - clampedX).coerceIn(
        arrowInset,
        (windowWidth - arrowInset).coerceAtLeast(arrowInset),
    )
    return if (useAbove) {
        FloatingSurfacePlacement(
            x = clampedX,
            y = screenHeight - windowBottom,
            anchorBottom = true,
            maxContentHeight = maxContentHeight,
            arrowSide = FloatingArrowSide.BOTTOM,
            arrowOffset = arrowOffset,
        )
    } else {
        FloatingSurfacePlacement(
            x = clampedX,
            y = belowTop,
            anchorBottom = false,
            maxContentHeight = maxContentHeight,
            arrowSide = FloatingArrowSide.TOP,
            arrowOffset = arrowOffset,
        )
    }
}
