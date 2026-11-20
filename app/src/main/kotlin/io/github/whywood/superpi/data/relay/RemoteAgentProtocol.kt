package io.github.whywood.superpi.data.relay

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface RemoteAgentEvent {
    data object AgentStart : RemoteAgentEvent

    data class TextDelta(val delta: String) : RemoteAgentEvent

    data class ReasoningDelta(val delta: String) : RemoteAgentEvent

    data class ToolCall(val callId: String, val name: String, val arguments: String) : RemoteAgentEvent

    data class ToolResult(val callId: String, val result: String, val isError: Boolean) : RemoteAgentEvent

    data class Failure(val message: String) : RemoteAgentEvent

    data object AgentEnd : RemoteAgentEvent

    data object AgentSettled : RemoteAgentEvent
}

object RemoteAgentProtocol {
    fun decode(event: JsonObject): RemoteAgentEvent? = when (event.string("type")) {
        "agent_start" -> RemoteAgentEvent.AgentStart
        "message_update" -> decodeMessageUpdate(event)
        "tool_call" -> decodeToolCall(event)
        "tool_result" -> decodeToolResult(event)
        "error" -> RemoteAgentEvent.Failure(event.string("message").orEmpty())
        "agent_end" -> RemoteAgentEvent.AgentEnd
        "agent_settled" -> RemoteAgentEvent.AgentSettled
        else -> null
    }

    private fun decodeMessageUpdate(event: JsonObject): RemoteAgentEvent? {
        val update = event["assistantMessageEvent"] as? JsonObject ?: return null
        val delta = update.string("delta") ?: return null
        return when (update.string("type")) {
            "text_delta" -> RemoteAgentEvent.TextDelta(delta)
            "reasoning_delta" -> RemoteAgentEvent.ReasoningDelta(delta)
            else -> null
        }
    }

    private fun decodeToolCall(event: JsonObject): RemoteAgentEvent? {
        val callId = event.string("callId") ?: return null
        val name = event.string("name") ?: return null
        return RemoteAgentEvent.ToolCall(callId = callId, name = name, arguments = text(event["args"]))
    }

    private fun decodeToolResult(event: JsonObject): RemoteAgentEvent? {
        val callId = event.string("callId") ?: return null
        return RemoteAgentEvent.ToolResult(
            callId = callId,
            result = text(event["result"]),
            isError = event["isError"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun text(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
}
