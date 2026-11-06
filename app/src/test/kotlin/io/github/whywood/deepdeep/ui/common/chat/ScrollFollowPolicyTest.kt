package io.github.whywood.deepdeep.ui.common.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollFollowPolicyTest {
    private fun action(
        overflowPx: Float,
        contentBottomDeltaPx: Float,
        sinceGrowthMs: Long?,
    ): FollowAction = ScrollFollowPolicy.decideFollowAction(
        overflowPx = overflowPx,
        contentBottomDeltaPx = contentBottomDeltaPx,
        sinceGrowthMs = sinceGrowthMs,
        deadbandPx = ScrollFollowPolicy.RetractDeadbandDp,
        debounceMs = ScrollFollowPolicy.RetractDebounceMs,
    )

    @Test
    fun nearBottomUsesThreshold() {
        assertTrue(ScrollFollowPolicy.nearBottom(distancePx = 0f, thresholdPx = 80f))
        assertTrue(ScrollFollowPolicy.nearBottom(distancePx = 80f, thresholdPx = 80f))
        assertFalse(ScrollFollowPolicy.nearBottom(distancePx = 80.5f, thresholdPx = 80f))
    }

    @Test
    fun revealDeltaCapsOverflow() {
        assertEquals(0f, ScrollFollowPolicy.revealDeltaPx(-12f))
        assertEquals(96f, ScrollFollowPolicy.revealDeltaPx(96f))
        assertEquals(240f, ScrollFollowPolicy.revealDeltaPx(600f))
    }

    @Test
    fun noOverflowDoesNothing() {
        assertEquals(FollowAction.None, action(overflowPx = 0f, contentBottomDeltaPx = 12f, sinceGrowthMs = 10L))
    }

    @Test
    fun growthReveals() {
        assertEquals(FollowAction.Reveal, action(overflowPx = 12f, contentBottomDeltaPx = 30f, sinceGrowthMs = 100L))
    }

    @Test
    fun smallRecentRetractionIsIgnored() {
        assertEquals(FollowAction.Ignore, action(overflowPx = 12f, contentBottomDeltaPx = -8f, sinceGrowthMs = 100L))
    }

    @Test
    fun largerRetractionReveals() {
        assertEquals(FollowAction.Reveal, action(overflowPx = 12f, contentBottomDeltaPx = -40f, sinceGrowthMs = 100L))
    }

    @Test
    fun quietRetractionReveals() {
        assertEquals(FollowAction.Reveal, action(overflowPx = 12f, contentBottomDeltaPx = -8f, sinceGrowthMs = 900L))
    }

    @Test
    fun unknownGrowthTimeReveals() {
        assertEquals(FollowAction.Reveal, action(overflowPx = 12f, contentBottomDeltaPx = -8f, sinceGrowthMs = null))
    }

    @Test
    fun movementWindowHoldsForDuration() {
        var now = 1_000L
        val window = MovementWindow(holdMs = 300L) { now }
        assertFalse(window.active())
        window.mark()
        assertTrue(window.active())
        now += 299L
        assertTrue(window.active())
        now += 2L
        assertFalse(window.active())
    }
}
