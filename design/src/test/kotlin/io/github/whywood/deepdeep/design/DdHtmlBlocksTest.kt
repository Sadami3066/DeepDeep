package io.github.whywood.deepdeep.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdHtmlBlocksTest {
    @Test
    fun plainHtmlStaysSingleTextSegment() {
        val segments = htmlSegments("<p>hello <b>world</b></p>")
        assertEquals(1, segments.size)
        assertTrue(segments.single() is DdHtmlSegment.Text)
    }

    @Test
    fun imageSplitsBlocksAndDecodesSource() {
        val segments = htmlSegments("<p>a</p><img src=\"https://x.test/a.png?k=1&amp;v=2\"><p>b</p>")
        assertEquals(3, segments.size)
        assertEquals("<p>a</p>", (segments[0] as DdHtmlSegment.Text).html)
        assertEquals("https://x.test/a.png?k=1&v=2", (segments[1] as DdHtmlSegment.Image).source)
        assertEquals("<p>b</p>", (segments[2] as DdHtmlSegment.Text).html)
    }

    @Test
    fun imageWithoutSourceIsDropped() {
        val segments = htmlSegments("<p>a</p><img alt=\"x\"><p>b</p>")
        assertTrue(segments.none { it is DdHtmlSegment.Image })
    }

    @Test
    fun blankHtmlHasNoSegments() {
        assertTrue(htmlSegments("   ").isEmpty())
        assertTrue(htmlSegments("").isEmpty())
    }
}
