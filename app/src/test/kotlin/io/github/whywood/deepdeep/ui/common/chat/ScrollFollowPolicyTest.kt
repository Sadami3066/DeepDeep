package io.github.whywood.deepdeep.ui.common.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollFollowPolicyTest {
    @Test
    fun nearBottomUsesThreshold() {
        assertTrue(ScrollFollowPolicy.nearBottom(distancePx = 0f, thresholdPx = 80f))
        assertTrue(ScrollFollowPolicy.nearBottom(distancePx = 80f, thresholdPx = 80f))
        assertFalse(ScrollFollowPolicy.nearBottom(distancePx = 80.5f, thresholdPx = 80f))
    }
}
