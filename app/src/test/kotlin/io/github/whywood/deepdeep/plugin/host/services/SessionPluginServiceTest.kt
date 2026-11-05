package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPluginServiceTest {
    @Test
    fun activeReflectsBusyState() {
        val runtime = FakeConversationQueries()
        val active = MutableStateFlow<String?>("session-1")
        val service = SessionPluginService(runtime, commands = null, activeSessionId = active)

        assertFalse(service.active?.busy ?: true)

        runtime.busySessionIds.value = setOf("session-1")

        assertTrue(service.active?.busy ?: false)
    }

    @Test
    fun observeEmitsWhenActiveSessionStartsAndStopsRunning() = runTest {
        val runtime = FakeConversationQueries().apply { titles = mapOf("session-1" to "First") }
        val active = MutableStateFlow<String?>("session-1")
        val service = SessionPluginService(runtime, commands = null, activeSessionId = active)
        val received = mutableListOf<io.github.whywood.deepdeep.plugin.api.SessionInfo?>()
        val collector = launch { service.observe().take(3).toList(received) }
        runCurrent()

        runtime.busySessionIds.value = setOf("session-1")
        runCurrent()
        runtime.busySessionIds.value = emptySet()
        runCurrent()
        collector.join()

        assertEquals(listOf(false, true, false), received.map { it?.busy })
        assertEquals("First", received.first()?.title)
    }

    @Test
    fun observeEmitsWhenActiveSessionChanges() = runTest {
        val runtime = FakeConversationQueries().apply {
            titles = mapOf("session-1" to "First", "session-2" to "Second")
        }
        val active = MutableStateFlow<String?>("session-1")
        val service = SessionPluginService(runtime, commands = null, activeSessionId = active)
        val received = mutableListOf<io.github.whywood.deepdeep.plugin.api.SessionInfo?>()
        val collector = launch { service.observe().take(2).toList(received) }
        runCurrent()

        active.value = "session-2"
        runCurrent()
        collector.join()

        assertEquals(listOf("session-1", "session-2"), received.map { it?.id })
        assertEquals("Second", received.last()?.title)
    }

    @Test
    fun observeIgnoresBusyChangesOfOtherSessions() = runTest {
        val runtime = FakeConversationQueries()
        val active = MutableStateFlow<String?>("session-1")
        val service = SessionPluginService(runtime, commands = null, activeSessionId = active)
        val received = mutableListOf<io.github.whywood.deepdeep.plugin.api.SessionInfo?>()
        val collector = launch { service.observe().take(2).toList(received) }
        runCurrent()

        runtime.busySessionIds.value = setOf("other-session")
        runCurrent()
        active.value = "session-2"
        runCurrent()
        collector.join()

        assertEquals(listOf("session-1", "session-2"), received.map { it?.id })
        assertEquals(listOf(false, false), received.map { it?.busy })
    }

    private class FakeConversationQueries : ConversationQueries {
        override val events = MutableSharedFlow<SessionRuntimeEvent>()

        override val busySessionIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

        override val activeRuns = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())

        override val interruptedRuns = MutableStateFlow<List<InterruptedRun>>(emptyList())

        var titles: Map<String, String> = emptyMap()

        override suspend fun workspaces(): List<RuntimeWorkspace> = emptyList()

        override suspend fun allSessions(): List<ConversationSummary> = emptyList()

        override suspend fun sessions(workspaceId: String): List<ConversationSummary> = emptyList()

        override suspend fun archivedSessions(): List<ConversationSummary> = emptyList()

        override suspend fun history(sessionId: String): List<AssistantMessage> = emptyList()

        override suspend fun sessionTitle(sessionId: String): String? = titles[sessionId]

        override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = null
    }
}
