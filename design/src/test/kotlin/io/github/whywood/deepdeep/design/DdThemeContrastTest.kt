package io.github.whywood.deepdeep.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DdThemeContrastTest {
    @Test
    fun lightPiKeepsTextReadable() {
        val colors = DdTheme.lightPi()
        assertContrast(colors.ink, colors.paper, 7f)
        assertContrast(colors.inkSecondary, colors.paper, 4.5f)
        assertContrast(colors.inkTertiary, colors.paper, 4.5f)
        assertContrast(colors.onAccent, colors.accent, 4.5f)
        assertContrast(colors.danger, colors.paper, 4.5f)
        assertContrast(colors.success, colors.paper, 4.5f)
        assertContrast(colors.warning, colors.paper, 4.5f)
    }

    @Test
    fun darkPiKeepsTextReadable() {
        val colors = DdTheme.darkPi()
        assertContrast(colors.ink, colors.paper, 7f)
        assertContrast(colors.inkSecondary, colors.paper, 4.5f)
        assertContrast(colors.inkTertiary, colors.paper, 4.5f)
        assertContrast(colors.onAccent, colors.accent, 4.5f)
        assertContrast(colors.danger, colors.paper, 4.5f)
        assertContrast(colors.success, colors.paper, 4.5f)
        assertContrast(colors.warning, colors.paper, 4.5f)
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float) {
        val ratio = ratio(foreground, background)
        assertTrue("contrast $ratio is below $minimum", ratio >= minimum)
    }

    private fun ratio(a: Color, b: Color): Float {
        val first = luminance(a)
        val second = luminance(b)
        val high = maxOf(first, second)
        val low = minOf(first, second)
        return (high + 0.05f) / (low + 0.05f)
    }

    private fun luminance(color: Color): Float =
        0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)

    private fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
}
