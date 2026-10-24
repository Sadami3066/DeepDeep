package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSetupToolTest {
    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun args(plugin: String): JsonObject = buildJsonObject { put("plugin", plugin) }

    @Test
    fun toolExposesPluginIdEnum() {
        val tool = pluginSetupTool(controller())
        assertEquals("plugin_setup", tool?.name)
        val properties = tool?.parameters?.getValue("properties")?.jsonObject
        val plugin = properties?.getValue("plugin")?.jsonObject
        assertTrue(plugin.toString().contains("feishu"))
    }

    @Test
    fun missingControllerDisablesTool() {
        assertNull(pluginSetupTool(null))
    }

    @Test
    fun setupSuccessReturnsJson() = runTest {
        val tool = pluginSetupTool(controller())
        val result = tool!!.execute("call-1", args("feishu"), null, null)
        assertFalse(result.isError == true)
        val json = Json.parseToJsonElement(resultText(result)).jsonObject
        assertEquals("true", json.getValue("ok").toString())
        assertEquals("feishu", json.getValue("plugin").toString().trim('"'))
        assertEquals("true", json.getValue("ready").toString())
        assertEquals("张三", json.getValue("account").toString().trim('"'))
    }

    @Test
    fun controllerFailureBecomesErrorText() = runTest {
        val tool = pluginSetupTool(
            object : PluginController {
                override suspend fun setup(pluginId: String): PluginSetupResult {
                    throw IllegalStateException("boom")
                }
            },
        )
        val result = tool!!.execute("call-2", args("feishu"), null, null)
        assertTrue(result.isError == true)
        assertEquals("boom", resultText(result))
    }

    private fun controller(): PluginController = object : PluginController {
        override suspend fun setup(pluginId: String): PluginSetupResult = PluginSetupResult(
            ok = true,
            pluginId = pluginId,
            installed = true,
            ready = true,
            status = "已连接 · 张三",
            account = "张三",
        )
    }
}
