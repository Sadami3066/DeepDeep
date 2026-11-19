package io.github.whywood.superpi.design

import org.junit.Assert.assertEquals
import org.junit.Test

class DdFontStyleTest {
    @Test
    fun missingPreferenceFallsBackToFredoka() {
        assertEquals(DdFontStyle.FREDOKA, DdFontStyle.fromId(null))
        assertEquals(DdFontStyle.FREDOKA, DdFontStyle.fromId(""))
        assertEquals(DdFontStyle.FREDOKA, DdFontStyle.fromId("unknown"))
    }

    @Test
    fun savedStylesRoundTrip() {
        DdFontStyle.entries.forEach { style ->
            assertEquals(style, DdFontStyle.fromId(style.id))
        }
    }
}
