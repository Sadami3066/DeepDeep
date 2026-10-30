package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.SessionBinding
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
        val plugin = GoalPlugin(GoalService(), strings)

        assertEquals(PluginKind.INTERNAL, plugin.kind)
        assertEquals("goal", plugin.id)
        val names = plugin.tools(SessionBinding()).map { it.name }
        assertTrue(names.containsAll(listOf("get_goal", "create_goal", "update_goal")))
        val setting = plugin.settings().single() as PluginSettingSpec.Number
        assertEquals("max_rounds", setting.key)
        assertEquals(0, setting.min)
        assertEquals(50, setting.max)
        assertEquals("0", setting.default)
        assertEquals("res:${io.github.whywood.deepdeep.R.string.plugins_status_builtin}", plugin.status().label)
    }

    @Test
    fun jobsPluginExposesJobToolsAndRunningSetting() = runTest {
        val registry = JobRegistry(
            scope = this,
            notifier = { _, _, _ -> },
        )
        val plugin = JobsPlugin(registry, strings)

        assertEquals(PluginKind.INTERNAL, plugin.kind)
        val names = plugin.tools(SessionBinding()).map { it.name }
        assertEquals(listOf("job_start", "job_output", "job_list", "job_kill"), names)
        val setting = plugin.settings().single() as PluginSettingSpec.Number
        assertEquals("max_running", setting.key)
        assertEquals(1, setting.min)
        assertEquals(20, setting.max)
        assertEquals("10", setting.default)
    }
}
