package io.github.whywood.deepdeep.vscreen

internal const val FRAME_BLANK_ALPHA_RATIO = 0.98f
internal const val FRAME_INCOMPLETE_ALPHA_RATIO = 0.15f
internal const val SCREENSHOT_INPUT_GRACE_MS = 3_000L
internal const val SCREENSHOT_LAUNCH_GRACE_MS = 6_000L
internal const val SCREENSHOT_SETTLE_WAIT_MS = 800L
internal const val SCREENSHOT_CONTENT_WAIT_MS = 3_500L
internal const val SCREENSHOT_POLL_MS = 20L
internal const val FRAME_ALPHA_SAMPLE_STEP = 4

internal fun transparentPixelRatio(
    pixels: ByteArray,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    step: Int = FRAME_ALPHA_SAMPLE_STEP,
): Float {
    if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride < 4) return 1f
    val stride = step.coerceAtLeast(1)
    var transparent = 0
    var total = 0
    var y = 0
    while (y < height) {
        val rowStart = y * rowStride
        var x = 0
        while (x < width) {
            val offset = rowStart + x * pixelStride + 3
            if (offset < pixels.size && pixels[offset].toInt() == 0) transparent += 1
            total += 1
            x += stride
        }
        y += stride
    }
    return if (total == 0) 1f else transparent.toFloat() / total
}

internal fun frameIsBlank(transparentRatio: Float): Boolean = transparentRatio >= FRAME_BLANK_ALPHA_RATIO

internal fun frameIsComplete(transparentRatio: Float): Boolean = transparentRatio <= FRAME_INCOMPLETE_ALPHA_RATIO

internal enum class ScreenshotWait {
    NONE,
    SETTLE,
    CONTENT,
}

internal fun screenshotWait(
    nowMs: Long,
    lastInputAtMs: Long,
    lastLaunchAtMs: Long,
    latestTransparentRatio: Float?,
): ScreenshotWait = when {
    (latestTransparentRatio == null || !frameIsComplete(latestTransparentRatio)) &&
        withinWindow(nowMs, lastLaunchAtMs, SCREENSHOT_LAUNCH_GRACE_MS) -> ScreenshotWait.CONTENT

    withinWindow(nowMs, lastInputAtMs, SCREENSHOT_INPUT_GRACE_MS) -> ScreenshotWait.SETTLE
    else -> ScreenshotWait.NONE
}

internal val ScreenshotWait.timeoutMs: Long
    get() = when (this) {
        ScreenshotWait.NONE -> 0L
        ScreenshotWait.SETTLE -> SCREENSHOT_SETTLE_WAIT_MS
        ScreenshotWait.CONTENT -> SCREENSHOT_CONTENT_WAIT_MS
    }

internal fun missingFrameMessage(): String =
    "The virtual screen has not produced a frame yet; wait a moment and retry"

internal fun blankFrameMessage(): String =
    "The virtual screen has not rendered any content yet; open the target app on this screen with screen_launch and retry"

private fun withinWindow(nowMs: Long, sinceMs: Long, windowMs: Long): Boolean {
    val elapsed = nowMs - sinceMs
    return elapsed in 0..windowMs
}
