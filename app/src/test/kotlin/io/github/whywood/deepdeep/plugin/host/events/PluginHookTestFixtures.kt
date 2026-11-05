package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry

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
): PluginHooks = object : PluginHooks {
    override suspend fun promptTransform(request: PromptRequest): String =
        onPrompt?.invoke(request) ?: request.prompt

    override suspend fun inputTransform(request: InputRequest): InputDecision =
        onInput?.invoke(request) ?: InputDecision.continueWith(request)

    override suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision =
        onBefore?.invoke(request) ?: ToolCallDecision.Continue

    override suspend fun afterToolExecution(request: ToolExecutionResult): ToolResultDecision =
        onAfter?.invoke(request) ?: ToolResultDecision.Keep
}

internal fun agentTextResult(text: String, isError: Boolean = false): AgentToolResult =
    AgentToolResult(content = listOf(TextContent(text)), isError = isError.takeIf { it })

internal class EventRecordingHooks : PluginHooks {
    val events = mutableListOf<PluginEvent>()

    val calls = mutableListOf<String>()

    override suspend fun sessionStarted(event: PluginEvent.SessionStarted) = record("sessionStarted", event)

    override suspend fun sessionClosed(event: PluginEvent.SessionClosed) = record("sessionClosed", event)

    override suspend fun runStarted(event: PluginEvent.RunStarted) = record("runStarted", event)

    override suspend fun runFinished(event: PluginEvent.RunFinished) = record("runFinished", event)

    override suspend fun turnStarted(event: PluginEvent.TurnStarted) = record("turnStarted", event)

    override suspend fun turnFinished(event: PluginEvent.TurnFinished) = record("turnFinished", event)

    override suspend fun messageStarted(event: PluginEvent.MessageStarted) = record("messageStarted", event)

    override suspend fun messageDelta(event: PluginEvent.MessageDelta) = record("messageDelta", event)

    override suspend fun messageFinished(event: PluginEvent.MessageFinished) = record("messageFinished", event)

    override suspend fun toolExecutionUpdate(event: PluginEvent.ToolExecutionUpdate) =
        record("toolExecutionUpdate", event)

    override suspend fun compactionStarted(event: PluginEvent.CompactionStarted) = record("compactionStarted", event)

    override suspend fun compactionFinished(event: PluginEvent.CompactionFinished) =
        record("compactionFinished", event)

    override suspend fun compactionFailed(event: PluginEvent.CompactionFailed) = record("compactionFailed", event)

    override suspend fun modelChanged(event: PluginEvent.ModelChanged) = record("modelChanged", event)

    override suspend fun thinkingLevelChanged(event: PluginEvent.ThinkingLevelChanged) =
        record("thinkingLevelChanged", event)

    override suspend fun retryScheduled(event: PluginEvent.RetryScheduled) = record("retryScheduled", event)

    override suspend fun retrySucceeded(event: PluginEvent.RetrySucceeded) = record("retrySucceeded", event)

    override suspend fun retryExhausted(event: PluginEvent.RetryExhausted) = record("retryExhausted", event)

    override suspend fun mcpStatusChanged(event: PluginEvent.McpStatusChanged) = record("mcpStatusChanged", event)

    override suspend fun mcpToolsChanged(event: PluginEvent.McpToolsChanged) = record("mcpToolsChanged", event)

    override suspend fun busEvent(event: PluginEvent.BusEvent) = record("busEvent", event)

    private fun record(name: String, event: PluginEvent) {
        calls += name
        events += event
    }
}

internal fun allPluginEvents(): List<PluginEvent> = listOf(
    PluginEvent.SessionStarted("session-1", "title", "/workspace"),
    PluginEvent.SessionClosed("session-1"),
    PluginEvent.RunStarted("session-1", "run-1"),
    PluginEvent.RunFinished("session-1", "run-1", null),
    PluginEvent.TurnStarted("session-1", "turn-1"),
    PluginEvent.TurnFinished("session-1", "turn-1", false),
    PluginEvent.MessageStarted("session-1", "message-1", PluginEvent.MessageRole.USER),
    PluginEvent.MessageDelta("session-1", "message-1", "delta"),
    PluginEvent.MessageFinished("session-1", "message-1", "text"),
    PluginEvent.ToolExecutionUpdate("session-1", "call-1", "bash", PluginEvent.ToolPhase.STARTED, null),
    PluginEvent.CompactionStarted("session-1"),
    PluginEvent.CompactionFinished("session-1", 100L, 50L),
    PluginEvent.CompactionFailed("session-1", "boom"),
    PluginEvent.ModelChanged("session-1", "model"),
    PluginEvent.ThinkingLevelChanged("session-1", "high"),
    PluginEvent.RetryScheduled("session-1", 1, 500L, "rate limited"),
    PluginEvent.RetrySucceeded("session-1", 1),
    PluginEvent.RetryExhausted("session-1", 3, "boom"),
    PluginEvent.McpStatusChanged("mcp", McpServerStatus.OK),
    PluginEvent.McpToolsChanged("mcp", listOf("tool")),
    PluginEvent.BusEvent(PluginBusEvent("topic", "publisher")),
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
