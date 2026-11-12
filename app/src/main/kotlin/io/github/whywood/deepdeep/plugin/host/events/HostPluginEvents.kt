package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.BusEventReceived
import io.github.whywood.deepdeep.plugin.api.CompactionFailedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionFinishedEvent
import io.github.whywood.deepdeep.plugin.api.CompactionStartedEvent
import io.github.whywood.deepdeep.plugin.api.McpStatusChangedEvent
import io.github.whywood.deepdeep.plugin.api.McpToolsChangedEvent
import io.github.whywood.deepdeep.plugin.api.MessageDeltaEvent
import io.github.whywood.deepdeep.plugin.api.MessageFinishedEvent
import io.github.whywood.deepdeep.plugin.api.MessageRole
import io.github.whywood.deepdeep.plugin.api.MessageStartedEvent
import io.github.whywood.deepdeep.plugin.api.ModelChangedEvent
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.RetryExhaustedEvent
import io.github.whywood.deepdeep.plugin.api.RetryScheduledEvent
import io.github.whywood.deepdeep.plugin.api.RetrySucceededEvent
import io.github.whywood.deepdeep.plugin.api.RunFinishedEvent
import io.github.whywood.deepdeep.plugin.api.RunStartedEvent
import io.github.whywood.deepdeep.plugin.api.SessionClosedEvent
import io.github.whywood.deepdeep.plugin.api.SessionStartedEvent
import io.github.whywood.deepdeep.plugin.api.ThinkingLevelChangedEvent
import io.github.whywood.deepdeep.plugin.api.ToolExecutionUpdateEvent
import io.github.whywood.deepdeep.plugin.api.ToolPhase
import io.github.whywood.deepdeep.plugin.api.TurnFinishedEvent
import io.github.whywood.deepdeep.plugin.api.TurnStartedEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus

internal data class HostSessionStartedEvent(
    override val sessionId: String,
    override val title: String? = null,
    override val workspace: String? = null,
) : SessionStartedEvent

internal data class HostSessionClosedEvent(
    override val sessionId: String,
) : SessionClosedEvent

internal data class HostRunStartedEvent(
    override val sessionId: String,
    override val runId: String,
) : RunStartedEvent

internal data class HostRunFinishedEvent(
    override val sessionId: String,
    override val runId: String,
    override val error: String? = null,
) : RunFinishedEvent

internal data class HostTurnStartedEvent(
    override val sessionId: String,
    override val turnId: String,
) : TurnStartedEvent

internal data class HostTurnFinishedEvent(
    override val sessionId: String,
    override val turnId: String,
    override val failed: Boolean = false,
) : TurnFinishedEvent

internal data class HostMessageStartedEvent(
    override val sessionId: String,
    override val messageId: String,
    override val role: MessageRole,
) : MessageStartedEvent

internal data class HostMessageDeltaEvent(
    override val sessionId: String,
    override val messageId: String,
    override val delta: String,
) : MessageDeltaEvent

internal data class HostMessageFinishedEvent(
    override val sessionId: String,
    override val messageId: String,
    override val text: String? = null,
) : MessageFinishedEvent

internal data class HostToolExecutionUpdateEvent(
    override val sessionId: String,
    override val callId: String,
    override val toolName: String,
    override val phase: ToolPhase,
    override val partial: PluginToolResult? = null,
) : ToolExecutionUpdateEvent

internal data class HostCompactionStartedEvent(
    override val sessionId: String,
) : CompactionStartedEvent

internal data class HostCompactionFinishedEvent(
    override val sessionId: String,
    override val tokensBefore: Long,
    override val tokensAfter: Long,
) : CompactionFinishedEvent

internal data class HostCompactionFailedEvent(
    override val sessionId: String,
    override val error: String,
) : CompactionFailedEvent

internal data class HostModelChangedEvent(
    override val sessionId: String,
    override val model: String,
) : ModelChangedEvent

internal data class HostThinkingLevelChangedEvent(
    override val sessionId: String,
    override val level: String,
) : ThinkingLevelChangedEvent

internal data class HostRetryScheduledEvent(
    override val sessionId: String,
    override val attempt: Int,
    override val delayMs: Long,
    override val reason: String? = null,
) : RetryScheduledEvent

internal data class HostRetrySucceededEvent(
    override val sessionId: String,
    override val attempt: Int,
) : RetrySucceededEvent

internal data class HostRetryExhaustedEvent(
    override val sessionId: String,
    override val attempts: Int,
    override val error: String,
) : RetryExhaustedEvent

internal data class HostMcpStatusChangedEvent(
    override val server: String,
    override val status: McpServerStatus,
) : McpStatusChangedEvent

internal data class HostMcpToolsChangedEvent(
    override val server: String,
    override val tools: List<String>,
) : McpToolsChangedEvent

internal data class HostBusEventReceived(
    override val event: PluginBusEvent,
) : BusEventReceived

internal data class HostPluginBusEvent(
    override val topic: String,
    override val publisher: String,
    override val payload: kotlinx.serialization.json.JsonObject,
    override val publishedAt: Long,
) : PluginBusEvent
