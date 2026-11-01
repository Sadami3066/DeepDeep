package io.github.whywood.deepdeep.ui.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTopBarMetricsTest {
    @Test
    fun wideScreenKeepsMaxWordmark() {
        val size = wordmarkFontSize(
            maxWidth = 800.dp,
            leadingWidth = 0.dp,
            trailingWidth = 0.dp,
            baseWidth = 110.dp,
            compact = false,
        )
        assertEquals(27f, size, 0.01f)
    }

    @Test
    fun bothSideGroupsShrinkWordmarkOnlyToFit() {
        val size = wordmarkFontSize(
            maxWidth = 360.dp,
            leadingWidth = 120.dp,
            trailingWidth = 100.dp,
            baseWidth = 110.dp,
            compact = false,
        )
        val expected = 27f * 100f / 110f
        assertEquals(expected, size, 0.01f)
        assertTrue(size < 27f)
        assertTrue(size >= 18f)
    }

    @Test
    fun oversizedGroupsKeepMinimumInsteadOfOverlapping() {
        val size = wordmarkFontSize(
            maxWidth = 360.dp,
            leadingWidth = 200.dp,
            trailingWidth = 200.dp,
            baseWidth = 110.dp,
            compact = false,
        )
        assertEquals(18f, size, 0.01f)
    }

    @Test
    fun compactLayoutUsesMinimumWordmark() {
        val size = wordmarkFontSize(
            maxWidth = 360.dp,
            leadingWidth = 0.dp,
            trailingWidth = 0.dp,
            baseWidth = 110.dp,
            compact = true,
        )
        assertEquals(18f, size, 0.01f)
    }

    @Test
    fun unmeasuredBaseWidthFallsBackToMaximum() {
        val size = wordmarkFontSize(
            maxWidth = 360.dp,
            leadingWidth = 120.dp,
            trailingWidth = 100.dp,
            baseWidth = 0.dp,
            compact = false,
        )
        assertEquals(27f, size, 0.01f)
    }
}
