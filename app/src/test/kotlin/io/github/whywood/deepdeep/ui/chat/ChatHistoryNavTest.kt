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
    fun shortHistoryShowsEveryUserMessage() {
        assertEquals(listOf(1, 4, 9), navWindowIndices(listOf(1, 4, 9), 4, ChatHistoryNavMaxTicks))
        assertTrue(navWindowIndices(emptyList(), 0, ChatHistoryNavMaxTicks).isEmpty())
        assertTrue(navWindowIndices((0 until 10).toList(), 3, 0).isEmpty())
    }

    @Test
    fun windowCentersOnAnchorAndClampsToEdges() {
        val userIndices = (0 until 20).map { it * 2 }
        assertEquals(userIndices.take(ChatHistoryNavMaxTicks), navWindowIndices(userIndices, -5, ChatHistoryNavMaxTicks))
        assertEquals(userIndices.take(ChatHistoryNavMaxTicks), navWindowIndices(userIndices, 6, ChatHistoryNavMaxTicks))
        assertEquals(userIndices.subList(4, 10), navWindowIndices(userIndices, 14, ChatHistoryNavMaxTicks))
        assertEquals(userIndices.subList(7, 13), navWindowIndices(userIndices, 20, ChatHistoryNavMaxTicks))
        assertEquals(userIndices.takeLast(ChatHistoryNavMaxTicks), navWindowIndices(userIndices, 100, ChatHistoryNavMaxTicks))
    }

    @Test
    fun windowSlidesByOneTickAsAnchorAdvances() {
        val userIndices = (0 until 100).toList()
        var previous = navWindowIndices(userIndices, -1, ChatHistoryNavMaxTicks)
        for (anchor in userIndices) {
            val window = navWindowIndices(userIndices, anchor, ChatHistoryNavMaxTicks)
            assertEquals(ChatHistoryNavMaxTicks, window.size)
            assertTrue(window.first() >= previous.first())
            assertTrue(window.first() - previous.first() <= 1)
            previous = window
        }
    }

    @Test
    fun activeTickFollowsWindowAnchor() {
        val window = navWindowIndices((0 until 20).toList(), 10, ChatHistoryNavMaxTicks)
        assertEquals(listOf(7, 8, 9, 10, 11, 12), window)
        assertEquals(3, activeNavSegmentIndex(window, 10))
        assertEquals(0, activeNavSegmentIndex(window, 0))
        assertEquals(5, activeNavSegmentIndex(window, 19))
    }

    @Test
    fun itemIndexAccountsForLoadEarlierPill() {
        assertEquals(3, messageItemIndex(99, 100, 3))
        assertEquals(11, messageItemIndex(50, 100, 60))
        assertEquals(99, messageItemIndex(99, 100, 100))
        assertEquals(0, messageItemIndex(0, 100, 100))
    }

    @Test
    fun itemIndexMapsBackToMessageIndex() {
        assertEquals(99, messageIndexForItemIndex(3, 100, 3))
        assertEquals(50, messageIndexForItemIndex(11, 100, 60))
        assertEquals(0, messageIndexForItemIndex(0, 100, 100))
        assertEquals(99, messageIndexForItemIndex(100, 100, 100))
        assertEquals(0, messageIndexForItemIndex(0, 0, 0))
    }

    @Test
    fun activeSegmentFollowsScrollPosition() {
        val segments = listOf(0, 10, 20, 30)
        assertEquals(0, activeNavSegmentIndex(segments, 0))
        assertEquals(0, activeNavSegmentIndex(segments, 9))
        assertEquals(1, activeNavSegmentIndex(segments, 10))
        assertEquals(2, activeNavSegmentIndex(segments, 25))
        assertEquals(3, activeNavSegmentIndex(segments, 99))
        assertEquals(-1, activeNavSegmentIndex(emptyList(), 5))
    }
}
