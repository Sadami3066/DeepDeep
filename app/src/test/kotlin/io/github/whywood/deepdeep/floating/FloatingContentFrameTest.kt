package io.github.whywood.deepdeep.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingContentFrameTest {
    @Test
    fun portraitContentFrameExcludesStatusAndNavigationBars() {
        val frame = floatingContentFrame(
            displayWidth = 1220,
            displayHeight = 2712,
            insets = FloatingFrameInsets(left = 0, top = 138, right = 0, bottom = 52),
        )
        assertEquals(FloatingContentFrame(left = 0, top = 138, width = 1220, height = 2522), frame)
    }

    @Test
    fun landscapeInsetsShrinkFrameOnBothAxes() {
        val frame = floatingContentFrame(
            displayWidth = 2712,
            displayHeight = 1220,
            insets = FloatingFrameInsets(left = 138, top = 0, right = 0, bottom = 52),
        )
        assertEquals(FloatingContentFrame(left = 138, top = 0, width = 2574, height = 1168), frame)
    }

    @Test
    fun insetsNeverProduceEmptyFrame() {
        val frame = floatingContentFrame(
            displayWidth = 100,
            displayHeight = 100,
            insets = FloatingFrameInsets(left = 80, top = 80, right = 80, bottom = 80),
        )
        assertEquals(1, frame.width)
        assertEquals(1, frame.height)
    }

    @Test
    fun fullFrameMatchesDisplaySize() {
        assertEquals(
            FloatingContentFrame(left = 0, top = 0, width = 1220, height = 2712),
            floatingFullContentFrame(displayWidth = 1220, displayHeight = 2712),
        )
    }

    @Test
    fun bottomAnchoredSurfaceUsesContentFrameHeight() {
        val content = floatingContentFrame(
            displayWidth = 1220,
            displayHeight = 2712,
            insets = FloatingFrameInsets(left = 0, top = 138, right = 0, bottom = 52),
        )
        val bubble = FloatingBubblePosition(x = 940, y = 2140)
        val placement = floatingPlaceSurface(
            bubble = bubble,
            bubbleSize = 220,
            windowWidth = 1000,
            screenWidth = content.width,
            screenHeight = content.height,
            edgeMargin = 24,
            gap = 18,
            windowPadding = 48,
            arrowInset = 60,
            maxHeightRatio = 0.70f,
        )
        assertTrue(placement.anchorBottom)
        assertEquals(FloatingArrowSide.BOTTOM, placement.arrowSide)
        assertEquals(content.height - (bubble.y - 18), placement.y)
    }

    @Test
    fun systemBarsCauseThePanelGapThatThisFrameFixes() {
        val content = floatingContentFrame(
            displayWidth = 1220,
            displayHeight = 2712,
            insets = FloatingFrameInsets(left = 0, top = 138, right = 0, bottom = 52),
        )
        val bubble = FloatingBubblePosition(x = 940, y = 2140)
        val withContent = floatingPlaceSurface(
            bubble = bubble,
            bubbleSize = 220,
            windowWidth = 1000,
            screenWidth = content.width,
            screenHeight = content.height,
            edgeMargin = 24,
            gap = 18,
            windowPadding = 48,
            arrowInset = 60,
            maxHeightRatio = 0.70f,
        )
        val withDisplay = floatingPlaceSurface(
            bubble = bubble,
            bubbleSize = 220,
            windowWidth = 1000,
            screenWidth = 1220,
            screenHeight = 2712,
            edgeMargin = 24,
            gap = 18,
            windowPadding = 48,
            arrowInset = 60,
            maxHeightRatio = 0.70f,
        )
        assertEquals(190, withDisplay.y - withContent.y)
    }
}
