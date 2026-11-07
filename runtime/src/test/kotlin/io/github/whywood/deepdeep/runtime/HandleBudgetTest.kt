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
    fun evictsSingleIdleHandleWithoutCountThreshold() {
        assertEquals(listOf("only"), HandleBudget.decideEvictions(listOf(entry("only")), now = now))
    }

    @Test
    fun evictsAllIdleHandlesOldestFirst() {
        val entries = listOf(
            entry("s-3", idleMs = HandleBudget.IDLE_TTL_MS + 1_000L),
            entry("s-1", idleMs = HandleBudget.IDLE_TTL_MS + 3_000L),
            entry("s-2", idleMs = HandleBudget.IDLE_TTL_MS + 2_000L),
        )
        assertEquals(listOf("s-1", "s-2", "s-3"), HandleBudget.decideEvictions(entries, now = now))
    }

    @Test
    fun doesNotEvictRecentlyActiveHandles() {
        val entries = listOf(
            entry("s-1", idleMs = 0L),
            entry("s-2", idleMs = 1_000L),
            entry("s-3", idleMs = 2_000L),
        )
        assertEquals(emptyList<String>(), HandleBudget.decideEvictions(entries, now = now))
    }

    @Test
    fun doesNotEvictHandleExactlyAtTtl() {
        assertEquals(emptyList<String>(), HandleBudget.decideEvictions(listOf(entry("s-1", idleMs = HandleBudget.IDLE_TTL_MS)), now = now))
    }

    @Test
    fun keepsBusyReconfiguringReferencedAndSelectedHandles() {
        val entries = listOf(
            entry("busy", busy = true),
            entry("reconfiguring", reconfiguring = true),
            entry("keep-alive", keepAlive = true),
            entry("selected", selected = true),
            entry("s-1", idleMs = HandleBudget.IDLE_TTL_MS + 1_000L),
            entry("s-2", idleMs = HandleBudget.IDLE_TTL_MS + 2_000L),
        )
        assertEquals(listOf("s-2", "s-1"), HandleBudget.decideEvictions(entries, now = now))
    }

    @Test
    fun respectsCustomTtl() {
        val entries = listOf(
            entry("s-1", idleMs = 5_000L),
            entry("s-2", idleMs = 6_000L),
            entry("s-3", idleMs = 7_000L),
        )
        assertEquals(
            listOf("s-3", "s-2", "s-1"),
            HandleBudget.decideEvictions(entries, now = now, ttlMs = 4_000L),
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
