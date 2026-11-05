package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPluginsTest {
    private val strings = object : PluginStrings {
        override fun get(id: Int): String = "res:$id"

        override fun format(id: Int, vararg args: Any): String = "res:$id"
    }

    @Test
    fun goalPluginExposesGoalToolsAndRoundsSetting() = runTest {
        val plugin = GoalPlugin(GoalService())
        val host = testPluginHost(testBuiltinManifest("goal"))

        assertEquals("goal", testBuiltinManifest("goal").id)
        val session = io.github.whywood.deepdeep.plugin.api.PluginSessionInfo(
            sessionId = "session",
            cwd = "/tmp",
        )
        val names = plugin.contributionsFor(host, session).tools.map { it.name }
        assertEquals(listOf("get_goal", "create_goal", "update_goal"), names)
        assertTrue(plugin.contributionsFor(host, null).tools.isEmpty())
        val setting = plugin.settings().single() as io.github.whywood.deepdeep.plugin.api.SettingSpec.Number
        assertEquals("max_rounds", setting.key)
        assertEquals(0, setting.min)
        assertEquals(50, setting.max)
        assertEquals("0", setting.default)
        assertTrue((plugin.status(host) as io.github.whywood.deepdeep.plugin.api.PluginStatus.Ready).label.isNotBlank())
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
        val session = PluginSessionInfo(sessionId = "session", cwd = "/tmp")
        val names = plugin.contributionsFor(host, session).tools.map { it.name }
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
