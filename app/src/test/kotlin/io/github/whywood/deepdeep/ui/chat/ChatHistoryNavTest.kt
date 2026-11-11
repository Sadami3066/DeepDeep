package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryNavTest {
    @Test
    fun shortHistoryShowsEveryMessage() {
        assertEquals(0, initialVisibleMessages(0))
        assertEquals(8, initialVisibleMessages(8))
        assertEquals(20, initialVisibleMessages(20))
    }

    @Test
    fun longHistoryStartsCollapsed() {
        assertEquals(3, initialVisibleMessages(21))
        assertEquals(3, initialVisibleMessages(200))
    }

    @Test
    fun firstResolutionUsesInitialWindow() {
        assertEquals(3, resolveVisibleMessages(0, 0, 100))
        assertEquals(8, resolveVisibleMessages(0, 0, 8))
    }

    @Test
    fun loadEarlierAddsBatch() {
        assertEquals(23, expandVisibleMessages(3, 200))
        assertEquals(43, expandVisibleMessages(23, 200))
        assertEquals(30, expandVisibleMessages(25, 30))
        assertEquals(0, expandVisibleMessages(0, 0))
    }

    @Test
    fun growthKeepsCollapsedWindow() {
        assertEquals(3, resolveVisibleMessages(3, 100, 101))
    }

    @Test
    fun growthPreservesLoadedHistory() {
        assertEquals(24, resolveVisibleMessages(23, 100, 101))
    }

    @Test
    fun historyShrinkClampsToTotal() {
        assertEquals(5, resolveVisibleMessages(23, 100, 5))
    }

    @Test
    fun userSegmentsStayUnsampledWhenFew() {
        assertEquals(listOf(1, 4, 9), navSegmentIndices(listOf(1, 4, 9), 12))
        assertTrue(navSegmentIndices(emptyList(), 12).isEmpty())
    }

    @Test
    fun manyUserSegmentsAreSampledEvenly() {
        val indices = navSegmentIndices((0 until 100).toList(), 12)
        assertEquals(12, indices.size)
        assertEquals(0, indices.first())
        assertEquals(99, indices.last())
        assertTrue(indices.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun singleMaxSegmentKeepsLatestUserMessage() {
        assertEquals(listOf(99), navSegmentIndices((0 until 100).toList(), 1))
    }

    @Test
    fun fractionMapsToNearestSegment() {
        assertEquals(0, navSegmentForFraction(0f, 12))
        assertEquals(11, navSegmentForFraction(1f, 12))
        assertEquals(0, navSegmentForFraction(-0.5f, 4))
        assertEquals(3, navSegmentForFraction(1.5f, 4))
        assertEquals(2, navSegmentForFraction(0.5f, 4))
    }

    @Test
    fun itemIndexAccountsForLoadEarlierPill() {
        assertEquals(3, messageItemIndex(99, 100, 3))
        assertEquals(11, messageItemIndex(50, 100, 60))
        assertEquals(99, messageItemIndex(99, 100, 100))
        assertEquals(0, messageItemIndex(0, 100, 100))
    }

    @Test
    fun segmentCentersSpanTrackInsetByPadding() {
        assertEquals(6f, navSegmentCenter(0, 12, 400f, 6f), 0.001f)
        assertEquals(394f, navSegmentCenter(11, 12, 400f, 6f), 0.001f)
        assertEquals(200f, navSegmentCenter(0, 1, 400f, 6f), 0.001f)
    }
}
