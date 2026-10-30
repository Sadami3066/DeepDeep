package io.github.whywood.deepdeep.ui.chat.state

import android.content.ComponentCallbacks2
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ConversationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCacheTest {
    private var activeState = ConversationState()

    private fun target(busy: Set<String> = emptySet()): ConversationWriteTarget =
        ConversationWriteTarget(
            activeReader = { activeState },
            activeWriter = { activeState = it },
            protectedSessionIds = { busy + listOfNotNull(activeSession) },
            onEvict = { evicted += it },
        ).apply { setActive(activeSession) }

    private var activeSession: String? = "session-active"
    private val evicted = mutableListOf<String>()

    @Test
    fun rememberCurrentStoresLoadedSnapshotAndFlushes() {
        val cache = ConversationCache(target())
        cache.store("session-1", ConversationState(loaded = true))
        var flushed = false
        val state = ChatUiState(
            conversationId = "session-1",
            historyLoaded = true,
            input = "hello",
            messages = listOf(AssistantMessage(0, AssistantRole.USER, "hi")),
        )

        val id = cache.rememberCurrent(state) { flushed = true }

        assertEquals("session-1", id)
        assertTrue(flushed)
        val stored = cache.readCached("session-1")
        assertNotNull(stored)
        assertTrue(stored!!.loaded)
        assertEquals(listOf("hi"), stored.messages.map { it.text })
    }

    @Test
    fun rememberCurrentSkipsWhenNotLoadedOrUnbound() {
        val cache = ConversationCache(target())
        var flushed = false

        assertNull(cache.rememberCurrent(ChatUiState(conversationId = "session-1")) { flushed = true })
        assertNull(cache.rememberCurrent(ChatUiState(historyLoaded = true)) { flushed = true })
        assertFalse(flushed)
        assertNull(cache.readCached("session-1"))
    }

    @Test
    fun trimMemoryClearsIdleButKeepsPinnedAndActive() {
        val cache = ConversationCache(target(busy = setOf("session-busy")))
        cache.store("session-active", ConversationState(title = "active"))
        cache.store("session-busy", ConversationState(title = "busy"))
        cache.store("session-idle", ConversationState(title = "idle"))

        cache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertNotNull(cache.readCached("session-active"))
        assertNotNull(cache.readCached("session-busy"))
        assertNull(cache.readCached("session-idle"))
        assertEquals(setOf("session-idle"), evicted.toSet())
    }

    @Test
    fun evictAndRetainOnlyDropCachedSessions() {
        val cache = ConversationCache(target())
        cache.store("session-1", ConversationState())
        cache.store("session-2", ConversationState())
        cache.store("session-3", ConversationState())

        cache.evict("session-1")
        cache.retainOnly(setOf("session-2"))

        assertNull(cache.readCached("session-1"))
        assertNotNull(cache.readCached("session-2"))
        assertNull(cache.readCached("session-3"))
    }

}
