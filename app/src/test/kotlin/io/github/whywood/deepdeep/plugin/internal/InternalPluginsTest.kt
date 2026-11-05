package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSessionSnapshot
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPluginsTest {
    private val strings = object : PluginStrings {
        override fun get(id: Int): String = "res:$id"

        override fun format(id: Int, vararg args: Any): String = "res:$id"
    }

    private fun session(
        lookup: () -> PluginSessionSnapshot? = { PluginSessionSnapshot("session", null, "/tmp") },
    ): PluginSessionInfo = PluginSessionInfo(supportsChatDelivery = true, lookup = lookup)

    private suspend fun executeText(tool: PluginTool, action: String): String =
        (tool.execute(PluginToolCall("call-1", buildJsonObject { put("action", action) })) as PluginToolResult.Text).text

    private suspend fun executeNoArgs(tool: PluginTool): String =
        (tool.execute(PluginToolCall("call-1", buildJsonObject {})) as PluginToolResult.Text).text

    @Test
    fun goalPluginExposesGoalToolsAndRoundsSetting() = runTest {
        val plugin = GoalPlugin(GoalService())
        val host = testPluginHost(testBuiltinManifest("goal"))

        assertEquals("goal", testBuiltinManifest("goal").id)
        val names = plugin.contributionsFor(host, session()).tools.map { it.name }
        assertEquals(listOf("get_goal", "create_goal", "update_goal"), names)
        assertTrue(plugin.contributionsFor(host, null).tools.isEmpty())
        val setting = plugin.settings().single() as SettingSpec.Number
        assertEquals("max_rounds", setting.key)
        assertEquals(0, setting.min)
        assertEquals(50, setting.max)
        assertEquals("0", setting.default)
        assertTrue((plugin.status(host) as PluginStatus.Ready).label.isNotBlank())
    }

    @Test
    fun goalToolsReadTheLiveSessionAtExecuteTime() = runTest {
        val plugin = GoalPlugin(GoalService())
        val host = testPluginHost(testBuiltinManifest("goal"))
        var live: PluginSessionSnapshot? = null
        val getGoal = plugin.contributionsFor(host, session { live }).tools.first { it.name == "get_goal" }

        assertTrue(executeNoArgs(getGoal).contains("No session is bound"))

        live = PluginSessionSnapshot("session-live", "/tmp/session-live.jsonl", "/workspace")
        assertFalse(executeNoArgs(getGoal).contains("No session is bound"))
    }

    @Test
    fun jobsPluginExposesJobToolsAndRunningSetting() = runTest {
        val registry = JobRegistry(
            scope = this,
            notifier = { _, _, _ -> },
        )
        val plugin = JobsPlugin(registry, strings)
        val host = testPluginHost(testBuiltinManifest("jobs"))

        assertEquals(PluginAvailability.Available, plugin.availability(host))
        val names = plugin.contributionsFor(host, session()).tools.map { it.name }
        assertEquals(listOf("job_start", "job_output", "job_list", "job_kill"), names)
        assertTrue(plugin.contributionsFor(host, null).tools.isEmpty())
        val setting = plugin.settings().single() as SettingSpec.Number
        assertEquals("max_running", setting.key)
        assertEquals(1, setting.min)
        assertEquals(20, setting.max)
        assertEquals("10", setting.default)
        assertTrue((plugin.status(host) as PluginStatus.Ready).label.isNotBlank())
    }
}
