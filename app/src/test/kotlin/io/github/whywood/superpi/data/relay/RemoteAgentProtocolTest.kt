package io.github.whywood.superpi.data.relay

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteAgentProtocolTest {
    @Test
    fun decodesTextDelta() {
        val event = messageUpdate("text_delta", "hello")

        assertEquals(RemoteAgentEvent.TextDelta("hello"), RemoteAgentProtocol.decode(event))
    }

    @Test
    fun decodesReasoningDelta() {
        val event = messageUpdate("reasoning_delta", "thinking")

        assertEquals(RemoteAgentEvent.ReasoningDelta("thinking"), RemoteAgentProtocol.decode(event))
    }

    @Test
    fun ignoresUnknownMessageUpdate() {
        assertNull(RemoteAgentProtocol.decode(messageUpdate("image_delta", "x")))
        assertNull(RemoteAgentProtocol.decode(buildJsonObject { put("type", "message_update") }))
    }

    @Test
    fun decodesToolCallWithArguments() {
        val event = buildJsonObject {
            put("type", "tool_call")
            put("callId", "call-1")
            put("name", "shell")
            put("args", buildJsonObject { put("command", "ls") })
        }

        val decoded = RemoteAgentProtocol.decode(event) as RemoteAgentEvent.ToolCall

        assertEquals("call-1", decoded.callId)
        assertEquals("shell", decoded.name)
        assertEquals(true, decoded.arguments.contains("\"command\":\"ls\""))
    }

    @Test
    fun decodesToolCallWithoutArguments() {
        val event = buildJsonObject {
            put("type", "tool_call")
            put("callId", "call-2")
            put("name", "present")
        }

        val decoded = RemoteAgentProtocol.decode(event) as RemoteAgentEvent.ToolCall

        assertEquals("", decoded.arguments)
    }

    @Test
    fun decodesToolResult() {
        val event = buildJsonObject {
            put("type", "tool_result")
            put("callId", "call-1")
            put("result", "done")
            put("isError", true)
        }

        assertEquals(
            RemoteAgentEvent.ToolResult(callId = "call-1", result = "done", isError = true),
            RemoteAgentProtocol.decode(event),
        )
    }

    @Test
    fun decodesFailureAndRunBoundaries() {
        val failure = buildJsonObject {
            put("type", "error")
            put("message", "model failed")
        }
        assertEquals(RemoteAgentEvent.Failure("model failed"), RemoteAgentProtocol.decode(failure))
        assertEquals(RemoteAgentEvent.AgentStart, RemoteAgentProtocol.decode(buildJsonObject { put("type", "agent_start") }))
        assertEquals(RemoteAgentEvent.AgentEnd, RemoteAgentProtocol.decode(buildJsonObject { put("type", "agent_end") }))
        assertEquals(
            RemoteAgentEvent.AgentSettled,
            RemoteAgentProtocol.decode(buildJsonObject { put("type", "agent_settled") }),
        )
    }

    @Test
    fun ignoresUnrelatedEvents() {
        assertNull(RemoteAgentProtocol.decode(buildJsonObject { put("type", "queue_update") }))
        assertNull(RemoteAgentProtocol.decode(buildJsonObject { put("type", "unknown") }))
        assertNull(RemoteAgentProtocol.decode(JsonObject(emptyMap())))
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
}
