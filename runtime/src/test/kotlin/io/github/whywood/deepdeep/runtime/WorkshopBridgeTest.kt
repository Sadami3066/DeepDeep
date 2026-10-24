package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopBridgeTest {
    @Test
    fun infoReturnsAppAndCapabilities() = runTest {
        val bridge = Bridge()
        val response = bridge.call("info")

        assertTrue(response.bool("ok"))
        val result = response.getValue("result").jsonObject
        val app = result.getValue("app").jsonObject
        assertEquals("garden", app.text("id"))
        assertEquals("Garden", app.text("name"))
        assertEquals("board", app.text("type"))
        assertEquals("1.0.0", app.text("version"))
        assertEquals(
            listOf("data.kv", "agent.run"),
            result.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun infoCapabilitiesFollowPermissions() = runTest {
        val bridge = Bridge(permissions = setOf(WorkshopPermissions.DATA_KV))
        val result = bridge.call("info").getValue("result").jsonObject

        assertEquals(listOf("data.kv"), result.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun dataRoundTrip() = runTest {
        val bridge = Bridge()
        val value = buildJsonObject { put("text", "buy milk") }

        val setResponse = bridge.call("data.set", buildJsonObject { put("key", "todos"); put("value", value) })
        assertTrue(setResponse.bool("ok"))
        assertTrue(setResponse.getValue("result").jsonPrimitive.boolean)

        val getResponse = bridge.call("data.get", buildJsonObject { put("key", "todos") })
        assertEquals(value, getResponse.getValue("result"))

        bridge.call("data.set", buildJsonObject { put("key", "alpha"); put("value", "one") })
        val keys = bridge.call("data.keys", buildJsonObject {})
        assertEquals(
            listOf("alpha", "todos"),
            keys.getValue("result").jsonArray.map { it.jsonPrimitive.content },
        )

        val removeResponse = bridge.call("data.remove", buildJsonObject { put("key", "todos") })
        assertTrue(removeResponse.getValue("result").jsonPrimitive.boolean)
        assertFalse(bridge.call("data.remove", buildJsonObject { put("key", "todos") }).getValue("result").jsonPrimitive.boolean)

        val missing = bridge.call("data.get", buildJsonObject { put("key", "todos") })
        assertEquals(JsonNull, missing.getValue("result"))
    }

    @Test
    fun getMissingValueReturnsNull() = runTest {
        val bridge = Bridge()
        val response = bridge.call("data.get", buildJsonObject { put("key", "missing") })

        assertTrue(response.bool("ok"))
        assertEquals(JsonNull, response.getValue("result"))
    }

    @Test
    fun dataMethodsRequirePermission() = runTest {
        val bridge = Bridge(permissions = emptySet())

        assertEquals("forbidden", bridge.call("data.get", buildJsonObject { put("key", "x") }).errorCode())
        assertEquals(
            "forbidden",
            bridge.call("data.set", buildJsonObject { put("key", "x"); put("value", 1) }).errorCode(),
        )
        assertEquals("forbidden", bridge.call("data.keys", buildJsonObject {}).errorCode())
    }

    @Test
    fun invalidKeyIsRejected() = runTest {
        val bridge = Bridge()

        assertEquals(
            "data_invalid_key",
            bridge.call("data.get", buildJsonObject { put("key", "Bad Key") }).errorCode(),
        )
        assertEquals(
            "data_invalid_key",
            bridge.call("data.get", buildJsonObject { put("key", "-leading") }).errorCode(),
        )
    }

    @Test
    fun oversizedValueIsRejected() = runTest {
        val bridge = Bridge()
        val oversized = "x".repeat(256 * 1024 + 1)
        val response = bridge.call(
            "data.set",
            buildJsonObject {
                put("key", "blob")
                put("value", oversized)
            },
        )

        assertEquals("data_too_large", response.errorCode())
        assertTrue(bridge.data.values.isEmpty())
    }

    @Test
    fun dataFailuresMapToDataCorrupt() = runTest {
        val bridge = Bridge()
        bridge.data.failure = IllegalStateException("database is closed")
        val response = bridge.call("data.get", buildJsonObject { put("key", "todos") })

        assertEquals("data_corrupt", response.errorCode())
    }

    @Test
    fun agentRunStreamsEventsAndReturnsText() = runTest {
        val agent = FakeAgent()
        val bridge = Bridge(agent = agent)
        val context = buildJsonObject { put("page", "board") }

        val response = bridge.call(
            "agent.run",
            buildJsonObject {
                put("prompt", "  add a header  ")
                put("context", context)
            },
        )

        assertTrue(response.bool("ok"))
        assertEquals("final text", response.getValue("result").jsonObject.text("text"))
        assertEquals("add a header", agent.lastInstruction)
        assertEquals(context, agent.lastContext)
        assertEquals(
            listOf("started", "text", "text", "tool", "finished"),
            bridge.emitted.map { it.text("type") },
        )
        assertTrue(bridge.emitted.all { it.text("event") == "agent" })
        assertEquals("Hel", bridge.emitted[1].text("delta"))
        assertEquals("lo", bridge.emitted[2].text("delta"))
        assertEquals("read", bridge.emitted[3].text("name"))
    }

    @Test
    fun agentRunRequiresRunner() = runTest {
        val bridge = Bridge(agent = null)
        val response = bridge.call("agent.run", buildJsonObject { put("prompt", "hello") })

        assertEquals("forbidden", response.errorCode())
    }

    @Test
    fun agentRunRequiresPermission() = runTest {
        val bridge = Bridge(permissions = emptySet(), agent = FakeAgent())
        val response = bridge.call("agent.run", buildJsonObject { put("prompt", "hello") })

        assertEquals("forbidden", response.errorCode())
    }

    @Test
    fun agentRunRejectsBadPrompts() = runTest {
        val bridge = Bridge(agent = FakeAgent())

        assertEquals("invalid_params", bridge.call("agent.run", buildJsonObject { put("prompt", "   ") }).errorCode())
        assertEquals("invalid_params", bridge.call("agent.run", buildJsonObject {}).errorCode())
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "x".repeat(8001)) }).errorCode(),
        )
    }

    @Test
    fun agentRunRejectsOversizedContext() = runTest {
        val bridge = Bridge(agent = FakeAgent())
        val response = bridge.call(
            "agent.run",
            buildJsonObject {
                put("prompt", "hello")
                put("context", "x".repeat(32 * 1024 + 1))
            },
        )

        assertEquals("invalid_params", response.errorCode())
    }

    @Test
    fun agentFailuresMapToBridgeCodes() = runTest {
        val cases = listOf(
            "busy" to "busy",
            "timeout" to "agent_timeout",
            "agent_cancelled" to "agent_cancelled",
            "model_missing" to "model_missing",
            "agent_failed" to "agent_failed",
            "unexpected" to "agent_failed",
        )
        cases.forEach { (code, expected) ->
            val agent = FakeAgent().apply { failure = WorkshopAgentException(code, "boom") }
            val bridge = Bridge(agent = agent)
            val response = bridge.call("agent.run", buildJsonObject { put("prompt", "hello") })
            assertEquals(expected, response.errorCode())
        }
    }

    @Test
    fun agentCancelForwardsToRunner() = runTest {
        val agent = FakeAgent()
        val bridge = Bridge(agent = agent)
        val response = bridge.call("agent.cancel", buildJsonObject {})

        assertTrue(response.bool("ok"))
        assertTrue(response.getValue("result").jsonPrimitive.boolean)
        assertEquals(1, agent.cancelCount)
    }

    @Test
    fun toastIsForwarded() = runTest {
        val bridge = Bridge()
        val response = bridge.call("ui.toast", buildJsonObject { put("text", "saved") })

        assertTrue(response.bool("ok"))
        assertEquals(listOf("saved"), bridge.toastMessages)
        assertEquals("invalid_params", bridge.call("ui.toast", buildJsonObject { put("text", "x".repeat(201)) }).errorCode())
        assertEquals("invalid_params", bridge.call("ui.toast", buildJsonObject {}).errorCode())
    }

    @Test
    fun unknownMethodIsRejected() = runTest {
        val bridge = Bridge()
        assertEquals("unknown_method", bridge.call("workshop.nope", buildJsonObject {}).errorCode())
    }

    @Test
    fun malformedJsonReturnsInvalidRequest() = runTest {
        val bridge = Bridge()
        val response = bridge.callRaw("{not json")

        assertEquals(JsonNull, response.getValue("id"))
        assertFalse(response.bool("ok"))
        assertEquals("invalid_request", response.errorCode())
    }

    @Test
    fun requestWithoutNumberIdIsRejected() = runTest {
        val bridge = Bridge()
        val response = bridge.callRaw("""{"method":"info"}""")

        assertEquals(JsonNull, response.getValue("id"))
        assertEquals("invalid_request", response.errorCode())
    }

    @Test
    fun badParamsAreRejected() = runTest {
        val bridge = Bridge()
        val wrongParams = bridge.callRaw("""{"id":7,"method":"info","params":"nope"}""")

        assertEquals("invalid_params", wrongParams.errorCode())
        assertEquals("invalid_params", bridge.call("data.get", buildJsonObject {}).errorCode())
        assertEquals("invalid_params", bridge.call("data.set", buildJsonObject { put("key", "x") }).errorCode())
    }

    private class Bridge(
        permissions: Set<String> = setOf(WorkshopPermissions.DATA_KV, WorkshopPermissions.AGENT_RUN),
        agent: WorkshopAgentRunner? = null,
    ) {
        val data = FakeData()
        val toastMessages = mutableListOf<String>()
        val emitted = mutableListOf<JsonObject>()
        private val router = WorkshopBridgeRouter(
            WorkshopManifest(
                id = "garden",
                name = "Garden",
                type = WorkshopType.BOARD,
                permissions = permissions,
            ),
            data,
            agent,
            { toastMessages.add(it) },
        )

        suspend fun call(method: String, params: JsonObject? = null): JsonObject {
            val request = buildJsonObject {
                put("id", 1)
                put("method", method)
                if (params != null) put("params", params)
            }
            return parse(router.handle(request.toString(), { emitted.add(it) }))
        }

        suspend fun callRaw(raw: String): JsonObject = parse(router.handle(raw, { emitted.add(it) }))

        private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    }

    private class FakeData : WorkshopData {
        val values = linkedMapOf<String, JsonElement>()
        var failure: Exception? = null

        override suspend fun get(key: String): JsonElement? {
            failure?.let { throw it }
            return values[key]
        }

        override suspend fun set(key: String, value: JsonElement) {
            failure?.let { throw it }
            values[key] = value
        }

        override suspend fun remove(key: String): Boolean {
            failure?.let { throw it }
            return values.remove(key) != null
        }

        override suspend fun keys(): List<String> {
            failure?.let { throw it }
            return values.keys.toList()
        }

        override fun close() {}
    }

    private class FakeAgent : WorkshopAgentRunner {
        var failure: WorkshopAgentException? = null
        var result = "final text"
        var lastInstruction: String? = null
        var lastContext: JsonElement? = null
        var cancelCount = 0

        override suspend fun run(
            instruction: String,
            context: JsonElement?,
            onEvent: (WorkshopAgentEvent) -> Unit,
        ): String {
            lastInstruction = instruction
            lastContext = context
            failure?.let { throw it }
            onEvent(WorkshopAgentEvent.Started)
            onEvent(WorkshopAgentEvent.Text("Hel"))
            onEvent(WorkshopAgentEvent.Text("lo"))
            onEvent(WorkshopAgentEvent.Tool("read"))
            onEvent(WorkshopAgentEvent.Finished)
            return result
        }

        override suspend fun cancel() {
            cancelCount++
        }
    }
}

private fun JsonObject.bool(field: String): Boolean = getValue(field).jsonPrimitive.boolean

private fun JsonObject.text(field: String): String = getValue(field).jsonPrimitive.content

private fun JsonObject.errorCode(): String =
    getValue("error").jsonObject.getValue("code").jsonPrimitive.content
