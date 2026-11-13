package io.github.whywood.deepdeep.design

import kotlin.math.roundToInt

data class DdScaledSize(val width: Int, val height: Int)

object DdImageScaling {
    const val MAX_EDGE = 2000
    const val MAX_DECODE_PIXELS = 4L * 1024L * 1024L

    fun sampleSizeFor(width: Int, height: Int, maxDecodePixels: Long = MAX_DECODE_PIXELS): Int {
        val pixels = width.toLong() * height.toLong()
        var sample = 1
        while (pixels / (sample.toLong() * sample) > maxDecodePixels) sample *= 2
        return sample
    }

    fun scaledSizeFor(width: Int, height: Int, maxEdge: Int = MAX_EDGE): DdScaledSize {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return DdScaledSize(width, height)
        val scale = maxEdge.toDouble() / longEdge
        return DdScaledSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
