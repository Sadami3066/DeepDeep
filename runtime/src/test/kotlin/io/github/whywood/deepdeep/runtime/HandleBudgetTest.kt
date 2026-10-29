package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HandleBudgetTest {
    private val now = 10_000_000L

    private fun entry(
        sessionId: String,
        idleMs: Long = HandleBudget.IDLE_TTL_MS + 1,
        busy: Boolean = false,
        reconfiguring: Boolean = false,
        keepAlive: Boolean = false,
        selected: Boolean = false,
    ): HandleBudget.Entry = HandleBudget.Entry(
        sessionId = sessionId,
        lastActiveAt = now - idleMs,
        busy = busy,
        reconfiguring = reconfiguring,
        keepAlive = keepAlive,
        selected = selected,
    )

    @Test
    fun keepsHandlesWithinBudget() {
        val entries = listOf(entry("s-1"), entry("s-2"), entry("s-3"), entry("s-4"), entry("s-5"), entry("s-6"))
        assertEquals(emptyList<String>(), HandleBudget.decideEvictions(entries, size = 6, now = now))
    }

    @Test
    fun evictsOldestIdleHandlesDownToBudget() {
        val entries = listOf(
            entry("s-1", idleMs = 1_000_000L),
            entry("s-2", idleMs = 900_000L),
            entry("s-3", idleMs = 800_000L),
            entry("s-4", idleMs = 700_000L),
            entry("s-5", idleMs = 500_000L),
            entry("s-6", idleMs = 400_000L),
            entry("s-7", idleMs = 300_000L),
            entry("s-8", idleMs = 200_000L),
            entry("s-9", idleMs = 100_000L),
        )
        assertEquals(listOf("s-1", "s-2", "s-3"), HandleBudget.decideEvictions(entries, size = 9, now = now))
    }

    @Test
    fun doesNotEvictHandlesThatAreNotIdleEnough() {
        val entries = listOf(
            entry("s-1", idleMs = HandleBudget.IDLE_TTL_MS),
            entry("s-2", idleMs = 1_000L),
            entry("s-3", idleMs = 2_000L),
            entry("s-4", idleMs = 3_000L),
            entry("s-5", idleMs = 4_000L),
            entry("s-6", idleMs = 5_000L),
            entry("s-7", idleMs = 6_000L),
            entry("s-8", idleMs = 7_000L),
        )
        assertEquals(emptyList<String>(), HandleBudget.decideEvictions(entries, size = 8, now = now))
    }

    @Test
    fun keepsBusyAndReconfiguringHandles() {
        val entries = listOf(
            entry("busy", busy = true),
            entry("reconfiguring", reconfiguring = true),
            entry("s-1"),
            entry("s-2"),
            entry("s-3"),
            entry("s-4"),
            entry("s-5"),
        )
        assertEquals(listOf("s-1"), HandleBudget.decideEvictions(entries, size = 7, now = now))
    }

    @Test
    fun keepsReferencedAndRecentlyOpenedHandles() {
        val entries = listOf(
            entry("keep-alive", keepAlive = true),
            entry("selected", selected = true),
            entry("s-1"),
            entry("s-2"),
            entry("s-3"),
            entry("s-4"),
            entry("s-5"),
        )
        assertEquals(listOf("s-1"), HandleBudget.decideEvictions(entries, size = 7, now = now))
    }

    @Test
    fun respectsCustomBudgetAndTtl() {
        val entries = listOf(
            entry("s-1", idleMs = 5_000L),
            entry("s-2", idleMs = 6_000L),
            entry("s-3", idleMs = 7_000L),
        )
        assertEquals(
            listOf("s-3", "s-2"),
            HandleBudget.decideEvictions(entries, size = 3, now = now, max = 1, ttlMs = 4_000L),
        )
    }

    @Test
    fun resolvesReconnectTargetFromCatalogSummaries() {
        val sessions = listOf(
            ConversationSummary(
                id = "session-1",
                title = "One",
                updatedAt = 1L,
                filePath = "/tmp/one.jsonl",
                cwd = "/tmp/one",
            ),
            ConversationSummary(
                id = "session-2",
                title = "Two",
                updatedAt = 2L,
                filePath = "/tmp/two.jsonl",
                cwd = "/tmp/two",
            ),
        )
        val target = HandleBudget.reconnectTarget(sessions, "session-2")
        assertNotNull(target)
        assertEquals("/tmp/two.jsonl", target?.filePath)
        assertEquals("/tmp/two", target?.cwd)
        assertNull(HandleBudget.reconnectTarget(sessions, "missing"))
    }
}
