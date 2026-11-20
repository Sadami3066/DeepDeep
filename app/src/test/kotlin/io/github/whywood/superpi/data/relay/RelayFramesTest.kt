package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.RuntimeEvent
import io.github.whywood.superpi.runtime.RunStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayFramesTest {
    @Test
    fun parsesHelloOkWithDevicesAndBots() {
        val frame = RelayFrames.parse(
            """
            {"type":"hello_ok","accountId":"acct","memoryVersion":4,"devices":[
              {"deviceId":"dev-1","name":"Desktop","platform":"desktop","online":true,"lastSeen":10,
               "bots":[{"id":"pi","name":"PI"}]}
            ]}
            """.trimIndent(),
        ) as RelayServerFrame.HelloOk

        assertEquals("acct", frame.accountId)
        assertEquals(4L, frame.memoryVersion)
        val device = frame.devices.single()
        assertEquals("Desktop", device.name)
        assertTrue(device.online)
        assertEquals("PI", device.bots.single().name)
    }

    @Test
    fun parsesPresence() {
        val frame = RelayFrames.parse(
            """{"type":"presence","deviceId":"dev-2","online":false,"lastSeen":99}""",
        ) as RelayServerFrame.Presence

        assertEquals("dev-2", frame.deviceId)
        assertEquals(false, frame.online)
        assertEquals(99L, frame.lastSeen)
    }

    @Test
    fun parsesRouteAndResults() {
        val route = RelayFrames.parse(
            """{"type":"route","id":"r1","from":"dev-2","method":"bot.prompt","params":{"botId":"pi","text":"hi"}}""",
        ) as RelayServerFrame.Route
        assertEquals("dev-2", route.from)
        assertEquals("pi", route.params.getValue("botId").jsonPrimitive.content)

        val ok = RelayFrames.parse("""{"type":"route_ok","id":"r1","result":{"sessionId":"s1"}}""") as RelayServerFrame.RouteOk
        assertEquals("s1", ok.result?.getValue("sessionId")?.jsonPrimitive?.content)

        val failure = RelayFrames.parse(
            """{"type":"route_error","id":"r1","code":"device_offline","message":"offline"}""",
        ) as RelayServerFrame.RouteError
        assertEquals("device_offline", failure.code)

        val event = RelayFrames.parse(
            """{"type":"route_event","id":"r1","seq":3,"event":{"type":"text_delta","delta":"hi"}}""",
        ) as RelayServerFrame.RouteEvent
        assertEquals(3L, event.seq)
        assertEquals("text_delta", event.event.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun parsesMemoryChangedAndPong() {
        val changed = RelayFrames.parse("""{"type":"memory_changed","version":7}""") as RelayServerFrame.MemoryChanged
        assertEquals(7L, changed.version)
        assertEquals(RelayServerFrame.Pong, RelayFrames.parse("""{"type":"pong"}"""))
        assertEquals(RelayServerFrame.Unknown("later"), RelayFrames.parse("""{"type":"later"}"""))
    }

    @Test
    fun encodesHelloAndRoutes() {
        val hello = RelayFrames.hello(
            deviceId = "dev-1",
            platform = "android",
            appVersion = "2.0.1",
            bots = listOf(RelayBot("bot-1", "Bot one")),
        )
        assertEquals("hello", hello.getValue("type").jsonPrimitive.content)
        assertEquals("android", hello.getValue("platform").jsonPrimitive.content)
        val bot = hello.getValue("bots").let { it as kotlinx.serialization.json.JsonArray }.single().jsonObject
        assertEquals("bot-1", bot.getValue("id").jsonPrimitive.content)

        val route = RelayFrames.route("r1", "dev-2", "bot.prompt", buildJsonObject { put("text", "hi") })
        assertEquals("bot.prompt", route.getValue("method").jsonPrimitive.content)
        assertEquals("r1", route.getValue("id").jsonPrimitive.content)
    }
}

class RelayEventMapperTest {
    @Test
    fun mapsStreamingTextAndReasoning() {
        val text = RelayEventMapper.map(RuntimeEvent.TextDelta("run", "hello")).single()
        assertEquals("message_update", text.string("type"))
        val delta = text.getValue("assistantMessageEvent") as JsonObject
        assertEquals("text_delta", delta.string("type"))
        assertEquals("hello", delta.string("delta"))

        val reasoning = RelayEventMapper.map(RuntimeEvent.ReasoningDelta("run", "think")).single()
        val reasoningDelta = reasoning.getValue("assistantMessageEvent") as JsonObject
        assertEquals("reasoning_delta", reasoningDelta.string("type"))
    }

    @Test
    fun mapsToolsAndRunEnd() {
        val call = RelayEventMapper.map(
            RuntimeEvent.ToolStarted(
                runId = "run",
                callId = "call-1",
                name = "read",
                arguments = mapOf("path" to "a.txt"),
                startedAt = 1L,
            ),
        ).single()
        assertEquals("tool_call", call.string("type"))
        assertEquals("call-1", call.string("callId"))
        assertEquals("a.txt", (call.getValue("args") as JsonObject).string("path"))

        val result = RelayEventMapper.map(
            RuntimeEvent.ToolFinished(
                runId = "run",
                callId = "call-1",
                name = "read",
                result = "done",
                isError = false,
                durationMs = 3L,
            ),
        ).single()
        assertEquals("tool_result", result.string("type"))
        assertEquals("done", result.string("result"))
        assertEquals(false, result.getValue("isError").jsonPrimitive.boolean)

        val finished = RelayEventMapper.map(RuntimeEvent.RunFinished("run", "ok", RunStatus.DONE, 2L))
        assertEquals(2, finished.size)
        assertEquals("agent_end", finished.first().string("type"))
        assertEquals("agent_settled", finished.last().string("type"))
    }

    @Test
    fun mapsFailuresAndQueue() {
        val failed = RelayEventMapper.map(RuntimeEvent.RunFailed("run", "boom"))
        assertEquals("error", failed.first().string("type"))
        assertEquals("boom", failed.first().string("message"))
        assertEquals("agent_settled", failed.last().string("type"))

        val queue = RelayEventMapper.map(RuntimeEvent.QueueChanged("run", listOf("a"), listOf("b"))).single()
        assertEquals("queue_update", queue.string("type"))
        assertEquals("a", (queue.getValue("steering") as kotlinx.serialization.json.JsonArray).single().jsonPrimitive.content)
    }

    @Test
    fun ignoresUiOnlyEvents() {
        assertTrue(RelayEventMapper.map(RuntimeEvent.SessionListChanged()).isEmpty())
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
