package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject

enum class ThinkingLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
    MAX,
}

data class ModelOption(
    val providerId: String,
    val id: String,
    val displayName: String,
    val thinkingLevels: List<ThinkingLevel>,
)

data class ProviderSummary(
    val id: String,
    val name: String,
    val modelCount: Int,
    val baseUrl: String?,
    val hasOAuth: Boolean,
    val supportsApiKey: Boolean,
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val filePath: String,
    val cwd: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

data class RuntimeWorkspace(
    val id: String,
    val name: String,
    val cwd: String,
    val createdAt: Long = 0L,
    val isHome: Boolean = false,
)

data class OpenedSession(
    val sessionId: String,
    val filePath: String?,
    val cwd: String,
)

data class SessionRuntimeEvent(
    val sessionId: String,
    val event: RuntimeEvent,
)

data class ActiveRunSnapshot(
    val runId: String,
    val startedAt: Long,
)

data class SessionModelSnapshot(
    val providerId: String,
    val modelId: String,
    val thinkingLevel: ThinkingLevel,
)

data class InterruptedRun(
    val sessionId: String,
    val runId: String,
    val startedAt: Long,
    val source: TurnSource,
)

data class InterruptedQuestion(
    val requestId: String,
    val sessionId: String,
    val toolCallId: String,
    val questions: List<UserQuestion>,
    val createdAt: Long,
)

data class ImagePart(
    val data: String,
    val mimeType: String,
    val path: String? = null,
)

enum class InputQueueKind {
    STEER,
    FOLLOW_UP,
}

data class QueuedInput(
    val kind: InputQueueKind,
    val text: String,
)

enum class AssistantRole {
    USER,
    ASSISTANT,
}

enum class MessageStatus {
    RUNNING,
    DONE,
    STOPPED,
    FAILED,
}

sealed interface AssistantStep {
    val id: Long
    val expanded: Boolean

    data class Reasoning(
        override val id: Long,
        val text: String,
        override val expanded: Boolean = false,
    ) : AssistantStep

    data class Tool(
        val trace: ToolTrace,
    ) : AssistantStep {
        override val id: Long get() = trace.id
        override val expanded: Boolean get() = trace.expanded
    }

    data class Text(
        override val id: Long,
        val text: String,
    ) : AssistantStep {
        override val expanded: Boolean = false
    }

    data class Notice(
        override val id: Long,
        val notice: TurnNotice,
    ) : AssistantStep {
        override val expanded: Boolean = false
    }
}

enum class ToolActivityStatus {
    RUNNING,
    DONE,
    FAILED,
}

data class ToolTrace(
    val id: Long,
    val name: String,
    val arguments: Map<String, String>,
    val status: ToolActivityStatus = ToolActivityStatus.RUNNING,
    val output: String? = null,
    val error: String? = null,
    val expanded: Boolean = false,
    val toolCallId: String = "",
    val startedAt: Long = 0,
    val durationMs: Long? = null,
    val rawArguments: JsonObject? = null,
    val resultPayload: ToolResultPayload? = null,
)

enum class MessageSummaryKind {
    COMPACTION,
    BRANCH,
}

data class MessageSummary(
    val kind: MessageSummaryKind,
    val text: String,
    val tokensBefore: Long? = null,
    val tokensAfter: Long? = null,
    val fromId: String? = null,
)

data class AssistantMessage(
    val id: Long,
    val role: AssistantRole,
    val text: String,
    val streaming: Boolean = false,
    val steps: List<AssistantStep> = emptyList(),
    val status: MessageStatus = MessageStatus.DONE,
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    val runId: String = "",
    val error: String? = null,
    val rawError: String? = null,
    val reminder: String? = null,
    val retrying: Boolean = false,
    val notice: TurnNotice? = null,
    val summary: MessageSummary? = null,
    val customType: String? = null,
    val customPayload: String? = null,
    val injectedSource: TurnSource? = null,
)

enum class RunStatus {
    DONE,
    STOPPED,
    FAILED,
}

sealed interface RuntimeActivity {
    data object Thinking : RuntimeActivity

    data class Retrying(val attempt: Int) : RuntimeActivity

    data object Compacting : RuntimeActivity

    data object CompactionFailed : RuntimeActivity

    data class RunningTool(val name: String) : RuntimeActivity
}

sealed interface RuntimeEvent {
    val runId: String

    data class RunStarted(
        override val runId: String,
        val startedAt: Long,
        val prompt: String = "",
    ) : RuntimeEvent

    data class ActivityChanged(
        override val runId: String,
        val activity: RuntimeActivity?,
    ) : RuntimeEvent

    data class TextDelta(
        override val runId: String,
        val text: String,
    ) : RuntimeEvent

    data class ReasoningDelta(
        override val runId: String,
        val text: String,
    ) : RuntimeEvent

    data class ToolStarted(
        override val runId: String,
        val callId: String,
        val name: String,
        val arguments: Map<String, String>,
        val startedAt: Long,
        val rawArguments: JsonObject? = null,
    ) : RuntimeEvent

    data class ToolCallStarted(
        override val runId: String,
        val callId: String,
        val name: String,
        val startedAt: Long,
    ) : RuntimeEvent

    data class ToolFinished(
        override val runId: String,
        val callId: String,
        val name: String,
        val result: String,
        val isError: Boolean,
        val durationMs: Long?,
        val payload: ToolResultPayload? = null,
    ) : RuntimeEvent

    data class RunFinished(
        override val runId: String,
        val text: String,
        val status: RunStatus,
        val finishedAt: Long,
    ) : RuntimeEvent

    data class RunFailed(
        override val runId: String,
        val message: String,
    ) : RuntimeEvent

    data class TurnInjected(
        override val runId: String,
        val text: String,
        val reminder: String?,
        val source: TurnSource = TurnSource.USER,
    ) : RuntimeEvent

    data class UserInputAdmitted(
        override val runId: String,
        val text: String,
    ) : RuntimeEvent

    data class QueueChanged(
        override val runId: String,
        val steering: List<String>,
        val followUp: List<String>,
    ) : RuntimeEvent

    data class QueuedInputsDropped(
        val texts: List<String>,
        override val runId: String = "",
    ) : RuntimeEvent

    data class PluginCustomMessage(
        override val runId: String = "",
        val customType: String,
        val payload: String,
        val text: String,
        val display: Boolean = true,
    ) : RuntimeEvent

    data class SessionRemoved(
        override val runId: String = "",
    ) : RuntimeEvent

    data class SessionArchived(
        override val runId: String = "",
        val archived: Boolean,
    ) : RuntimeEvent

    data class SessionListChanged(
        override val runId: String = "",
    ) : RuntimeEvent
}

sealed interface PluginTurnResult {
    data class Started(val runId: String) : PluginTurnResult

    data class Queued(val runId: String) : PluginTurnResult
}

interface ModelSettings {
    val config: kotlinx.coroutines.flow.Flow<ModelConfig>

    suspend fun read(): ModelConfig

    suspend fun save(config: ModelConfig)
}

interface SessionCommands {
    suspend fun newSession(workspaceId: String): OpenedSession

    suspend fun openSession(filePath: String, cwd: String): OpenedSession

    suspend fun closeSession(sessionId: String)

    suspend fun reloadSession(sessionId: String): String

    suspend fun send(sessionId: String, text: String, images: List<ImagePart> = emptyList())

    suspend fun steer(sessionId: String, text: String, images: List<ImagePart> = emptyList())

    suspend fun followUp(sessionId: String, text: String, images: List<ImagePart> = emptyList())

    fun queuedInputs(sessionId: String): List<QueuedInput>

    fun clearQueuedInputs(sessionId: String): List<String>

    fun removeQueuedInput(sessionId: String, kind: InputQueueKind, text: String): Boolean

    suspend fun retryLastTurn(sessionId: String)

    suspend fun deliverReminder(sessionId: String, text: String, reminder: String?)

    suspend fun deliverPluginTurn(sessionId: String, text: String, followUp: Boolean = false): PluginTurnResult

    suspend fun activeRunId(sessionId: String): String?

    fun stop(sessionId: String): List<String>
}

interface ConversationQueries {
    val events: SharedFlow<SessionRuntimeEvent>

    val busySessionIds: kotlinx.coroutines.flow.StateFlow<Set<String>>

    val activeRuns: kotlinx.coroutines.flow.StateFlow<Map<String, ActiveRunSnapshot>>

    val interruptedRuns: kotlinx.coroutines.flow.StateFlow<List<InterruptedRun>>

    suspend fun workspaces(): List<RuntimeWorkspace>

    suspend fun allSessions(): List<ConversationSummary>

    suspend fun sessions(workspaceId: String): List<ConversationSummary>

    suspend fun archivedSessions(): List<ConversationSummary>

    suspend fun history(sessionId: String): List<AssistantMessage>

    suspend fun sessionTitle(sessionId: String): String?

    suspend fun sessionModel(sessionId: String): SessionModelSnapshot?

    suspend fun usage(sessionId: String): SessionUsage? = null
}

interface WorkspaceAdmin {
    suspend fun createWorkspace(name: String, directory: String? = null): RuntimeWorkspace

    suspend fun renameWorkspace(id: String, name: String)

    suspend fun deleteWorkspace(id: String)

    suspend fun workspaceSessionCount(workspaceId: String): Int
}

interface ModelControl {
    suspend fun setSessionModel(sessionId: String, providerId: String, modelId: String, thinkingLevel: ThinkingLevel)

    suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel)

    suspend fun currentConfig(): ModelConfig
}

interface Maintenance {
    suspend fun start()

    suspend fun ensureStarted()

    suspend fun dismissInterruptedRun(sessionId: String)

    suspend fun compact(sessionId: String): CompactionOutcome = CompactionOutcome.Unavailable

    suspend fun renameSession(id: String, title: String)

    suspend fun setSessionPinned(id: String, pinned: Boolean)

    suspend fun setSessionArchived(id: String, archived: Boolean)

    suspend fun deleteSession(id: String)
}

interface AgentRuntime : SessionCommands, ConversationQueries, WorkspaceAdmin, ModelControl, Maintenance
