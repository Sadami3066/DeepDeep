package io.github.whywood.deepdeep.vscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenFramesTest {
    @Test
    fun fullyTransparentFrameHasRatioOne() {
        assertEquals(1f, ratio(alpha = byteArrayOf(0, 0, 0, 0)), 0.0001f)
    }

    @Test
    fun fullyOpaqueFrameHasRatioZero() {
        assertEquals(0f, ratio(alpha = byteArrayOf(-1, -1, -1, -1)), 0.0001f)
    }

    @Test
    fun halfTransparentFrameReportsHalf() {
        assertEquals(0.5f, ratio(alpha = byteArrayOf(0, 0, -1, -1)), 0.0001f)
    }

    @Test
    fun rowStridePaddingIsSkipped() {
        val pixels = ByteArray(2 * 16)
        pixels[3] = 0
        pixels[7] = -1
        pixels[16 + 3] = 0
        pixels[16 + 7] = -1
        val value = transparentPixelRatio(
            pixels = pixels,
            rowStride = 16,
            pixelStride = 4,
            width = 2,
            height = 2,
            step = 1,
        )
        assertEquals(0.5f, value, 0.0001f)
    }

    @Test
    fun truncatedBufferNeverReportsFewerTransparentPixelsThanKnownRows() {
        val value = transparentPixelRatio(
            pixels = ByteArray(16),
            rowStride = 16,
            pixelStride = 4,
            width = 4,
            height = 2,
            step = 1,
        )
        assertEquals(0.5f, value, 0.0001f)
    }

    @Test
    fun emptyFrameIsBlank() {
        assertEquals(1f, transparentPixelRatio(ByteArray(0), 0, 4, 0, 0, 1), 0.0001f)
    }

    @Test
    fun blankAndCompleteThresholdsLeaveResidualFramesIncomplete() {
        assertTrue(frameIsBlank(1f))
        assertTrue(frameIsBlank(FRAME_BLANK_ALPHA_RATIO))
        assertFalse(frameIsBlank(0.4f))
        assertTrue(frameIsComplete(0f))
        assertTrue(frameIsComplete(FRAME_INCOMPLETE_ALPHA_RATIO))
        assertFalse(frameIsComplete(0.4f))
        assertFalse(frameIsComplete(0.9f))
    }

    @Test
    fun waitForContentRightAfterLaunchWhenFrameIsBlankOrMissing() {
        assertEquals(
            ScreenshotWait.CONTENT,
            screenshotWait(nowMs = 10_000L, lastInputAtMs = 0L, lastLaunchAtMs = 9_000L, latestTransparentRatio = 1f),
        )
        assertEquals(
            ScreenshotWait.CONTENT,
            screenshotWait(nowMs = 10_000L, lastInputAtMs = 0L, lastLaunchAtMs = 9_000L, latestTransparentRatio = null),
        )
        assertEquals(
            ScreenshotWait.CONTENT,
            screenshotWait(nowMs = 10_000L, lastInputAtMs = 9_900L, lastLaunchAtMs = 9_000L, latestTransparentRatio = 0.4f),
        )
    }

    @Test
    fun launchedScreenWithCompleteFrameDoesNotWait() {
        assertEquals(
            ScreenshotWait.NONE,
            screenshotWait(nowMs = 10_000L, lastInputAtMs = 0L, lastLaunchAtMs = 9_000L, latestTransparentRatio = 0f),
        )
    }

    @Test
    fun waitForSettleRightAfterInput() {
        assertEquals(
            ScreenshotWait.SETTLE,
            screenshotWait(nowMs = 10_000L, lastInputAtMs = 9_500L, lastLaunchAtMs = 0L, latestTransparentRatio = 0f),
        )
    }

    @Test
    fun staleLaunchAndInputWindowsDoNotWait() {
        assertEquals(
            ScreenshotWait.NONE,
            screenshotWait(
                nowMs = 60_000L,
                lastInputAtMs = 10_000L,
                lastLaunchAtMs = 10_000L,
                latestTransparentRatio = 0f,
            ),
        )
        assertEquals(
            ScreenshotWait.NONE,
            screenshotWait(nowMs = 60_000L, lastInputAtMs = 0L, lastLaunchAtMs = 0L, latestTransparentRatio = null),
        )
    }

    @Test
    fun launchGraceOutranksInputGrace() {
        assertEquals(
            ScreenshotWait.CONTENT,
            screenshotWait(
                nowMs = 60_000L,
                lastInputAtMs = 59_000L,
                lastLaunchAtMs = 57_000L,
                latestTransparentRatio = 1f,
            ),
        )
    }

    @Test
    fun waitTimeoutsMatchTheirModes() {
        assertEquals(0L, ScreenshotWait.NONE.timeoutMs)
        assertEquals(SCREENSHOT_SETTLE_WAIT_MS, ScreenshotWait.SETTLE.timeoutMs)
        assertEquals(SCREENSHOT_CONTENT_WAIT_MS, ScreenshotWait.CONTENT.timeoutMs)
        assertTrue(ScreenshotWait.CONTENT.timeoutMs > ScreenshotWait.SETTLE.timeoutMs)
        assertTrue(SCREENSHOT_INPUT_GRACE_MS > SCREENSHOT_SETTLE_WAIT_MS)
        assertTrue(SCREENSHOT_LAUNCH_GRACE_MS > SCREENSHOT_CONTENT_WAIT_MS)
    }

    @Test
    fun failureMessagesTellTheAgentWhatToDo() {
        assertTrue(missingFrameMessage().contains("frame"))
        assertTrue(blankFrameMessage().contains("screen_launch"))
        assertTrue(blankFrameMessage().contains("content"))
    }

    private fun ratio(alpha: ByteArray): Float {
        val pixels = ByteArray(alpha.size * 4)
        alpha.forEachIndexed { index, value -> pixels[index * 4 + 3] = value }
        return transparentPixelRatio(
            pixels = pixels,
            rowStride = alpha.size * 4,
            pixelStride = 4,
            width = alpha.size,
            height = 1,
            step = 1,
        )
    }
}
