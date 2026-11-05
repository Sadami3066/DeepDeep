package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.coding.mcp.McpServerState
import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.TurnNotice
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val RUNTIME_CUSTOM_MESSAGE_TOPIC: String = "host.runtime.custom-message"
internal const val HOST_BUS_PUBLISHER: String = "deepdeep.host"

internal class RuntimeEventMapper(
    private val emit: suspend (PluginEvent) -> List<PluginHookFailure>,
    private val includeRuntimeFallbacks: Boolean = true,
) {
    internal constructor(
        adapter: PluginRuntimeHooksAdapter,
        includeRuntimeFallbacks: Boolean = true,
    ) : this(adapter::emit, includeRuntimeFallbacks)

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
        is RuntimeEvent.TextDelta -> listOf(
            PluginEvent.MessageDelta(sessionId, assistantMessageId(event.runId), event.text),
        )

        is RuntimeEvent.ReasoningDelta -> listOf(
            PluginEvent.MessageDelta(sessionId, assistantMessageId(event.runId), event.text),
        )

        is RuntimeEvent.ToolStarted -> listOf(
            PluginEvent.ToolExecutionUpdate(
                sessionId = sessionId,
                callId = event.callId,
                toolName = event.name,
                phase = PluginEvent.ToolPhase.STARTED,
            ),
        )

        is RuntimeEvent.ToolFinished -> listOf(
            PluginEvent.ToolExecutionUpdate(
                sessionId = sessionId,
                callId = event.callId,
                toolName = event.name,
                phase = if (event.isError) PluginEvent.ToolPhase.FAILED else PluginEvent.ToolPhase.FINISHED,
            ),
        )

        is RuntimeEvent.TurnInjected -> systemMessage(sessionId, event.runId, event.text)
        is RuntimeEvent.UserInputAdmitted -> userMessage(sessionId, event.runId, event.text)
        is RuntimeEvent.ActivityChanged -> activityEvents(sessionId, event.activity)
        is NoticeEvent -> noticeEvents(sessionId, event.notice)
        else -> emptyList()
    }

    fun mapSessionEvent(sessionId: String, event: SessionEvent): List<PluginEvent> = when (event) {
        is SessionEvent.ModelChanged -> listOf(
            PluginEvent.ModelChanged(sessionId, "${event.provider}/${event.modelId}"),
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

    private suspend fun emitAll(events: List<PluginEvent>): List<PluginHookFailure> {
        if (events.isEmpty()) return emptyList()
        val failures = mutableListOf<PluginHookFailure>()
        for (event in events) failures += emit(event)
        return failures
    }

    private fun activityEvents(sessionId: String, activity: RuntimeActivity?): List<PluginEvent> {
        if (!includeRuntimeFallbacks || activity == null) return emptyList()
        return when (activity) {
            RuntimeActivity.Compacting -> listOf(PluginEvent.CompactionStarted(sessionId))
            RuntimeActivity.CompactionFailed -> listOf(PluginEvent.CompactionFailed(sessionId, ""))
            is RuntimeActivity.Retrying -> listOf(PluginEvent.RetryScheduled(sessionId, activity.attempt, 0L))
            else -> emptyList()
        }
    }

    private fun noticeEvents(sessionId: String, notice: TurnNotice): List<PluginEvent> {
        if (!includeRuntimeFallbacks) return emptyList()
        return when (notice) {
            is TurnNotice.Compacted -> listOf(
                PluginEvent.CompactionFinished(sessionId, notice.tokensBefore, notice.tokensAfter ?: 0L),
            )

            else -> emptyList()
        }
    }

    private fun systemMessage(sessionId: String, runId: String, text: String): List<PluginEvent> {
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

    private companion object {
        fun assistantMessageId(runId: String): String = "$runId:assistant"
    }
}

private fun dev.pi.coding.mcp.McpServerStatus.toPluginStatus(): McpServerStatus = when (state) {
    McpServerState.Connected -> McpServerStatus.OK
    McpServerState.AuthRequired -> McpServerStatus.AUTH_REQUIRED
    McpServerState.Failed -> McpServerStatus.FAILED
    else -> McpServerStatus.UNKNOWN
}
