package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.SharedFlow

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
    val reminder: String? = null,
    val retrying: Boolean = false,
    val notice: TurnNotice? = null,
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
    ) : RuntimeEvent

    data class ToolFinished(
        override val runId: String,
        val callId: String,
        val name: String,
        val result: String,
        val isError: Boolean,
        val durationMs: Long?,
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

interface ProviderDirectory {
    val summaries: List<ProviderSummary>

    fun summary(providerId: String): ProviderSummary?

    fun models(providerId: String): List<ModelOption>

    fun resolveConfig(config: ModelConfig): ModelConfig

    fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model?

    fun modelReference(providerId: String, modelId: String): String
}

interface ModelSettings {
    val config: kotlinx.coroutines.flow.Flow<ModelConfig>

    suspend fun read(): ModelConfig

    suspend fun save(config: ModelConfig)
}

interface AgentRuntime {
    val events: SharedFlow<SessionRuntimeEvent>
    val busySessionIds: kotlinx.coroutines.flow.StateFlow<Set<String>>

    suspend fun start()

    suspend fun ensureStarted()

    suspend fun workspaces(): List<RuntimeWorkspace>

    suspend fun createWorkspace(name: String): RuntimeWorkspace

    suspend fun renameWorkspace(id: String, name: String)

    suspend fun deleteWorkspace(id: String)

    suspend fun allSessions(): List<ConversationSummary>

    suspend fun workspaceSessionCount(workspaceId: String): Int

    suspend fun newSession(workspaceId: String): OpenedSession

    suspend fun openSession(filePath: String, cwd: String): OpenedSession

    suspend fun closeSession(sessionId: String)

    suspend fun reloadSession(sessionId: String): String

    suspend fun send(sessionId: String, text: String)

    suspend fun retryLastTurn(sessionId: String)

    suspend fun deliverReminder(sessionId: String, text: String, reminder: String?)

    suspend fun activeRunId(sessionId: String): String?

    fun stop(sessionId: String)

    suspend fun history(sessionId: String): List<AssistantMessage>

    suspend fun sessionTitle(sessionId: String): String?

    suspend fun sessions(workspaceId: String): List<ConversationSummary>

    suspend fun archivedSessions(): List<ConversationSummary>

    suspend fun renameSession(id: String, title: String)

    suspend fun setSessionPinned(id: String, pinned: Boolean)

    suspend fun setSessionArchived(id: String, archived: Boolean)

    suspend fun deleteSession(id: String)

    suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel)

    suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel)

    suspend fun currentConfig(): ModelConfig

    suspend fun usage(sessionId: String): SessionUsage? = null

    suspend fun compact(sessionId: String): CompactionOutcome = CompactionOutcome.Unavailable
}
