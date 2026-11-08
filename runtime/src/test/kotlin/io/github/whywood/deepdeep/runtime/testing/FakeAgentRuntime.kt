package io.github.whywood.deepdeep.runtime.testing

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.CompactionOutcome
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.PluginTurnResult
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class DeliveryRecord(
    val sessionId: String,
    val text: String,
    val reminder: String?,
)

class FakeAgentRuntime : AgentRuntime {
    override val events = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 16)
    override val busySessionIds = MutableStateFlow<Set<String>>(emptySet())
    override val activeRuns = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
    override val interruptedRuns = MutableStateFlow<List<InterruptedRun>>(emptyList())

    val deliveries = mutableListOf<DeliveryRecord>()
    val openedFiles = mutableListOf<String>()
    var openFailure: Exception? = null
    var deliveryFailure: Exception? = null
    var histories: Map<String, List<AssistantMessage>> = emptyMap()
    var sessions: List<ConversationSummary> = emptyList()
    var usageResult: SessionUsage? = null
    var compactionOutcome: CompactionOutcome = CompactionOutcome.Unavailable

    fun setBusy(ids: Set<String>) {
        busySessionIds.value = ids
    }

    override suspend fun dismissInterruptedRun(sessionId: String) = Unit

    override suspend fun start() = Unit

    override suspend fun ensureStarted() = Unit

    override suspend fun workspaces(): List<RuntimeWorkspace> = emptyList()

    override suspend fun createWorkspace(name: String, directory: String?): RuntimeWorkspace = error("unused")

    override suspend fun renameWorkspace(id: String, name: String) = Unit

    override suspend fun deleteWorkspace(id: String) = Unit

    override suspend fun allSessions(): List<ConversationSummary> = sessions.filter { !it.archived }

    override suspend fun workspaceSessionCount(workspaceId: String): Int = 0

    override suspend fun newSession(workspaceId: String): OpenedSession = error("unused")

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
        openFailure?.let { throw it }
        openedFiles += filePath
        val sessionId = filePath.substringAfterLast('/').substringBeforeLast('.')
        return OpenedSession(sessionId = sessionId, filePath = filePath, cwd = cwd)
    }

    override suspend fun closeSession(sessionId: String) = Unit

    override suspend fun reloadSession(sessionId: String): String = error("unused")

    override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) = Unit

    override suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) = Unit

    override suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) = Unit

    override fun queuedInputs(sessionId: String): List<QueuedInput> = emptyList()

    override fun clearQueuedInputs(sessionId: String): List<String> = emptyList()

    override fun removeQueuedInput(
        sessionId: String,
        kind: InputQueueKind,
        text: String,
    ): Boolean = kind in InputQueueKind.entries && text.isNotEmpty()

    override suspend fun retryLastTurn(sessionId: String) = Unit

    override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
        deliveryFailure?.let { throw it }
        deliveries += DeliveryRecord(sessionId, text, reminder)
    }

    override suspend fun deliverPluginTurn(
        sessionId: String,
        text: String,
        followUp: Boolean,
    ): PluginTurnResult {
        deliveryFailure?.let { throw it }
        deliveries += DeliveryRecord(sessionId, text, null)
        return PluginTurnResult.Started("run")
    }

    override suspend fun activeRunId(sessionId: String): String? = activeRuns.value[sessionId]?.runId

    override fun stop(sessionId: String): List<String> = emptyList()

    override suspend fun history(sessionId: String): List<AssistantMessage> = histories[sessionId].orEmpty()

    override suspend fun sessionTitle(sessionId: String): String? = sessions.firstOrNull { it.id == sessionId }?.title

    override suspend fun sessions(workspaceId: String): List<ConversationSummary> = sessions.filter { !it.archived }

    override suspend fun archivedSessions(): List<ConversationSummary> = sessions.filter { it.archived }

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

    override suspend fun usage(sessionId: String): SessionUsage? = usageResult

    override suspend fun compact(sessionId: String): CompactionOutcome = compactionOutcome
}
