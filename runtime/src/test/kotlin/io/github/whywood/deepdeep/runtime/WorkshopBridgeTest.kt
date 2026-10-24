package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopBridgeTest {
    @Test
    fun infoReturnsTheAppIdentity() = runTest {
        val bridge = Bridge()
        val response = bridge.call("info")

        assertTrue(response.bool("ok"))
        val result = response.getValue("result").jsonObject
        val app = result.getValue("app").jsonObject
        assertEquals("garden", app.text("id"))
        assertEquals("Garden", app.text("name"))
        assertEquals("🌱", app.text("icon"))
        assertEquals("1.0.0", app.text("version"))
        assertEquals(setOf("id", "name", "icon", "version"), app.keys)
        assertFalse(result.containsKey("capabilities"))
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
        assertFalse(
            bridge.call("data.remove", buildJsonObject { put("key", "todos") })
                .getValue("result").jsonPrimitive.boolean,
        )

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
    fun invalidKeyIsRejected() = runTest {
        val bridge = Bridge()

        assertEquals(
            "data_invalid_key",
            bridge.call("data.get", buildJsonObject { put("key", "Bad Key") }).errorCode(),
        )
        assertEquals(
            "data_invalid_key",
            bridge.call("data.set", buildJsonObject { put("key", "-leading"); put("value", 1) }).errorCode(),
        )
        assertEquals(
            "data_invalid_key",
            bridge.call("data.remove", buildJsonObject { put("key", "a".repeat(65)) }).errorCode(),
        )
    }

    @Test
    fun oversizedValueIsRejected() = runTest {
        val bridge = Bridge()
        val response = bridge.call(
            "data.set",
            buildJsonObject {
                put("key", "blob")
                put("value", "x".repeat(256 * 1024 + 1))
            },
        )

        assertEquals("data_too_large", response.errorCode())
        assertTrue(bridge.data.values.isEmpty())
    }

    @Test
    fun dataFailuresMapToDataCorrupt() = runTest {
        val bridge = Bridge()
        bridge.data.failure = IllegalStateException("database is closed")

        assertEquals("data_corrupt", bridge.call("data.get", buildJsonObject { put("key", "todos") }).errorCode())
        assertEquals("data_corrupt", bridge.call("data.keys", buildJsonObject {}).errorCode())
    }

    @Test
    fun toastIsForwarded() = runTest {
        val bridge = Bridge()
        val response = bridge.call("ui.toast", buildJsonObject { put("text", "saved") })

        assertTrue(response.bool("ok"))
        assertTrue(response.getValue("result").jsonPrimitive.boolean)
        assertEquals(listOf("saved"), bridge.toastMessages)
        assertEquals("invalid_params", bridge.call("ui.toast", buildJsonObject { put("text", "x".repeat(201)) }).errorCode())
        assertEquals("invalid_params", bridge.call("ui.toast", buildJsonObject {}).errorCode())
    }

    @Test
    fun unknownMethodIsRejected() = runTest {
        val bridge = Bridge()
        assertEquals("unknown_method", bridge.call("workshop.unknown", buildJsonObject {}).errorCode())
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

    @Test
    fun agentModelsReturnConfiguredModels() = runTest {
        val bridge = Bridge(FakeGateway())
        val response = bridge.call("agent.models")

        assertTrue(response.bool("ok"))
        val models = response.getValue("result").jsonObject.getValue("models").jsonArray
        assertEquals(1, models.size)
        val model = models[0].jsonObject
        assertEquals("deepseek", model.text("providerId"))
        assertEquals("deepseek-v4.1-flash", model.text("modelId"))
        assertEquals("DeepSeek V4.1 Flash", model.text("label"))
        assertEquals(
            listOf("low", "high"),
            model.getValue("thinkingLevels").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun agentCapabilitiesReturnToolsAndSkills() = runTest {
        val bridge = Bridge(FakeGateway())
        val response = bridge.call("agent.capabilities")

        assertTrue(response.bool("ok"))
        val result = response.getValue("result").jsonObject
        val tools = result.getValue("tools").jsonArray
        assertEquals(1, tools.size)
        assertEquals("web_search", tools[0].jsonObject.text("name"))
        assertEquals("Search the web", tools[0].jsonObject.text("description"))
        val skills = result.getValue("skills").jsonArray
        assertEquals(1, skills.size)
        assertEquals("workshop", skills[0].jsonObject.text("name"))
        assertEquals("Build mini apps", skills[0].jsonObject.text("description"))
    }

    @Test
    fun agentRunPassesValidatedRequestAndReturnsText() = runTest {
        val gateway = FakeGateway()
        val bridge = Bridge(gateway)
        val response = bridge.call(
            "agent.run",
            buildJsonObject {
                put("prompt", "  summarize this  ")
                put("providerId", "deepseek")
                put("modelId", "deepseek-v4.1-flash")
                put("thinkingLevel", "high")
                put("systemPrompt", "be brief")
                put("maxTurns", 8)
                put("tools", buildJsonArray { add(JsonPrimitive("web_search")); add(JsonPrimitive("web_fetch")) })
                put("skills", buildJsonArray { add(JsonPrimitive("workshop")) })
            },
        )

        assertTrue(response.bool("ok"))
        assertEquals("final text", response.getValue("result").jsonObject.text("text"))
        val request = gateway.requests.single()
        assertEquals("summarize this", request.prompt)
        assertEquals("deepseek", request.providerId)
        assertEquals("deepseek-v4.1-flash", request.modelId)
        assertEquals("high", request.thinkingLevel)
        assertEquals("be brief", request.systemPrompt)
        assertEquals(8, request.maxTurns)
        assertEquals(listOf("web_search", "web_fetch"), request.tools)
        assertEquals(listOf("workshop"), request.skills)
    }

    @Test
    fun agentRunAllowsOmittedAndEmptyOptionalFields() = runTest {
        val gateway = FakeGateway()
        val bridge = Bridge(gateway)

        bridge.call("agent.run", buildJsonObject { put("prompt", "hello") })
        val minimal = gateway.requests.last()
        assertEquals("hello", minimal.prompt)
        assertNull(minimal.providerId)
        assertNull(minimal.modelId)
        assertNull(minimal.thinkingLevel)
        assertNull(minimal.systemPrompt)
        assertNull(minimal.maxTurns)
        assertNull(minimal.tools)
        assertNull(minimal.skills)

        bridge.call(
            "agent.run",
            buildJsonObject {
                put("prompt", "hello")
                put("tools", buildJsonArray {})
                put("skills", buildJsonArray {})
            },
        )
        val empty = gateway.requests.last()
        assertEquals(emptyList<String>(), empty.tools)
        assertEquals(emptyList<String>(), empty.skills)
    }

    @Test
    fun agentRunRejectsInvalidParams() = runTest {
        val gateway = FakeGateway()
        val bridge = Bridge(gateway)

        assertEquals("invalid_params", bridge.call("agent.run", buildJsonObject {}).errorCode())
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "   ") }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "x".repeat(64 * 1024 + 1)) }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call(
                "agent.run",
                buildJsonObject {
                    put("prompt", "ok")
                    put("systemPrompt", "x".repeat(16 * 1024 + 1))
                },
            ).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "ok"); put("maxTurns", 0) }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "ok"); put("maxTurns", 65) }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "ok"); put("maxTurns", "8") }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "ok"); put("providerId", 3) }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call("agent.run", buildJsonObject { put("prompt", "ok"); put("tools", "web_search") }).errorCode(),
        )
        assertEquals(
            "invalid_params",
            bridge.call(
                "agent.run",
                buildJsonObject {
                    put("prompt", "ok")
                    put("skills", buildJsonArray { add(JsonPrimitive(1)) })
                },
            ).errorCode(),
        )
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun agentMethodsAreUnavailableWithoutGateway() = runTest {
        val bridge = Bridge()

        assertEquals("agent_unavailable", bridge.call("agent.models").errorCode())
        assertEquals("agent_unavailable", bridge.call("agent.capabilities").errorCode())
        assertEquals(
            "agent_unavailable",
            bridge.call("agent.run", buildJsonObject { put("prompt", "hi") }).errorCode(),
        )
    }

    @Test
    fun gatewayFailuresMapToAgentFailed() = runTest {
        val gateway = FakeGateway().apply { failure = IllegalStateException("model missing") }
        val bridge = Bridge(gateway)

        assertEquals(
            "agent_failed",
            bridge.call("agent.run", buildJsonObject { put("prompt", "hi") }).errorCode(),
        )
        assertEquals("agent_failed", bridge.call("agent.models").errorCode())
        assertEquals("agent_failed", bridge.call("agent.capabilities").errorCode())
    }

    @Test
    fun requestsNeverEmitEvents() = runTest {
        val bridge = Bridge()
        bridge.call("info")
        bridge.call("data.keys", buildJsonObject {})
        bridge.call("ui.toast", buildJsonObject { put("text", "hi") })

        assertTrue(bridge.emitted.isEmpty())
    }

    private class Bridge(gateway: WorkshopAgentGateway? = null) {
        val data = FakeData()
        val toastMessages = mutableListOf<String>()
        val emitted = mutableListOf<JsonObject>()
        private val router = WorkshopBridgeRouter(
            WorkshopManifest(id = "garden", name = "Garden", icon = "🌱", version = "1.0.0"),
            data,
            { toastMessages.add(it) },
            gateway,
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

    private class FakeGateway : WorkshopAgentGateway {
        var failure: Exception? = null
        var response: String = "final text"
        val requests = mutableListOf<WorkshopAgentRequest>()

        override suspend fun models(): List<WorkshopAgentModel> {
            failure?.let { throw it }
            return listOf(
                WorkshopAgentModel(
                    providerId = "deepseek",
                    modelId = "deepseek-v4.1-flash",
                    label = "DeepSeek V4.1 Flash",
                    thinkingLevels = listOf("low", "high"),
                ),
            )
        }

        override suspend fun capabilities(): WorkshopAgentCapabilities {
            failure?.let { throw it }
            return WorkshopAgentCapabilities(
                tools = listOf(WorkshopAgentTool("web_search", "Search the web")),
                skills = listOf(WorkshopAgentSkill("workshop", "Build mini apps")),
            )
        }

        override suspend fun run(request: WorkshopAgentRequest): String {
            failure?.let { throw it }
            requests.add(request)
            return response
        }
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
}

private fun JsonObject.bool(field: String): Boolean = getValue(field).jsonPrimitive.boolean

private fun JsonObject.text(field: String): String = getValue(field).jsonPrimitive.content

private fun JsonObject.errorCode(): String =
    getValue("error").jsonObject.getValue("code").jsonPrimitive.content
