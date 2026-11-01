package io.github.whywood.deepdeep.data

import kotlin.math.roundToInt

internal data class ScaledSize(val width: Int, val height: Int)

internal object ImageScaling {
    const val MAX_EDGE = 2000
    const val MAX_DECODE_PIXELS = 4L * 1024L * 1024L

    fun sampleSizeFor(width: Int, height: Int): Int {
        val pixels = width.toLong() * height.toLong()
        var sample = 1
        while (pixels / (sample.toLong() * sample) > MAX_DECODE_PIXELS) sample *= 2
        return sample
    }

    fun scaledSizeFor(width: Int, height: Int): ScaledSize {
        val longEdge = maxOf(width, height)
        if (longEdge <= MAX_EDGE) return ScaledSize(width, height)
        val scale = MAX_EDGE.toDouble() / longEdge
        return ScaledSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
