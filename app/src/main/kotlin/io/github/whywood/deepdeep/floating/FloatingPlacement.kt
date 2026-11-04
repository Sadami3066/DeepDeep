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

data class FloatingSurfaceAnchor(
    val x: Int,
    val y: Int,
    val anchorBottom: Boolean,
    val arrowSide: FloatingArrowSide,
    val alignEnd: Boolean,
    val available: Int,
)

fun floatingSurfaceAnchor(
    bubble: FloatingBubblePosition,
    bubbleSize: Int,
    windowWidth: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
    gap: Int,
): FloatingSurfaceAnchor {
    val rightX = screenWidth - windowWidth - edgeMargin
    val alignEnd = bubble.x + bubbleSize / 2 >= screenWidth / 2
    val clampedX = (if (alignEnd) rightX else edgeMargin)
        .coerceIn(edgeMargin, rightX.coerceAtLeast(edgeMargin))
    val windowBottom = bubble.y - gap
    val belowTop = bubble.y + bubbleSize + gap
    val aboveSpace = windowBottom - edgeMargin
    val belowSpace = screenHeight - edgeMargin - belowTop
    val useAbove = aboveSpace >= belowSpace
    val available = (if (useAbove) aboveSpace else belowSpace).coerceAtLeast(0)
    return if (useAbove) {
        FloatingSurfaceAnchor(
            x = clampedX,
            y = screenHeight - windowBottom,
            anchorBottom = true,
            arrowSide = FloatingArrowSide.BOTTOM,
            alignEnd = alignEnd,
            available = available,
        )
    } else {
        FloatingSurfaceAnchor(
            x = clampedX,
            y = belowTop,
            anchorBottom = false,
            arrowSide = FloatingArrowSide.TOP,
            alignEnd = alignEnd,
            available = available,
        )
    }
}

data class FloatingPreviewSpec(
    val width: Int,
    val aspectRatio: Float,
    val maxHeightRatio: Float,
    val chipHeight: Int,
    val online: Boolean,
)

data class FloatingStackPlacement(
    val x: Int,
    val y: Int,
    val anchorBottom: Boolean,
    val arrowSide: FloatingArrowSide,
    val alignEnd: Boolean,
    val maxContentHeight: Int,
    val preview: FloatingPreviewSize?,
)

fun floatingPlaceSurfaceStack(
    bubble: FloatingBubblePosition,
    bubbleSize: Int,
    windowWidth: Int,
    screenWidth: Int,
    screenHeight: Int,
    edgeMargin: Int,
    gap: Int,
    windowPadding: Int,
    blockGap: Int,
    maxHeightRatio: Float,
    minBodyHeight: Int,
    preview: FloatingPreviewSpec?,
): FloatingStackPlacement {
    val anchor = floatingSurfaceAnchor(
        bubble = bubble,
        bubbleSize = bubbleSize,
        windowWidth = windowWidth,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        edgeMargin = edgeMargin,
        gap = gap,
    )
    val inner = (anchor.available - windowPadding * 2).coerceAtLeast(0)
    val previewSize = preview?.let {
        floatingStackPreview(
            spec = it,
            inner = inner,
            blockGap = blockGap,
            minBodyHeight = minBodyHeight,
            screenHeight = screenHeight,
        )
    }
    val bodyMaxHeight = floatingBodyMaxHeight(
        available = inner - (previewSize?.let { it.height + blockGap } ?: 0),
        screenHeight = screenHeight,
        maxHeightRatio = maxHeightRatio,
    )
    return FloatingStackPlacement(
        x = anchor.x,
        y = anchor.y,
        anchorBottom = anchor.anchorBottom,
        arrowSide = anchor.arrowSide,
        alignEnd = anchor.alignEnd,
        maxContentHeight = bodyMaxHeight,
        preview = previewSize,
    )
}

private fun floatingBodyMaxHeight(
    available: Int,
    screenHeight: Int,
    maxHeightRatio: Float,
): Int = minOf(
    available.coerceAtLeast(0),
    (screenHeight * maxHeightRatio).roundToInt(),
)

private fun floatingStackPreview(
    spec: FloatingPreviewSpec,
    inner: Int,
    blockGap: Int,
    minBodyHeight: Int,
    screenHeight: Int,
): FloatingPreviewSize? {
    val maxPreviewHeight = minOf(
        (screenHeight * spec.maxHeightRatio).roundToInt(),
        inner - blockGap - minBodyHeight,
    )
    if (maxPreviewHeight < spec.chipHeight) return null
    return if (spec.online) {
        val size = floatingPreviewSize(
            width = spec.width,
            aspectRatio = spec.aspectRatio,
            maxHeight = maxPreviewHeight,
        )
        size.takeIf { it.width >= spec.width * FLOATING_STACK_PREVIEW_MIN_WIDTH_RATIO }
    } else {
        FloatingPreviewSize(width = spec.width, height = spec.chipHeight)
    }
}

private const val FLOATING_STACK_PREVIEW_MIN_WIDTH_RATIO = 2f / 3f

fun floatingStandalonePreviewSize(
    spec: FloatingPreviewSpec,
    screenHeight: Int,
    windowPadding: Int,
): FloatingPreviewSize = if (spec.online) {
    floatingPreviewSize(
        width = spec.width,
        aspectRatio = spec.aspectRatio,
        maxHeight = (screenHeight * spec.maxHeightRatio).toInt() - windowPadding * 2,
    )
} else {
    FloatingPreviewSize(width = spec.width, height = spec.chipHeight)
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
