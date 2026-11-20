package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.RuntimeEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

internal object RelayEventMapper {
    fun map(event: RuntimeEvent): List<JsonObject> = when (event) {
        is RuntimeEvent.RunStarted -> listOf(buildJsonObject { put("type", "agent_start") })
        is RuntimeEvent.TextDelta -> listOf(messageUpdate("text_delta", event.text))
        is RuntimeEvent.ReasoningDelta -> listOf(messageUpdate("reasoning_delta", event.text))
        is RuntimeEvent.ToolStarted -> listOf(
            toolCall(event.callId, event.name, event.rawArguments ?: argumentsJson(event.arguments)),
        )

        is RuntimeEvent.ToolCallStarted -> listOf(
            buildJsonObject {
                put("type", "tool_call")
                put("callId", event.callId)
                put("name", event.name)
            },
        )

        is RuntimeEvent.ToolFinished -> listOf(
            buildJsonObject {
                put("type", "tool_result")
                put("callId", event.callId)
                put("result", event.result)
                put("isError", event.isError)
            },
        )

        is RuntimeEvent.RunFinished -> listOf(
            buildJsonObject {
                put("type", "agent_end")
                put("status", event.status.name.lowercase())
            },
            buildJsonObject { put("type", "agent_settled") },
        )

        is RuntimeEvent.RunFailed -> listOf(
            buildJsonObject {
                put("type", "error")
                put("message", event.message)
            },
            buildJsonObject { put("type", "agent_settled") },
        )

        is RuntimeEvent.QueueChanged -> listOf(
            buildJsonObject {
                put("type", "queue_update")
                put("steering", strings(event.steering))
                put("followUp", strings(event.followUp))
            },
        )

        else -> emptyList()
    }

    private fun messageUpdate(type: String, delta: String): JsonObject = buildJsonObject {
        put("type", "message_update")
        put(
            "assistantMessageEvent",
            buildJsonObject {
                put("type", type)
                put("delta", delta)
            },
        )
    }

    private fun toolCall(callId: String, name: String, args: JsonObject?): JsonObject = buildJsonObject {
        put("type", "tool_call")
        put("callId", callId)
        put("name", name)
        if (args != null) put("args", args)
    }

    private fun argumentsJson(arguments: Map<String, String>): JsonObject = buildJsonObject {
        arguments.forEach { (key, value) -> put(key, value) }
    }

    private fun strings(values: List<String>) = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }
}
