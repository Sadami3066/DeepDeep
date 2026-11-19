package io.github.whywood.superpi.floating

import io.github.whywood.superpi.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBubbleMoodArtTest {
    @Test
    fun everyMoodMapsToItsArtwork() {
        assertEquals(R.drawable.v2_wave, FloatingBubbleMood.IDLE.drawable())
        assertEquals(R.drawable.v2_thinking, FloatingBubbleMood.THINKING.drawable())
        assertEquals(R.drawable.v2_waiting, FloatingBubbleMood.SLEEPY.drawable())
        assertEquals(R.drawable.v2_confused, FloatingBubbleMood.WAITING.drawable())
        assertEquals(R.drawable.v2_done, FloatingBubbleMood.DONE.drawable())
        assertEquals(R.drawable.v2_alert, FloatingBubbleMood.ALERT.drawable())
    }
}
