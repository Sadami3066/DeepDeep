package io.github.whywood.deepdeep.vscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitScreenTest {
    @Test
    fun keepsPortraitMetrics() {
        val size = portraitScreenSize(1080, 2400)

        assertEquals(1080, size.width)
        assertEquals(2400, size.height)
    }

    @Test
    fun turnsLandscapeMetricsIntoPortrait() {
        val size = portraitScreenSize(2400, 1080)

        assertEquals(1080, size.width)
        assertEquals(2400, size.height)
    }

    @Test
    fun keepsSquareMetrics() {
        val size = portraitScreenSize(1440, 1440)

        assertEquals(1440, size.width)
        assertEquals(1440, size.height)
    }
}
