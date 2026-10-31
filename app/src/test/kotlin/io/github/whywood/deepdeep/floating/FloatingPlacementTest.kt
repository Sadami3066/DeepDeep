package io.github.whywood.deepdeep.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPlacementTest {
    private val screenWidth = 1080
    private val screenHeight = 2400
    private val bubbleSize = 200
    private val margin = 24
    private val gap = 18
    private val padding = 48
    private val arrowInset = 60

    @Test
    fun defaultBubbleSitsOnRightEdgeBelowUpperHalf() {
        val position = floatingDefaultBubblePosition(
            bubbleSize = bubbleSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = 0,
        )
        assertEquals(screenWidth - bubbleSize, position.x)
        assertEquals(1772, position.y)
    }

    @Test
    fun offscreenBubbleIsClampedBackOnScreen() {
        val topLeft = floatingClampBubblePosition(
            x = -400,
            y = -300,
            bubbleSize = bubbleSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = 0,
        )
        assertEquals(FloatingBubblePosition(0, 0), topLeft)
        val bottomRight = floatingClampBubblePosition(
            x = 5_000,
            y = 6_000,
            bubbleSize = bubbleSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = 0,
        )
        assertEquals(
            FloatingBubblePosition(screenWidth - bubbleSize, screenHeight - bubbleSize),
            bottomRight,
        )
    }

    @Test
    fun surfaceSitsAboveBubbleWhenThereIsRoom() {
        val placement = floatingPlaceSurface(
            bubble = FloatingBubblePosition(x = 880, y = 1628),
            bubbleSize = bubbleSize,
            windowWidth = 800,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
            windowPadding = padding,
            arrowInset = arrowInset,
            maxHeightRatio = 0.45f,
        )
        assertTrue(placement.anchorBottom)
        assertEquals(FloatingArrowSide.BOTTOM, placement.arrowSide)
        assertEquals(screenHeight - (1628 - gap), placement.y)
        assertEquals(screenWidth - 800 - margin, placement.x)
        assertEquals(880 + bubbleSize / 2 - (screenWidth - 800 - margin), placement.arrowOffset)
        assertEquals(1080, placement.maxContentHeight)
    }

    @Test
    fun surfaceDropsBelowBubbleWhenTopRoomIsMissing() {
        val placement = floatingPlaceSurface(
            bubble = FloatingBubblePosition(x = 880, y = 100),
            bubbleSize = bubbleSize,
            windowWidth = 800,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
            windowPadding = padding,
            arrowInset = arrowInset,
            maxHeightRatio = 0.45f,
        )
        assertFalse(placement.anchorBottom)
        assertEquals(FloatingArrowSide.TOP, placement.arrowSide)
        assertEquals(100 + bubbleSize + gap, placement.y)
        assertEquals(1080, placement.maxContentHeight)
    }

    @Test
    fun contentHeightNeverExceedsAvailableSpace() {
        val placement = floatingPlaceSurface(
            bubble = FloatingBubblePosition(x = 0, y = 700),
            bubbleSize = bubbleSize,
            windowWidth = 320,
            screenWidth = 500,
            screenHeight = 900,
            edgeMargin = margin,
            gap = gap,
            windowPadding = padding,
            arrowInset = arrowInset,
            maxHeightRatio = 0.70f,
        )
        val available = 700 - gap - margin - padding * 2
        assertEquals(minOf(available, (900 * 0.70f).toInt()), placement.maxContentHeight)
    }

    @Test
    fun arrowStaysInsideSurfaceOnNarrowScreens() {
        val placement = floatingPlaceSurface(
            bubble = FloatingBubblePosition(x = 10, y = 1628),
            bubbleSize = bubbleSize,
            windowWidth = 1100,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
            windowPadding = padding,
            arrowInset = arrowInset,
            maxHeightRatio = 0.45f,
        )
        assertEquals(margin, placement.x)
        assertTrue(placement.arrowOffset >= arrowInset)
        assertTrue(placement.arrowOffset <= 1100 - arrowInset)
    }

    @Test
    fun previewSizeFollowsScreenAspectRatio() {
        val size = floatingPreviewSize(width = 396, aspectRatio = 0.5f, maxHeight = 1_000)
        assertEquals(396, size.width)
        assertEquals(792, size.height)
    }

    @Test
    fun previewSizeCapsHeightAndShrinksWidth() {
        val size = floatingPreviewSize(width = 396, aspectRatio = 0.5f, maxHeight = 500)
        assertEquals(250, size.width)
        assertEquals(500, size.height)
    }

    @Test
    fun previewSizeFallsBackToDefaultRatio() {
        val zero = floatingPreviewSize(width = 360, aspectRatio = 0f, maxHeight = 4_000)
        val negative = floatingPreviewSize(width = 360, aspectRatio = -1f, maxHeight = 4_000)
        assertEquals(640, zero.height)
        assertEquals(zero, negative)
    }

    @Test
    fun previewSitsCenteredAboveBubble() {
        val placement = floatingPlacePreview(
            bubble = FloatingBubblePosition(x = 880, y = 1_628),
            bubbleSize = bubbleSize,
            windowWidth = 100,
            windowHeight = 200,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
        )
        assertEquals(930, placement.x)
        assertEquals(1_410, placement.y)
    }

    @Test
    fun previewDropsBelowBubbleWhenTopRoomIsMissing() {
        val placement = floatingPlacePreview(
            bubble = FloatingBubblePosition(x = 880, y = 100),
            bubbleSize = bubbleSize,
            windowWidth = 100,
            windowHeight = 200,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
        )
        assertEquals(318, placement.y)
    }

    @Test
    fun previewStaysInsideScreenNearLeftEdge() {
        val placement = floatingPlacePreview(
            bubble = FloatingBubblePosition(x = 0, y = 1_628),
            bubbleSize = bubbleSize,
            windowWidth = 400,
            windowHeight = 200,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
        )
        assertEquals(margin, placement.x)
    }

    @Test
    fun previewStaysInsideScreenNearRightEdge() {
        val placement = floatingPlacePreview(
            bubble = FloatingBubblePosition(x = 880, y = 1_628),
            bubbleSize = bubbleSize,
            windowWidth = 400,
            windowHeight = 200,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = margin,
            gap = gap,
        )
        assertEquals(screenWidth - 400 - margin, placement.x)
    }

    @Test
    fun previewFallsBackInsideWhenNeitherSideHasRoom() {
        val placement = floatingPlacePreview(
            bubble = FloatingBubblePosition(x = 250, y = 300),
            bubbleSize = bubbleSize,
            windowWidth = 300,
            windowHeight = 300,
            screenWidth = 500,
            screenHeight = 800,
            edgeMargin = margin,
            gap = gap,
        )
        assertEquals(176, placement.x)
        assertEquals(476, placement.y)
    }
}
