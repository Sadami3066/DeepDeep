package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

sealed interface PluginEvent {
    data class SessionStarted(
        val sessionId: String,
        val title: String? = null,
        val workspace: String? = null,
    ) : PluginEvent

    data class SessionClosed(val sessionId: String) : PluginEvent

    data class RunStarted(
        val sessionId: String,
        val runId: String,
    ) : PluginEvent

    data class RunFinished(
        val sessionId: String,
        val runId: String,
        val error: String? = null,
    ) : PluginEvent {
        val succeeded: Boolean get() = error == null
    }

    data class TurnStarted(
        val sessionId: String,
        val turnId: String,
    ) : PluginEvent

    data class TurnFinished(
        val sessionId: String,
        val turnId: String,
        val failed: Boolean = false,
    ) : PluginEvent

    enum class MessageRole {
        USER,
        ASSISTANT,
        TOOL,
        SYSTEM,
    }

    data class MessageStarted(
        val sessionId: String,
        val messageId: String,
        val role: MessageRole,
    ) : PluginEvent

    data class MessageDelta(
        val sessionId: String,
        val messageId: String,
        val delta: String,
    ) : PluginEvent

    data class MessageFinished(
        val sessionId: String,
        val messageId: String,
        val text: String? = null,
    ) : PluginEvent

    enum class ToolPhase {
        STARTED,
        PARTIAL,
        FINISHED,
        FAILED,
    }

    data class ToolExecutionUpdate(
        val sessionId: String,
        val callId: String,
        val toolName: String,
        val phase: ToolPhase,
        val partial: PluginToolResult? = null,
    ) : PluginEvent

    data class CompactionStarted(val sessionId: String) : PluginEvent

    data class CompactionFinished(
        val sessionId: String,
        val tokensBefore: Long,
        val tokensAfter: Long,
    ) : PluginEvent

    data class CompactionFailed(
        val sessionId: String,
        val error: String,
    ) : PluginEvent

    data class ModelChanged(
        val sessionId: String,
        val model: String,
    ) : PluginEvent

    data class ThinkingLevelChanged(
        val sessionId: String,
        val level: String,
    ) : PluginEvent

    data class RetryScheduled(
        val sessionId: String,
        val attempt: Int,
        val delayMs: Long,
        val reason: String? = null,
    ) : PluginEvent

    data class RetrySucceeded(
        val sessionId: String,
        val attempt: Int,
    ) : PluginEvent

    data class RetryExhausted(
        val sessionId: String,
        val attempts: Int,
        val error: String,
    ) : PluginEvent

    data class McpStatusChanged(
        val server: String,
        val status: McpServerStatus,
    ) : PluginEvent

    data class McpToolsChanged(
        val server: String,
        val tools: List<String>,
    ) : PluginEvent

    data class BusEvent(val event: PluginBusEvent) : PluginEvent
}

data class PromptRequest(
    val sessionId: String,
    val prompt: String,
)

data class InputRequest(
    val sessionId: String,
    val text: String,
    val imagePaths: List<String> = emptyList(),
)

sealed interface InputDecision {
    data class Continue(
        val text: String,
        val imagePaths: List<String> = emptyList(),
    ) : InputDecision

    data object Handled : InputDecision

    companion object {
        fun continueWith(request: InputRequest): Continue = Continue(request.text, request.imagePaths)
    }
}

data class ToolCallRequest(
    val sessionId: String,
    val callId: String,
    val toolName: String,
    val args: JsonObject,
)

sealed interface ToolCallDecision {
    data object Continue : ToolCallDecision

    data class Block(val reason: String) : ToolCallDecision

    data class Rewrite(val args: JsonObject) : ToolCallDecision
}

data class ToolExecutionResult(
    val sessionId: String,
    val callId: String,
    val toolName: String,
    val result: PluginToolResult,
)

sealed interface ToolResultDecision {
    data object Keep : ToolResultDecision

    data class Replace(val result: PluginToolResult) : ToolResultDecision
}

interface PluginHooks {
    suspend fun sessionStarted(event: PluginEvent.SessionStarted) = Unit

    suspend fun sessionClosed(event: PluginEvent.SessionClosed) = Unit

    suspend fun runStarted(event: PluginEvent.RunStarted) = Unit

    suspend fun runFinished(event: PluginEvent.RunFinished) = Unit

    suspend fun turnStarted(event: PluginEvent.TurnStarted) = Unit

    suspend fun turnFinished(event: PluginEvent.TurnFinished) = Unit

    suspend fun messageStarted(event: PluginEvent.MessageStarted) = Unit

    suspend fun messageDelta(event: PluginEvent.MessageDelta) = Unit

    suspend fun messageFinished(event: PluginEvent.MessageFinished) = Unit

    suspend fun toolExecutionUpdate(event: PluginEvent.ToolExecutionUpdate) = Unit

    suspend fun compactionStarted(event: PluginEvent.CompactionStarted) = Unit

    suspend fun compactionFinished(event: PluginEvent.CompactionFinished) = Unit

    suspend fun compactionFailed(event: PluginEvent.CompactionFailed) = Unit

    suspend fun modelChanged(event: PluginEvent.ModelChanged) = Unit

    suspend fun thinkingLevelChanged(event: PluginEvent.ThinkingLevelChanged) = Unit

    suspend fun retryScheduled(event: PluginEvent.RetryScheduled) = Unit

    suspend fun retrySucceeded(event: PluginEvent.RetrySucceeded) = Unit

    suspend fun retryExhausted(event: PluginEvent.RetryExhausted) = Unit

    suspend fun mcpStatusChanged(event: PluginEvent.McpStatusChanged) = Unit

    suspend fun mcpToolsChanged(event: PluginEvent.McpToolsChanged) = Unit

    suspend fun busEvent(event: PluginEvent.BusEvent) = Unit

    suspend fun promptTransform(request: PromptRequest): String = request.prompt

    suspend fun inputTransform(request: InputRequest): InputDecision = InputDecision.continueWith(request)

    suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision = ToolCallDecision.Continue

    suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision = ToolResultDecision.Keep

    companion object {
        const val TRANSFORM_TIMEOUT_MS: Long = 2_000

        const val OBSERVE_TIMEOUT_MS: Long = 5_000

        val None: PluginHooks = object : PluginHooks {}
    }
}
