package io.github.whywood.deepdeep.vscreen

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.virtualscreen.VIRTUAL_SCREEN_DESCRIPTION
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenAccess
import io.github.whywood.deepdeep.plugin.virtualscreen.virtualScreenAvailability
import io.github.whywood.deepdeep.plugin.virtualscreen.virtualScreenTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenPluginTest {

    @Test
    fun availabilityFollowsBackendAvailability() {
        val reason = "需要无线调试（ADB）或 Root 权限"
        val unavailable = virtualScreenAvailability(available = false, unavailableReason = reason)
        assertTrue(unavailable is PluginAvailability.Unavailable)
        assertEquals(reason, (unavailable as PluginAvailability.Unavailable).reason)
        assertTrue(virtualScreenAvailability(available = true, unavailableReason = reason) is PluginAvailability.Available)
    }

    @Test
    fun exposesSingleLifecycleTool() {
        val tools = virtualScreenTools(FakeScreen())

        assertEquals(listOf("virtual_screen"), tools.map { it.name })
        assertEquals("virtual_screen", tools.single().label)
        assertNull(tools.single().promptSnippet)
        assertNull(tools.single().promptGuidelines)
    }

    @Test
    fun actionParameterIsRequiredEnum() {
        val parameters = tool().parameters
        val properties = parameters["properties"]!!.jsonObject
        val action = properties["action"]!!.jsonObject
        assertEquals("string", action["type"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("start", "stop", "status"),
            (action["enum"] as JsonArray).map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("action"),
            (parameters["required"] as JsonArray).map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun statusOfOfflineScreenIsJustState() = runTest {
        val result = run("status")
        assertNull(result.isError)
        val payload = result.json()
        assertEquals(setOf("state", "reconnecting"), payload.keys)
        assertEquals("offline", payload["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun onlineStateReportsSizeWithoutInternalDetails() = runTest {
        val screen = FakeScreen(VirtualScreenState.Online(3, 1080, 1920, "adb"))
        val result = runTool(screen, "status")
        assertNull(result.isError)
        val payload = result.json()
        assertEquals(
            setOf("state", "width", "height", "reconnecting"),
            payload.keys,
        )
        assertEquals("online", payload["state"]!!.jsonPrimitive.content)
        assertEquals(1080, payload["width"]!!.jsonPrimitive.content.toInt())
        assertEquals(1920, payload["height"]!!.jsonPrimitive.content.toInt())
        assertTrue(!result.text().contains("displayId"))
        assertTrue(!result.text().contains("uid"))
        assertTrue(!result.text().contains("backend"))
    }

    @Test
    fun startReportIncludesStateAndRebuildNotice() = runTest {
        val screen = FakeScreen(VirtualScreenState.Online(3, 1200, 2600, "root")).apply {
            lastTransportNotice = "虚拟屏已重建"
        }
        val result = runTool(screen, "start")
        assertNull(result.isError)
        assertEquals("online", result.json()["state"]!!.jsonPrimitive.content)
        assertEquals("虚拟屏已重建", result.json()["notice"]!!.jsonPrimitive.content)
        assertTrue(screen.started)
    }

    @Test
    fun startFailureReturnsFailedState() = runTest {
        val screen = FakeScreen().apply {
            startResult = VirtualScreenResult(false, "启动失败")
            lastFailure = VirtualScreenFailureInfo(
                category = VirtualScreenFailureCategory.START,
                message = "启动失败",
                recoverable = true,
            )
        }
        val result = runTool(screen, "start")
        assertEquals(true, result.isError)
        assertEquals("failed", result.json()["state"]!!.jsonPrimitive.content)
        val failure = result.json()["failure"]!!.jsonObject
        assertEquals("start", failure["category"]!!.jsonPrimitive.content)
        assertEquals("启动失败", failure["message"]!!.jsonPrimitive.content)
        assertNotNull(failure["hint"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun stopReturnsOfflineState() = runTest {
        val screen = FakeScreen(VirtualScreenState.Online(3, 1080, 1920, "adb"))
        val result = runTool(screen, "stop")
        assertNull(result.isError)
        assertEquals("offline", result.json()["state"]!!.jsonPrimitive.content)
        assertTrue(screen.stopped)
    }

    @Test
    fun failureCategoriesAreMappedToTaskLanguage() = runTest {
        val cases = mapOf(
            VirtualScreenFailureCategory.TRANSPORT to "connection",
            VirtualScreenFailureCategory.BACKEND to "privilege",
            VirtualScreenFailureCategory.START to "start",
        )
        cases.forEach { (category, expected) ->
            val screen = FakeScreen(VirtualScreenState.Failed("失败")).apply {
                lastFailure = VirtualScreenFailureInfo(category, "失败", recoverable = false)
            }
            val result = runTool(screen, "status")
            val failure = result.json()["failure"]!!.jsonObject
            assertEquals(expected, failure["category"]!!.jsonPrimitive.content)
            assertNotNull(failure["message"]!!.jsonPrimitive.contentOrNull)
            assertNotNull(failure["hint"]!!.jsonPrimitive.contentOrNull)
            listOf("uid", "displayId", "backend", "helper", "daemon").forEach { word ->
                assertFalse(word, result.text().contains(word, ignoreCase = true))
            }
        }
    }

    @Test
    fun startingStateIsReported() = runTest {
        val result = runTool(FakeScreen(VirtualScreenState.Starting), "status")
        assertNull(result.isError)
        assertEquals("starting", result.json()["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun unknownActionIsRejected() = runTest {
        val result = run("move")
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("action"))
    }

    @Test
    fun descriptionExplainsLifecycleAndParameterUsage() {
        assertTrue(VIRTUAL_SCREEN_DESCRIPTION.contains("start") && VIRTUAL_SCREEN_DESCRIPTION.contains("stop"))
        assertTrue(VIRTUAL_SCREEN_DESCRIPTION.all { it.code < 128 })
        assertTrue(VIRTUAL_SCREEN_DESCRIPTION.isNotBlank())
        val descriptions = buildList {
            add(tool().description)
            collectParameterDescriptions(tool().parameters, this)
        }
        descriptions.forEach { text ->
            assertTrue(text.isNotBlank())
        }
    }

    private suspend fun run(action: String): AgentToolResult = runTool(FakeScreen(), action)

    private suspend fun runTool(screen: VirtualScreenAccess, action: String): AgentToolResult = tool(screen).run(
        buildJsonObject { put("action", action) },
    )

    private fun tool(screen: VirtualScreenAccess = FakeScreen()): CodingTool = virtualScreenTools(screen).single()

    private suspend fun CodingTool.run(args: JsonObject): AgentToolResult = execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun AgentToolResult.json(): JsonObject = Json.parseToJsonElement(text()).jsonObject

    private fun collectParameterDescriptions(parameters: JsonObject, into: MutableList<String>) {
        val properties = parameters["properties"] as? JsonObject ?: return
        properties.values.forEach { property ->
            val description = (property as? JsonObject)?.get("description") as? JsonPrimitive
            description?.contentOrNull?.let(into::add)
        }
    }

    private class FakeScreen(
        initial: VirtualScreenState = VirtualScreenState.Offline,
    ) : VirtualScreenAccess {
        override val state = MutableStateFlow(initial)
        override var lastFailure: VirtualScreenFailureInfo? = null
        override var lastTransportNotice: String? = null
        var startResult: VirtualScreenResult = VirtualScreenResult(true)
        var started = false
        var stopped = false

        override suspend fun start(): VirtualScreenResult {
            started = true
            state.value = if (startResult.ok) {
                VirtualScreenState.Online(3, 1080, 1920, "adb")
            } else {
                VirtualScreenState.Failed(startResult.message)
            }
            return startResult
        }

        override suspend fun stop() {
            stopped = true
            state.value = VirtualScreenState.Offline
        }
    }
}
