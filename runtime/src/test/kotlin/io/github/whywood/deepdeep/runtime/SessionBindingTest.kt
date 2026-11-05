package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBindingTest {
    @Test
    fun bindKeepsCurrentSession() {
        val binding = SessionBinding()
        assertNull(binding.current())

        binding.bind(BoundSession("session-1", "/tmp/session-1.jsonl", "/workspace"))
        assertEquals("session-1", binding.current()?.sessionId)
        assertEquals("/tmp/session-1.jsonl", binding.current()?.sessionFile)
        assertEquals("/workspace", binding.current()?.cwd)
        assertTrue(binding.supportsChatDelivery)

        val disabled = SessionBinding(supportsChatDelivery = false)
        assertFalse(disabled.supportsChatDelivery)
        assertNull(disabled.current())
    }

    @Test
    fun liveProviderOverridesBoundSession() {
        val binding = SessionBinding(supportsChatDelivery = false) {
            BoundSession("live-session", null, "/live")
        }
        binding.bind(BoundSession("snapshot-session", "/tmp/snapshot.jsonl", "/snapshot"))

        assertEquals("live-session", binding.current()?.sessionId)
        assertEquals("/live", binding.current()?.cwd)
        assertFalse(binding.supportsChatDelivery)
    }

    @Test
    fun liveProviderFallsBackToBoundSessionWhenItReturnsNull() {
        var live: BoundSession? = null
        var reads = 0
        val binding = SessionBinding { reads += 1; live }
        binding.bind(BoundSession("snapshot-session", null, "/snapshot"))

        assertEquals("snapshot-session", binding.current()?.sessionId)
        assertEquals(1, reads)

        live = BoundSession("live-session", "/tmp/live.jsonl", "/live")
        val current = binding.current()
        assertEquals("live-session", current?.sessionId)
        assertEquals("/tmp/live.jsonl", current?.sessionFile)
        assertEquals(2, reads)
    }
}
