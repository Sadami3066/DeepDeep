package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentEvent
import dev.pi.agent.AgentMessage
import dev.pi.agent.asAssistantMessage
import dev.pi.agent.asLlmMessage
import dev.pi.ai.AssistantMessage
import dev.pi.ai.StopReason
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.TextContent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.ai.UserMessage
import dev.pi.coding.mcp.McpServerState
import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
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
        is RuntimeEvent.RunStarted -> listOf(PluginEvent.RunStarted(sessionId, event.runId))
        is RuntimeEvent.RunFinished -> listOf(PluginEvent.RunFinished(sessionId, event.runId))
        is RuntimeEvent.RunFailed -> listOf(PluginEvent.RunFinished(sessionId, event.runId, event.message))
        is RuntimeEvent.TurnInjected -> injectedMessage(sessionId, event.runId, event.text)
        is RuntimeEvent.UserInputAdmitted -> userMessage(sessionId, event.runId, event.text)
        else -> emptyList()
    }

    fun mapSessionEvent(sessionId: String, event: SessionEvent): List<PluginEvent> = when (event) {
        is SessionEvent.AgentEventReceived -> mapAgentEvent(sessionId, event.event)
        is SessionEvent.ModelChanged -> listOf(
            PluginEvent.ModelChanged(sessionId, "${event.provider}/${event.modelId}"),
            PluginEvent.ThinkingLevelChanged(sessionId, event.thinkingLevel),
        )

        is SessionEvent.CompactionStarted -> listOf(PluginEvent.CompactionStarted(sessionId))
        is SessionEvent.CompactionFinished -> listOf(
            PluginEvent.CompactionFinished(sessionId, event.tokensBefore, event.tokensAfter ?: 0L),
        )

        is SessionEvent.CompactionFailed -> listOf(
            PluginEvent.CompactionFailed(sessionId, event.errorMessage.orEmpty()),
        )

        is SessionEvent.RetryScheduled -> listOf(
            PluginEvent.RetryScheduled(sessionId, event.attempt, event.delayMs, event.errorMessage),
        )

        is SessionEvent.RetrySucceeded -> listOf(PluginEvent.RetrySucceeded(sessionId, event.attempt))
        is SessionEvent.RetryExhausted -> listOf(
            PluginEvent.RetryExhausted(sessionId, event.attempts, event.errorMessage.orEmpty()),
        )

        is SessionEvent.McpServerStatusChanged -> listOf(
            PluginEvent.McpStatusChanged(event.name, event.status.toPluginStatus()),
        )

        is SessionEvent.McpToolsChanged -> listOf(
            PluginEvent.McpToolsChanged(event.server, event.added),
        )

        is SessionEvent.CustomMessageAppended -> listOf(customMessageEvent(sessionId, event))
        else -> emptyList()
    }

    private fun mapAgentEvent(sessionId: String, event: AgentEvent): List<PluginEvent> = when (event) {
        AgentEvent.TurnStart -> listOf(PluginEvent.TurnStarted(sessionId, ids.turnStarted(sessionId)))
        is AgentEvent.TurnEnd -> listOf(
            PluginEvent.TurnFinished(
                sessionId = sessionId,
                turnId = ids.turnFinished(sessionId),
                failed = event.message.asAssistantMessage()?.stopReason == StopReason.Error,
            ),
        )

        is AgentEvent.MessageStart -> listOf(
            PluginEvent.MessageStarted(sessionId, messageId(sessionId, event.message), event.message.role.toPluginRole()),
        )

        is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
            is TextDeltaEvent -> listOf(
                PluginEvent.MessageDelta(sessionId, messageId(sessionId, event.message), update.delta),
            )

            is ThinkingDeltaEvent -> listOf(
                PluginEvent.MessageDelta(sessionId, messageId(sessionId, event.message), update.delta),
            )

            else -> emptyList()
        }

        is AgentEvent.MessageEnd -> listOf(
            PluginEvent.MessageFinished(
                sessionId = sessionId,
                messageId = messageId(sessionId, event.message),
                text = event.message.text(),
            ),
        )

        is AgentEvent.ToolExecutionStart -> listOf(
            PluginEvent.ToolExecutionUpdate(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = PluginEvent.ToolPhase.STARTED,
            ),
        )

        is AgentEvent.ToolExecutionUpdate -> listOf(
            PluginEvent.ToolExecutionUpdate(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = PluginEvent.ToolPhase.PARTIAL,
                partial = event.partialResult.toPluginToolResult(),
            ),
        )

        is AgentEvent.ToolExecutionEnd -> listOf(
            PluginEvent.ToolExecutionUpdate(
                sessionId = sessionId,
                callId = event.toolCallId,
                toolName = event.toolName,
                phase = if (event.isError) PluginEvent.ToolPhase.FAILED else PluginEvent.ToolPhase.FINISHED,
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
            PluginEvent.MessageStarted(sessionId, messageId, PluginEvent.MessageRole.SYSTEM),
            PluginEvent.MessageFinished(sessionId, messageId, text),
        )
    }

    private fun userMessage(sessionId: String, runId: String, text: String): List<PluginEvent> {
        val messageId = "$runId:user"
        return listOf(
            PluginEvent.MessageStarted(sessionId, messageId, PluginEvent.MessageRole.USER),
            PluginEvent.MessageFinished(sessionId, messageId, text),
        )
    }

    private fun customMessageEvent(
        sessionId: String,
        event: SessionEvent.CustomMessageAppended,
    ): PluginEvent = PluginEvent.BusEvent(
        PluginBusEvent(
            topic = RUNTIME_CUSTOM_MESSAGE_TOPIC,
            publisher = HOST_BUS_PUBLISHER,
            payload = buildJsonObject {
                put("sessionId", sessionId)
                put("customType", event.customType)
                put("entryId", event.entryId)
            },
        ),
    )

    private fun PluginToolResult.withError(isError: Boolean): PluginToolResult =
        if (isError && !this.isError) {
            when (this) {
                is PluginToolResult.Text -> copy(isError = true)
                is PluginToolResult.Rich -> copy(isError = true)
            }
        } else {
            this
        }

    private fun messageId(sessionId: String, message: AgentMessage): String =
        "$sessionId:${message.role}:${message.timestamp}"

    private fun String.toPluginRole(): PluginEvent.MessageRole = when (this) {
        "assistant" -> PluginEvent.MessageRole.ASSISTANT
        "user" -> PluginEvent.MessageRole.USER
        "toolResult" -> PluginEvent.MessageRole.TOOL
        else -> PluginEvent.MessageRole.SYSTEM
    }

    private fun AgentMessage.text(): String? = when (val message = asLlmMessage()) {
        is AssistantMessage -> message.content.filterIsInstance<TextContent>()
            .joinToString("") { it.text }
            .takeIf { it.isNotBlank() }

        is UserMessage -> message.content.asText().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun dev.pi.ai.MessageContent.asText(): String = when (this) {
        is dev.pi.ai.PlainTextContent -> text
        is dev.pi.ai.BlockContent -> blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
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

private fun dev.pi.coding.mcp.McpServerStatus.toPluginStatus(): McpServerStatus = when (state) {
    McpServerState.Connected -> McpServerStatus.OK
    McpServerState.AuthRequired -> McpServerStatus.AUTH_REQUIRED
    McpServerState.Failed -> McpServerStatus.FAILED
    else -> McpServerStatus.UNKNOWN
}
