package io.github.whywood.superpi.remote

import io.github.whywood.superpi.sdk.agent.AgentEvent
import io.github.whywood.superpi.sdk.agent.asAgentMessage
import io.github.whywood.superpi.sdk.agent.AgentToolResult
import io.github.whywood.superpi.sdk.ai.AssistantMessage
import io.github.whywood.superpi.sdk.ai.Content
import io.github.whywood.superpi.sdk.ai.StopReason
import io.github.whywood.superpi.sdk.ai.TextContent
import io.github.whywood.superpi.sdk.ai.TextDeltaEvent
import io.github.whywood.superpi.sdk.ai.ThinkingContent
import io.github.whywood.superpi.sdk.ai.ThinkingDeltaEvent
import io.github.whywood.superpi.sdk.ai.ToolCall
import io.github.whywood.superpi.sdk.ai.ToolCallStartEvent
import io.github.whywood.superpi.sdk.ai.ToolResultMessage
import io.github.whywood.superpi.sdk.ai.Usage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal sealed interface RemoteBotEvent {
    data object AgentStart : RemoteBotEvent

    data class TextDelta(val delta: String) : RemoteBotEvent

    data class ReasoningDelta(val delta: String) : RemoteBotEvent

    data class ToolCall(val callId: String, val name: String, val args: JsonObject) : RemoteBotEvent

    data class ToolResult(
        val callId: String,
        val result: String,
        val isError: Boolean,
        val details: JsonElement?,
    ) : RemoteBotEvent

    data class Failed(val message: String) : RemoteBotEvent

    data class Ended(val status: String?) : RemoteBotEvent

    data object Settled : RemoteBotEvent

    data class QueueUpdate(val steering: List<String>, val followUp: List<String>) : RemoteBotEvent
}

internal object RemoteEventMapping {
    fun decode(event: JsonObject): RemoteBotEvent? = when (event.string("type")) {
        "agent_start" -> RemoteBotEvent.AgentStart
        "message_update" -> decodeMessageUpdate(event)
        "tool_call" -> decodeToolCall(event)
        "tool_result" -> decodeToolResult(event)
        "error" -> RemoteBotEvent.Failed(event.string("message").orEmpty().ifBlank { "The remote bot failed." })
        "agent_end" -> RemoteBotEvent.Ended(event.string("status"))
        "agent_settled" -> RemoteBotEvent.Settled
        "queue_update" -> RemoteBotEvent.QueueUpdate(
            steering = event.strings("steering"),
            followUp = event.strings("followUp"),
        )

        else -> null
    }

    private fun decodeMessageUpdate(event: JsonObject): RemoteBotEvent? {
        val update = event["assistantMessageEvent"] as? JsonObject ?: return null
        val delta = update.string("delta") ?: return null
        return when (update.string("type")) {
            "text_delta" -> RemoteBotEvent.TextDelta(delta)
            "reasoning_delta" -> RemoteBotEvent.ReasoningDelta(delta)
            else -> null
        }
    }

    private fun decodeToolCall(event: JsonObject): RemoteBotEvent.ToolCall? {
        val callId = event.string("callId") ?: return null
        val name = event.string("name") ?: return null
        return RemoteBotEvent.ToolCall(
            callId = callId,
            name = name,
            args = event["args"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    private fun decodeToolResult(event: JsonObject): RemoteBotEvent.ToolResult? {
        val callId = event.string("callId") ?: return null
        return RemoteBotEvent.ToolResult(
            callId = callId,
            result = text(event["result"]),
            isError = (event["isError"] as? JsonPrimitive)?.booleanOrNull ?: false,
            details = event["details"]?.takeUnless { it is kotlinx.serialization.json.JsonNull },
        )
    }

    private fun text(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }
}

internal data class RemoteToolCallRecord(
    val callId: String,
    val name: String,
    val args: JsonObject,
)

internal data class RemoteToolResultRecord(
    val callId: String,
    val toolName: String,
    val result: String,
    val isError: Boolean,
    val details: JsonElement?,
    val timestamp: Long,
)

internal class RemoteTurnState(
    private val modelName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val textBuilder = StringBuilder()
    private val reasoningBuilder = StringBuilder()
    private val calls = LinkedHashMap<String, RemoteToolCallRecord>()
    private val results = mutableListOf<RemoteToolResultRecord>()
    private val started = mutableSetOf<String>()
    private val signaled = mutableSetOf<String>()
    private var failureValue: String? = null
    private var endStatusValue: String? = null

    val failure: String? get() = synchronized(lock) { failureValue }

    val endStatus: String? get() = synchronized(lock) { endStatusValue }

    val text: String get() = synchronized(lock) { textBuilder.toString() }

    val reasoning: String get() = synchronized(lock) { reasoningBuilder.toString() }

    val toolResults: List<RemoteToolResultRecord> get() = synchronized(lock) { results.toList() }

    val hasContent: Boolean
        get() = synchronized(lock) {
            textBuilder.isNotEmpty() || reasoningBuilder.isNotEmpty() || calls.isNotEmpty() || results.isNotEmpty()
        }

    fun runningToolNames(): List<String> = synchronized(lock) {
        val finished = results.mapTo(mutableSetOf()) { it.callId }
        calls.values.filterNot { it.callId in finished }.map { it.name }.filter { it.isNotEmpty() }
    }

    fun consume(event: RemoteBotEvent): List<AgentEvent> = synchronized(lock) { consumeLocked(event) }

    fun assistantMessage(stopReason: StopReason, errorMessage: String?): AssistantMessage = synchronized(lock) {
        AssistantMessage(
            content = contentLocked(),
            api = "",
            provider = REMOTE_PROVIDER,
            model = modelName,
            usage = Usage.empty(),
            stopReason = stopReason,
            errorMessage = errorMessage,
            timestamp = clock(),
        )
    }

    fun toolResultMessages(): List<ToolResultMessage> = synchronized(lock) {
        results.map { result ->
            ToolResultMessage(
                toolCallId = result.callId,
                toolName = result.toolName,
                content = listOf(TextContent(result.result)),
                details = result.details,
                isError = result.isError,
                timestamp = result.timestamp,
            )
        }
    }

    private fun consumeLocked(event: RemoteBotEvent): List<AgentEvent> = when (event) {
        RemoteBotEvent.AgentStart -> listOf(AgentEvent.AgentStart)
        is RemoteBotEvent.TextDelta -> {
            textBuilder.append(event.delta)
            val partial = partialMessage()
            listOf(
                AgentEvent.MessageUpdate(
                    message = partial.asAgentMessage(),
                    assistantMessageEvent = TextDeltaEvent(
                        contentIndex = textIndexLocked(),
                        delta = event.delta,
                        partial = partial,
                    ),
                ),
            )
        }

        is RemoteBotEvent.ReasoningDelta -> {
            reasoningBuilder.append(event.delta)
            val partial = partialMessage()
            listOf(
                AgentEvent.MessageUpdate(
                    message = partial.asAgentMessage(),
                    assistantMessageEvent = ThinkingDeltaEvent(
                        contentIndex = THINKING_INDEX,
                        delta = event.delta,
                        partial = partial,
                    ),
                ),
            )
        }

        is RemoteBotEvent.ToolCall -> onToolCallLocked(event)
        is RemoteBotEvent.ToolResult -> onToolResultLocked(event)
        is RemoteBotEvent.Failed -> {
            failureValue = event.message
            emptyList()
        }

        is RemoteBotEvent.Ended -> {
            endStatusValue = event.status
            emptyList()
        }

        RemoteBotEvent.Settled -> emptyList()
        is RemoteBotEvent.QueueUpdate -> emptyList()
    }

    private fun onToolCallLocked(event: RemoteBotEvent.ToolCall): List<AgentEvent> {
        val existing = calls[event.callId]
        val record = RemoteToolCallRecord(
            callId = event.callId,
            name = event.name.ifBlank { existing?.name.orEmpty() },
            args = if (event.args.isNotEmpty()) event.args else existing?.args ?: JsonObject(emptyMap()),
        )
        calls[event.callId] = record
        if (event.args.isNotEmpty()) {
            if (!started.add(event.callId)) return emptyList()
            return listOf(AgentEvent.ToolExecutionStart(event.callId, record.name, record.args))
        }
        if (event.callId in started || !signaled.add(event.callId)) return emptyList()
        val content = contentLocked()
        val index = content.indexOfFirst { it is ToolCall && it.id == event.callId }
        val message = partialMessage(content)
        return listOf(
            AgentEvent.MessageUpdate(
                message = message.asAgentMessage(),
                assistantMessageEvent = ToolCallStartEvent(contentIndex = index, partial = message),
            ),
        )
    }

    private fun onToolResultLocked(event: RemoteBotEvent.ToolResult): List<AgentEvent> {
        val record = calls[event.callId] ?: RemoteToolCallRecord(
            callId = event.callId,
            name = "",
            args = JsonObject(emptyMap()),
        ).also { calls[event.callId] = it }
        results += RemoteToolResultRecord(
            callId = event.callId,
            toolName = record.name,
            result = event.result,
            isError = event.isError,
            details = event.details,
            timestamp = clock(),
        )
        val events = mutableListOf<AgentEvent>()
        if (started.add(event.callId)) {
            events += AgentEvent.ToolExecutionStart(event.callId, record.name, record.args)
        }
        events += AgentEvent.ToolExecutionEnd(
            toolCallId = event.callId,
            toolName = record.name,
            result = AgentToolResult(
                content = listOf(TextContent(event.result)),
                details = event.details,
                isError = event.isError,
            ),
            isError = event.isError,
        )
        return events
    }

    private fun textIndexLocked(): Int = if (reasoningBuilder.isNotEmpty()) THINKING_INDEX + 1 else 0

    private fun partialMessage(content: MutableList<Content> = contentLocked()): AssistantMessage = AssistantMessage(
        content = content,
        api = "",
        provider = REMOTE_PROVIDER,
        model = modelName,
        usage = Usage.empty(),
        stopReason = StopReason.Stop,
        timestamp = clock(),
    )

    private fun contentLocked(): MutableList<Content> {
        val blocks = mutableListOf<Content>()
        if (reasoningBuilder.isNotEmpty()) blocks += ThinkingContent(reasoningBuilder.toString())
        if (textBuilder.isNotEmpty()) blocks += TextContent(textBuilder.toString())
        calls.values.forEach { call ->
            blocks += ToolCall(id = call.callId, name = call.name, arguments = call.args)
        }
        return blocks
    }

    companion object {
        const val REMOTE_PROVIDER = "remote"
        private const val THINKING_INDEX = 0
    }
}

private fun JsonObject.string(key: String): String? =
    ((this[key] as? JsonPrimitive)?.contentOrNull)?.takeIf { it.isNotEmpty() }

private fun JsonObject.strings(key: String): List<String> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
}
