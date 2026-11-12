package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.agent.AgentMessage
import io.github.whywood.deepdeep.sdk.agent.asAssistantMessage
import io.github.whywood.deepdeep.sdk.agent.asLlmMessage
import io.github.whywood.deepdeep.sdk.ai.AssistantMessage
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ThinkingDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.coding.mcp.McpServerState
import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.MessageRole
import io.github.whywood.deepdeep.plugin.api.ToolPhase
import io.github.whywood.deepdeep.plugin.api.richResult
import io.github.whywood.deepdeep.plugin.api.textResult
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val RUNTIME_CUSTOM_MESSAGE_TOPIC: String = "host.runtime.custom-message"
internal const val HOST_BUS_PUBLISHER: String = "deepdeep.host"

internal class RuntimeEventMapper(
    private val emit: suspend (PluginEvent) -> List<PluginHookFailure>,
    private val ids: PluginEventIds = PluginEventIds(),
) {
    internal constructor(adapter: PluginRuntimeHooksAdapter) : this(adapter::emit, adapter.eventIds)

    suspend fun dispatch(session: PluginHookSession, event: SessionRuntimeEvent): List<PluginHookFailure> =
        emitAll(map(session.sessionId, event.event))

    suspend fun dispatch(sessionId: String, event: SessionRuntimeEvent): List<PluginHookFailure> =
        emitAll(map(sessionId, event.event))

    suspend fun dispatch(sessionId: String, event: RuntimeEvent): List<PluginHookFailure> =
        emitAll(map(sessionId, event))

    suspend fun dispatchSessionEvent(sessionId: String, event: SessionEvent): List<PluginHookFailure> =
        emitAll(mapSessionEvent(sessionId, event))

    fun map(sessionId: String, event: RuntimeEvent): List<PluginEvent> = when (event) {
        is RuntimeEvent.RunStarted -> listOf(HostRunStartedEvent(sessionId, event.runId))
        is RuntimeEvent.RunFinished -> listOf(HostRunFinishedEvent(sessionId, event.runId))
        is RuntimeEvent.RunFailed -> listOf(HostRunFinishedEvent(sessionId, event.runId, event.message))
        is RuntimeEvent.TurnInjected -> injectedMessage(sessionId, event.runId, event.text)
        is RuntimeEvent.UserInputAdmitted -> userMessage(sessionId, event.runId, event.text)
        else -> emptyList()
    }

    fun mapSessionEvent(sessionId: String, event: SessionEvent): List<PluginEvent> = when (event) {
        is SessionEvent.AgentEventReceived -> mapAgentEvent(sessionId, event.event)
        is SessionEvent.ModelChanged -> listOf(
            HostModelChangedEvent(sessionId, "${event.provider}/${event.modelId}"),
            HostThinkingLevelChangedEvent(sessionId, event.thinkingLevel),
        )

        is SessionEvent.CompactionStarted -> listOf(HostCompactionStartedEvent(sessionId))
        is SessionEvent.CompactionFinished -> listOf(
            HostCompactionFinishedEvent(sessionId, event.tokensBefore, event.tokensAfter ?: 0L),
        )

        is SessionEvent.CompactionFailed -> listOf(
            HostCompactionFailedEvent(sessionId, event.errorMessage.orEmpty()),
        )

        is SessionEvent.RetryScheduled -> listOf(
            HostRetryScheduledEvent(sessionId, event.attempt, event.delayMs, event.errorMessage),
        )

        is SessionEvent.RetrySucceeded -> listOf(HostRetrySucceededEvent(sessionId, event.attempt))
        is SessionEvent.RetryExhausted -> listOf(
            HostRetryExhaustedEvent(sessionId, event.attempts, event.errorMessage.orEmpty()),
        )

        is SessionEvent.McpServerStatusChanged -> listOf(
            HostMcpStatusChangedEvent(event.name, event.status.toPluginStatus()),
        )

        is SessionEvent.McpToolsChanged -> listOf(
            HostMcpToolsChangedEvent(event.server, event.added),
        )

        is SessionEvent.CustomMessageAppended -> listOf(customMessageEvent(sessionId, event))
        else -> emptyList()
    }

    private fun mapAgentEvent(sessionId: String, event: AgentEvent): List<PluginEvent> = when (event) {
        AgentEvent.TurnStart -> listOf(HostTurnStartedEvent(sessionId, ids.turnStarted(sessionId)))
        is AgentEvent.TurnEnd -> listOf(
            HostTurnFinishedEvent(
                sessionId = sessionId,
                turnId = ids.turnFinished(sessionId),
                failed = event.message.asAssistantMessage()?.stopReason == StopReason.Error,
            ),
        )

        is AgentEvent.MessageStart -> listOf(
            HostMessageStartedEvent(sessionId, messageId(sessionId, event.message), event.message.role.toPluginRole()),
        )

        is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
            is TextDeltaEvent -> listOf(
                HostMessageDeltaEvent(sessionId, messageId(sessionId, event.message), update.delta),
            )

            is ThinkingDeltaEvent -> listOf(
                HostMessageDeltaEvent(sessionId, messageId(sessionId, event.message), update.delta),
            )

            else -> emptyList()
        }

        is AgentEvent.MessageEnd -> listOf(
            HostMessageFinishedEvent(
                sessionId = sessionId,
                messageId = messageId(sessionId, event.message),
                text = event.message.text(),
            ),
        )

        is AgentEvent.ToolExecutionStart -> listOf(
            HostToolExecutionUpdateEvent(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = ToolPhase.STARTED,
            ),
        )

        is AgentEvent.ToolExecutionUpdate -> listOf(
            HostToolExecutionUpdateEvent(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = ToolPhase.PARTIAL,
                partial = event.partialResult.toPluginToolResult(),
            ),
        )

        is AgentEvent.ToolExecutionEnd -> listOf(
            HostToolExecutionUpdateEvent(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = if (event.isError) ToolPhase.FAILED else ToolPhase.FINISHED,
                partial = event.result.toPluginToolResult().withError(event.isError),
            ),
        )

        else -> emptyList()
    }

    private suspend fun emitAll(events: List<PluginEvent>): List<PluginHookFailure> {
        if (events.isEmpty()) return emptyList()
        val failures = mutableListOf<PluginHookFailure>()
        for (event in events) failures += emit(event)
        return failures
    }

    private fun injectedMessage(sessionId: String, runId: String, text: String): List<PluginEvent> {
        val messageId = "$runId:system"
        return listOf(
            HostMessageStartedEvent(sessionId, messageId, MessageRole.SYSTEM),
            HostMessageFinishedEvent(sessionId, messageId, text),
        )
    }

    private fun userMessage(sessionId: String, runId: String, text: String): List<PluginEvent> {
        val messageId = "$runId:user"
        return listOf(
            HostMessageStartedEvent(sessionId, messageId, MessageRole.USER),
            HostMessageFinishedEvent(sessionId, messageId, text),
        )
    }

    private fun customMessageEvent(
        sessionId: String,
        event: SessionEvent.CustomMessageAppended,
    ): PluginEvent = HostBusEventReceived(
        HostPluginBusEvent(
            topic = RUNTIME_CUSTOM_MESSAGE_TOPIC,
            publisher = HOST_BUS_PUBLISHER,
            payload = buildJsonObject {
                put("sessionId", sessionId)
                put("customType", event.customType)
                put("entryId", event.entryId)
            },
            publishedAt = 0,
        ),
    )

    private fun PluginToolResult.withError(isError: Boolean): PluginToolResult {
        if (!isError || this.isError) return this
        val currentText = text
        return if (currentText != null) {
            textResult(currentText, details, isError = true)
        } else {
            richResult(blocks, details, isError = true)
        }
    }

    private fun messageId(sessionId: String, message: AgentMessage): String =
        "$sessionId:${message.role}:${message.timestamp}"

    private fun String.toPluginRole(): MessageRole = when (this) {
        "assistant" -> MessageRole.ASSISTANT
        "user" -> MessageRole.USER
        "toolResult" -> MessageRole.TOOL
        else -> MessageRole.SYSTEM
    }

    private fun AgentMessage.text(): String? = when (val message = asLlmMessage()) {
        is AssistantMessage -> message.content.filterIsInstance<TextContent>()
            .joinToString("") { it.text }
            .takeIf { it.isNotBlank() }

        is UserMessage -> message.content.asText().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun io.github.whywood.deepdeep.sdk.ai.MessageContent.asText(): String = when (this) {
        is io.github.whywood.deepdeep.sdk.ai.PlainTextContent -> text
        is io.github.whywood.deepdeep.sdk.ai.BlockContent -> blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
    }
}

internal class PluginEventIds {
    private val turnCounts = ConcurrentHashMap<String, AtomicLong>()

    private val currentTurns = ConcurrentHashMap<String, String>()

    fun turnStarted(sessionId: String): String {
        val turnId = "$sessionId:turn:${nextTurnNumber(sessionId)}"
        currentTurns[sessionId] = turnId
        return turnId
    }

    fun turnFinished(sessionId: String): String =
        currentTurns.remove(sessionId) ?: "$sessionId:turn:${nextTurnNumber(sessionId)}"

    private fun nextTurnNumber(sessionId: String): Long =
        turnCounts.computeIfAbsent(sessionId) { AtomicLong() }.incrementAndGet()
}

private fun io.github.whywood.deepdeep.sdk.coding.mcp.McpServerStatus.toPluginStatus(): McpServerStatus = when (state) {
    McpServerState.Connected -> McpServerStatus.OK
    McpServerState.AuthRequired -> McpServerStatus.AUTH_REQUIRED
    McpServerState.Failed -> McpServerStatus.FAILED
    else -> McpServerStatus.UNKNOWN
}
