package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class UsageContractTest {
    @Test
    fun runtimesWithoutUsageSupportReportNoUsage() = runTest {
        val runtime = MinimalAgentRuntime()
        assertNull(runtime.usage("session-1"))
        assertSame(CompactionOutcome.Unavailable, runtime.compact("session-1"))
    }

    private class MinimalAgentRuntime : AgentRuntime {
        override val events = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 16)
        override val busySessionIds = MutableStateFlow<Set<String>>(emptySet())
        override val activeRuns = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
        override val interruptedRuns = MutableStateFlow<List<InterruptedRun>>(emptyList())

        override suspend fun dismissInterruptedRun(sessionId: String) = Unit

        override suspend fun start() = Unit

        override suspend fun ensureStarted() = Unit

        override suspend fun workspaces(): List<RuntimeWorkspace> = emptyList()

        override suspend fun createWorkspace(name: String): RuntimeWorkspace = error("unused")

        override suspend fun renameWorkspace(id: String, name: String) = Unit

        override suspend fun deleteWorkspace(id: String) = Unit

        override suspend fun allSessions(): List<ConversationSummary> = emptyList()

        override suspend fun workspaceSessionCount(workspaceId: String): Int = 0

        override suspend fun newSession(workspaceId: String): OpenedSession = error("unused")

        override suspend fun openSession(filePath: String, cwd: String): OpenedSession = error("unused")

        override suspend fun closeSession(sessionId: String) = Unit

        override suspend fun reloadSession(sessionId: String): String = error("unused")

        override suspend fun send(sessionId: String, text: String) = Unit

        override suspend fun retryLastTurn(sessionId: String) = Unit

        override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) = Unit

        override suspend fun activeRunId(sessionId: String): String? = null

        override fun stop(sessionId: String) = Unit

        override suspend fun history(sessionId: String): List<AssistantMessage> = emptyList()

        override suspend fun sessionTitle(sessionId: String): String? = null

        override suspend fun sessions(workspaceId: String): List<ConversationSummary> = emptyList()

        override suspend fun archivedSessions(): List<ConversationSummary> = emptyList()

        override suspend fun renameSession(id: String, title: String) = Unit

        override suspend fun setSessionPinned(id: String, pinned: Boolean) = Unit

        override suspend fun setSessionArchived(id: String, archived: Boolean) = Unit

        override suspend fun deleteSession(id: String) = Unit

        override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = null

        override suspend fun setSessionModel(
            sessionId: String,
            providerId: String,
            modelId: String,
            thinkingLevel: ThinkingLevel,
        ) = Unit

        override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) = Unit

        override suspend fun currentConfig(): ModelConfig = ModelConfig()
    }
}
