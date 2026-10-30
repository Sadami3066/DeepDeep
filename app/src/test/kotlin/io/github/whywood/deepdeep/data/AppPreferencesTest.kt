package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun normalizeClampsToRange() {
        assertEquals(CHAT_FONT_SCALE_MIN, normalizeChatFontScale(0.5f))
        assertEquals(CHAT_FONT_SCALE_MAX, normalizeChatFontScale(2f))
        assertEquals(CHAT_FONT_SCALE_MIN, normalizeChatFontScale(-1f))
    }

    @Test
    fun normalizeRoundsToFivePercentSteps() {
        assertEquals(0.85f, normalizeChatFontScale(0.873f))
        assertEquals(0.90f, normalizeChatFontScale(0.888f))
        assertEquals(1.00f, normalizeChatFontScale(1.02f))
        assertEquals(1.40f, normalizeChatFontScale(1.44f))
        assertEquals(0.95f, normalizeChatFontScale(0.974f))
    }

    @Test
    fun normalizeFallsBackForNonFiniteValues() {
        assertEquals(CHAT_FONT_SCALE_DEFAULT, normalizeChatFontScale(Float.NaN))
        assertEquals(CHAT_FONT_SCALE_DEFAULT, normalizeChatFontScale(Float.POSITIVE_INFINITY))
        assertEquals(CHAT_FONT_SCALE_DEFAULT, normalizeChatFontScale(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun normalizeKeepsValidValues() {
        assertEquals(1f, normalizeChatFontScale(1f))
        assertEquals(CHAT_FONT_SCALE_MIN, normalizeChatFontScale(CHAT_FONT_SCALE_MIN))
        assertEquals(CHAT_FONT_SCALE_MAX, normalizeChatFontScale(CHAT_FONT_SCALE_MAX))
    }
}
