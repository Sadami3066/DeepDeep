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
    fun anchorSlotTracksMessagePosition() {
        val userIndices = listOf(1, 4, 9, 14)
        assertEquals(0, navAnchorSlot(userIndices, -5))
        assertEquals(0, navAnchorSlot(userIndices, 3))
        assertEquals(1, navAnchorSlot(userIndices, 4))
        assertEquals(2, navAnchorSlot(userIndices, 10))
        assertEquals(3, navAnchorSlot(userIndices, 99))
        assertEquals(0, navAnchorSlot(emptyList(), 5))
    }

    @Test
    fun followTopScrollsOneTickOnlyAtWindowEdges() {
        assertEquals(0, navFollowTopSlot(2, 0, 20, ChatHistoryNavWindowTicks))
        assertEquals(0, navFollowTopSlot(4, 0, 20, ChatHistoryNavWindowTicks))
        assertEquals(1, navFollowTopSlot(5, 0, 20, ChatHistoryNavWindowTicks))
        assertEquals(9, navFollowTopSlot(10, 9, 20, ChatHistoryNavWindowTicks))
        assertEquals(10, navFollowTopSlot(13, 10, 20, ChatHistoryNavWindowTicks))
        assertEquals(9, navFollowTopSlot(9, 10, 20, ChatHistoryNavWindowTicks))
        assertEquals(15, navFollowTopSlot(19, 11, 20, ChatHistoryNavWindowTicks))
        assertEquals(15, navFollowTopSlot(50, 3, 20, ChatHistoryNavWindowTicks))
        assertEquals(0, navFollowTopSlot(3, 0, 2, ChatHistoryNavWindowTicks))
    }

    @Test
    fun followTopSlidesOneTickAsActiveAdvances() {
        var top = 0
        for (slot in 1 until 100) {
            val next = navFollowTopSlot(slot, top, 100, ChatHistoryNavWindowTicks)
            assertTrue(next >= top)
            assertTrue(next - top <= 1)
            top = next
        }
        assertEquals(95, top)
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

}
