package io.github.whywood.deepdeep.ui.chat.testing

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

const val HOME_ID = "home"
const val HOME_CWD = "/home"

class FakeAgentRuntime : AgentRuntime {
    private val flow = MutableSharedFlow<SessionRuntimeEvent>(extraBufferCapacity = 64)
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    val runId = "run-1"
    override val events: SharedFlow<SessionRuntimeEvent> = flow
    override val busySessionIds: StateFlow<Set<String>> = busy
    val interruptedRunState = MutableStateFlow<List<InterruptedRun>>(emptyList())
    override val interruptedRuns: StateFlow<List<InterruptedRun>> = interruptedRunState
    override val activeRuns = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
    var sessions: List<ConversationSummary> = emptyList()
    var histories: Map<String, List<AssistantMessage>> = emptyMap()
    var sessionModels: MutableMap<String, SessionModelSnapshot> = mutableMapOf()
    val historyCalls = mutableMapOf<String, Int>()
    val openCalls = mutableListOf<String>()
    val sent = mutableListOf<String>()
    val sentImages = mutableListOf<List<ImagePart>>()
    val steered = mutableListOf<String>()
    val followUps = mutableListOf<String>()
    var queued: List<QueuedInput> = emptyList()
    var stopDropped: List<String> = emptyList()
    val delivered = mutableListOf<Triple<String, String, String?>>()
    val stopped = mutableListOf<String>()
    private var createdCount = 0
    private val extraProjects = mutableListOf<RuntimeWorkspace>()

    fun setBusy(ids: Set<String>) {
        busy.value = ids
    }

    override suspend fun start() = Unit

    override suspend fun ensureStarted() = Unit

    val dismissedRuns = mutableListOf<String>()

    override suspend fun dismissInterruptedRun(sessionId: String) {
        dismissedRuns += sessionId
    }

    override suspend fun workspaces(): List<RuntimeWorkspace> =
        listOf(RuntimeWorkspace(HOME_ID, "Home", HOME_CWD, isHome = true)) + extraProjects

    override suspend fun createWorkspace(name: String): RuntimeWorkspace {
        val workspace = RuntimeWorkspace("workspace-$name", name, "/workspaces/$name", createdAt = 1L)
        extraProjects += workspace
        return workspace
    }

    override suspend fun renameWorkspace(id: String, name: String) = Unit

    override suspend fun deleteWorkspace(id: String) = Unit

    override suspend fun allSessions(): List<ConversationSummary> = sessions.filter { !it.archived }

    override suspend fun workspaceSessionCount(workspaceId: String): Int = sessions.size

    override suspend fun newSession(workspaceId: String): OpenedSession {
        createdCount += 1
        val id = "session-$createdCount"
        sessions = sessions + ConversationSummary(id, "", createdCount.toLong(), "$id.jsonl", HOME_CWD)
        return OpenedSession(id, "$id.jsonl", HOME_CWD)
    }

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
        openCalls += filePath
        val summary = sessions.firstOrNull { it.filePath == filePath }
        return OpenedSession(summary?.id ?: filePath, filePath, cwd)
    }

    override suspend fun closeSession(sessionId: String) = Unit

    override suspend fun reloadSession(sessionId: String): String = sessionId

    override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) {
        sent += text
        sentImages += images
    }

    override suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) {
        steered += text
        sentImages += images
    }

    override suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) {
        followUps += text
        sentImages += images
    }

    override fun queuedInputs(sessionId: String): List<QueuedInput> = queued

    override fun clearQueuedInputs(sessionId: String): List<String> {
        val dropped = queued.map { it.text }
        queued = emptyList()
        return dropped
    }

    var retried = 0

    override suspend fun retryLastTurn(sessionId: String) {
        retried += 1
    }

    override fun stop(sessionId: String): List<String> {
        stopped += sessionId
        val dropped = stopDropped
        stopDropped = emptyList()
        return dropped
    }

    override suspend fun history(sessionId: String): List<AssistantMessage> {
        historyCalls[sessionId] = (historyCalls[sessionId] ?: 0) + 1
        return histories[sessionId].orEmpty()
    }

    override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
        delivered += Triple(sessionId, text, reminder)
    }

    override suspend fun activeRunId(sessionId: String): String? = activeRuns.value[sessionId]?.runId

    override suspend fun sessionTitle(sessionId: String): String? =
        sessions.firstOrNull { it.id == sessionId }?.title

    override suspend fun sessions(workspaceId: String): List<ConversationSummary> =
        sessions.filter { !it.archived }

    override suspend fun archivedSessions(): List<ConversationSummary> =
        sessions.filter { it.archived }

    override suspend fun renameSession(id: String, title: String) = Unit

    override suspend fun setSessionPinned(id: String, pinned: Boolean) = Unit

    override suspend fun setSessionArchived(id: String, archived: Boolean) = Unit

    override suspend fun deleteSession(id: String) = Unit

    override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = sessionModels[sessionId]

    override suspend fun setSessionModel(
        sessionId: String,
        providerId: String,
        modelId: String,
        thinkingLevel: ThinkingLevel,
    ) {
        sessionModels[sessionId] = SessionModelSnapshot(providerId, modelId, thinkingLevel)
    }

    override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) {
        sessionModels[sessionId]?.let { sessionModels[sessionId] = it.copy(thinkingLevel = thinkingLevel) }
    }

    override suspend fun currentConfig(): ModelConfig = ModelConfig()

    suspend fun emitFor(sessionId: String, event: RuntimeEvent) =
        flow.emit(SessionRuntimeEvent(sessionId, event))
}
