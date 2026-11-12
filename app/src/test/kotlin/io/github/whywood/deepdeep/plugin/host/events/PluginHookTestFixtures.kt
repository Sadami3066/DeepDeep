package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginContextRequest
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision
import io.github.whywood.deepdeep.plugin.api.inputContinue
import io.github.whywood.deepdeep.plugin.api.resultKeep
import io.github.whywood.deepdeep.plugin.api.toolContinue
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import io.github.whywood.deepdeep.plugin.api.MessageRole
import io.github.whywood.deepdeep.plugin.api.ToolPhase
import io.github.whywood.deepdeep.plugin.api.BusEventReceived
import io.github.whywood.deepdeep.plugin.api.CompactionFailedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionFinishedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionStartedEvent
import io.github.whywood.deepdeep.plugin.api.McpStatusChangedEvent
import io.github.whywood.deepdeep.plugin.api.McpToolsChangedEvent
import io.github.whywood.deepdeep.plugin.api.MessageDeltaEvent
import io.github.whywood.deepdeep.plugin.api.MessageFinishedEvent
import io.github.whywood.deepdeep.plugin.api.MessageStartedEvent
import io.github.whywood.deepdeep.plugin.api.ModelChangedEvent
import io.github.whywood.deepdeep.plugin.api.RetryExhaustedEvent
import io.github.whywood.deepdeep.plugin.api.RetryScheduledEvent
import io.github.whywood.deepdeep.plugin.api.RetrySucceededEvent
import io.github.whywood.deepdeep.plugin.api.RunFinishedEvent
import io.github.whywood.deepdeep.plugin.api.RunStartedEvent
import io.github.whywood.deepdeep.plugin.api.SessionClosedEvent
import io.github.whywood.deepdeep.plugin.api.SessionStartedEvent
import io.github.whywood.deepdeep.plugin.api.ThinkingLevelChangedEvent
import io.github.whywood.deepdeep.plugin.api.ToolExecutionUpdateEvent
import io.github.whywood.deepdeep.plugin.api.TurnFinishedEvent
import io.github.whywood.deepdeep.plugin.api.TurnStartedEvent

internal fun hookEntry(pluginId: String, hooks: PluginHooks): PluginHooksEntry = PluginHooksEntry(pluginId, hooks)

internal class TestHookSource(var list: List<PluginHooksEntry> = emptyList()) : PluginHookSource {
    var reads = 0

    override fun entries(): List<PluginHooksEntry> {
        reads += 1
        return list
    }
}

internal class RecordingReporter : PluginHookReporter {
    val failures = mutableListOf<PluginHookFailure>()

    override fun report(failure: PluginHookFailure) {
        failures += failure
    }
}

internal fun probeHooks(
    onPrompt: (suspend (PromptRequest) -> String)? = null,
    onInput: (suspend (InputRequest) -> InputDecision)? = null,
    onBefore: (suspend (ToolCallRequest) -> ToolCallDecision)? = null,
    onAfter: (suspend (ToolExecutionResult) -> ToolResultDecision)? = null,
    onContext: (suspend (PluginContextRequest) -> String?)? = null,
): PluginHooks = object : PluginHooks {
    override suspend fun promptTransform(request: PromptRequest): String =
        onPrompt?.invoke(request) ?: request.prompt

    override suspend fun inputTransform(request: InputRequest): InputDecision =
        onInput?.invoke(request) ?: inputContinue(request.text, request.imagePaths)

    override suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision =
        onBefore?.invoke(request) ?: toolContinue()

    override suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision =
        onAfter?.invoke(request) ?: resultKeep()

    override suspend fun context(request: PluginContextRequest): String? = onContext?.invoke(request)
}

internal fun agentTextResult(text: String, isError: Boolean = false): AgentToolResult =
    AgentToolResult(content = listOf(TextContent(text)), isError = isError.takeIf { it })

internal class EventRecordingHooks : PluginHooks {
    val events = mutableListOf<PluginEvent>()

    val calls = mutableListOf<String>()

    override suspend fun sessionStarted(event: SessionStartedEvent) = record("sessionStarted", event)

    override suspend fun sessionClosed(event: SessionClosedEvent) = record("sessionClosed", event)

    override suspend fun runStarted(event: RunStartedEvent) = record("runStarted", event)

    override suspend fun runFinished(event: RunFinishedEvent) = record("runFinished", event)

    override suspend fun turnStarted(event: TurnStartedEvent) = record("turnStarted", event)

    override suspend fun turnFinished(event: TurnFinishedEvent) = record("turnFinished", event)

    override suspend fun messageStarted(event: MessageStartedEvent) = record("messageStarted", event)

    override suspend fun messageDelta(event: MessageDeltaEvent) = record("messageDelta", event)

    override suspend fun messageFinished(event: MessageFinishedEvent) = record("messageFinished", event)

    override suspend fun toolExecutionUpdate(event: ToolExecutionUpdateEvent) =
        record("toolExecutionUpdate", event)

    override suspend fun compactionStarted(event: CompactionStartedEvent) = record("compactionStarted", event)

    override suspend fun compactionFinished(event: CompactionFinishedEvent) =
        record("compactionFinished", event)

    override suspend fun compactionFailed(event: CompactionFailedEvent) = record("compactionFailed", event)

    override suspend fun modelChanged(event: ModelChangedEvent) = record("modelChanged", event)

    override suspend fun thinkingLevelChanged(event: ThinkingLevelChangedEvent) =
        record("thinkingLevelChanged", event)

    override suspend fun retryScheduled(event: RetryScheduledEvent) = record("retryScheduled", event)

    override suspend fun retrySucceeded(event: RetrySucceededEvent) = record("retrySucceeded", event)

    override suspend fun retryExhausted(event: RetryExhaustedEvent) = record("retryExhausted", event)

    override suspend fun mcpStatusChanged(event: McpStatusChangedEvent) = record("mcpStatusChanged", event)

    override suspend fun mcpToolsChanged(event: McpToolsChangedEvent) = record("mcpToolsChanged", event)

    override suspend fun busEvent(event: BusEventReceived) = record("busEvent", event)

    private fun record(name: String, event: PluginEvent) {
        calls += name
        events += event
    }
}

internal fun allPluginEvents(): List<PluginEvent> = listOf(
    HostSessionStartedEvent("session-1", "title", "/workspace"),
    HostSessionClosedEvent("session-1"),
    HostRunStartedEvent("session-1", "run-1"),
    HostRunFinishedEvent("session-1", "run-1", null),
    HostTurnStartedEvent("session-1", "turn-1"),
    HostTurnFinishedEvent("session-1", "turn-1", false),
    HostMessageStartedEvent("session-1", "message-1", MessageRole.USER),
    HostMessageDeltaEvent("session-1", "message-1", "delta"),
    HostMessageFinishedEvent("session-1", "message-1", "text"),
    HostToolExecutionUpdateEvent("session-1", "call-1", "bash", ToolPhase.STARTED, null),
    HostCompactionStartedEvent("session-1"),
    HostCompactionFinishedEvent("session-1", 100L, 50L),
    HostCompactionFailedEvent("session-1", "boom"),
    HostModelChangedEvent("session-1", "model"),
    HostThinkingLevelChangedEvent("session-1", "high"),
    HostRetryScheduledEvent("session-1", 1, 500L, "rate limited"),
    HostRetrySucceededEvent("session-1", 1),
    HostRetryExhaustedEvent("session-1", 3, "boom"),
    HostMcpStatusChangedEvent("mcp", McpServerStatus.OK),
    HostMcpToolsChangedEvent("mcp", listOf("tool")),
    HostBusEventReceived(HostPluginBusEvent("topic", "publisher", kotlinx.serialization.json.JsonObject(emptyMap()), 0)),
)

internal val pluginEventMethodNames: List<String> = listOf(
    "sessionStarted",
    "sessionClosed",
    "runStarted",
    "runFinished",
    "turnStarted",
    "turnFinished",
    "messageStarted",
    "messageDelta",
    "messageFinished",
    "toolExecutionUpdate",
    "compactionStarted",
    "compactionFinished",
    "compactionFailed",
    "modelChanged",
    "thinkingLevelChanged",
    "retryScheduled",
    "retrySucceeded",
    "retryExhausted",
    "mcpStatusChanged",
    "mcpToolsChanged",
    "busEvent",
)
