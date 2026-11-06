package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.conversation.ConversationState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationWriteTargetTest {
    private var activeState = ConversationState()

    private fun target(
        maxCachedSessions: Int = 2,
        onEvict: (String) -> Unit = {},
    ): ConversationWriteTarget = ConversationWriteTarget(
        maxCachedSessions = maxCachedSessions,
        activeReader = { activeState },
        activeWriter = { activeState = it },
        onEvict = onEvict,
    )

    private fun message(id: Long, text: String): AssistantMessage =
        AssistantMessage(id = id, role = AssistantRole.ASSISTANT, text = text)

    @Test
    fun activeAndBackgroundWritesReachTheSameReadApi() {
        val target = target()
        target.setActive("session-a")
        target.update("session-a") { it.copy(title = "active", messages = it.messages + message(1, "a1")) }
        target.update("session-b") { it.copy(title = "background", messages = it.messages + message(2, "b1")) }

        assertEquals("active", target.read("session-a").title)
        assertEquals("background", target.read("session-b").title)
        assertEquals(target.read("session-b"), target.readCached("session-b"))
        assertEquals(2L, target.readCached("session-b")?.messages?.single()?.id)
    }

    @Test
    fun switchingActiveSessionKeepsPreviousSessionReadable() {
        val target = target()
        target.setActive("session-a")
        target.update("session-a") { it.copy(title = "cold") }
        target.store("session-a", target.read("session-a"))
        target.update("session-b") { it.copy(title = "warm") }
        target.store("session-b", target.read("session-b"))

        activeState = target.readCached("session-b")!!
        target.setActive("session-b")

        assertEquals("warm", target.read("session-b").title)
        assertEquals("cold", target.read("session-a").title)
        assertFalse(target.isActive("session-a"))
    }

    @Test
    fun pinnedSessionSurvivesLruEviction() {
        val evicted = mutableListOf<String>()
        val target = target(maxCachedSessions = 2, onEvict = { evicted += it })
        target.store("session-a", ConversationState(title = "pinned"))
        target.pin("session-a")
        target.store("session-b", ConversationState(title = "b"))
        target.store("session-c", ConversationState(title = "c"))

        assertNotNull(target.readCached("session-a"))
        assertNull(target.readCached("session-b"))
        assertNotNull(target.readCached("session-c"))
        assertEquals(listOf("session-b"), evicted)
    }

    @Test
    fun activeSessionIsNeverEvicted() {
        val target = target(maxCachedSessions = 1)
        target.setActive("session-a")
        target.store("session-a", ConversationState(title = "active"))
        target.store("session-b", ConversationState(title = "background"))

        assertNotNull(target.readCached("session-a"))
        assertNull(target.readCached("session-b"))
    }

    @Test
    fun concurrentUpdatesDoNotLoseWrites() = runBlocking {
        val target = target(maxCachedSessions = 64)
        val ids = AtomicInteger()
        coroutineScope {
            repeat(50) {
                launch(Dispatchers.Default) {
                    val id = ids.incrementAndGet().toLong()
                    target.update("session-bg") { state ->
                        state.copy(messages = state.messages + message(id, "delta-$id"))
                    }
                }
            }
        }

        val stored = target.read("session-bg")
        assertEquals(50, stored.messages.size)
        assertEquals(50, stored.messages.map { it.id }.toSet().size)
        assertNotNull(target.readCached("session-bg"))
    }

    @Test
    fun readMissingSessionReturnsDefaults() {
        val target = target()

        assertEquals(ConversationState(), target.read("never-cached"))
        assertTrue(target.read("never-cached").messages.isEmpty())
        assertFalse(target.read("never-cached").loaded)
    }
}
