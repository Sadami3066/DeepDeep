package io.github.whywood.deepdeep.plugin.arrange

import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.provider.AlarmClock
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangePluginTest {
    private val context = FakeContext()
    private val launcher = FakeLauncher()

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()

        assertEquals("arrange", plugin.id)
        assertEquals("1.0.0", plugin.version)
        assertEquals(DdIcons.Clock, plugin.icon)
        assertEquals(R.string.plugin_arrange_name, plugin.nameRes)
        assertEquals(R.string.plugin_arrange_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_arrange_size, plugin.sizeLabelRes)
        assertEquals(listOf("arrange_alarm", "arrange_calendar"), plugin.tools().map { it.name })
    }

    @Test
    fun availabilityAndStatusAreAlwaysReady() = runTest {
        val plugin = plugin()

        assertEquals(PluginAvailability.Available, plugin.availability())
        val status = plugin.status()
        assertTrue(status is PluginStatus.Ready)
        assertEquals("res:${R.string.plugin_arrange_status_ready}", status.label)
    }

    @Test
    fun actionsOpenAlarmsAndCalendar() = runTest {
        val plugin = plugin()
        val actions = plugin.actions(PluginStatus.Ready("x"))

        assertEquals(listOf("alarm", "calendar"), actions.map { it.id })
        assertEquals(
            listOf(R.string.plugin_arrange_action_alarm, R.string.plugin_arrange_action_calendar),
            actions.map { it.labelRes },
        )

        plugin.runAction("alarm", NoopUi)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, launcher.last.action)

        plugin.runAction("calendar", NoopUi)
        assertEquals(Intent.ACTION_MAIN, launcher.last.action)
        assertEquals(listOf(Intent.CATEGORY_APP_CALENDAR), launcher.last.categories)

        val launches = launcher.launches
        plugin.runAction("unknown", NoopUi)
        assertEquals(launches, launcher.launches)
    }

    @Test
    fun detailRowsExplainSystemOwnership() = runTest {
        val rows = plugin().detailRows(PluginStatus.Ready("x"))

        assertEquals(2, rows.size)
        assertEquals(
            listOf(R.string.plugin_arrange_detail_saved, R.string.plugin_arrange_detail_confirm),
            rows.map { it.labelRes },
        )
        assertEquals(
            listOf(
                "res:${R.string.plugin_arrange_detail_saved_value}",
                "res:${R.string.plugin_arrange_detail_confirm_value}",
            ),
            rows.map { it.value },
        )
    }

    @Test
    fun promptBodyMentionsBothToolsAndLimits() {
        val body = plugin().promptBody(PluginStatus.Ready("x")).orEmpty()

        assertTrue(body.contains("arrange_alarm"))
        assertTrue(body.contains("arrange_calendar"))
        assertTrue(body.contains("无法读取"))
    }

    @Test
    fun skillHasFrontmatterAndCoversTools() {
        val skills = plugin().skills()

        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("deepdeep-arrange", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-arrange"))
        assertTrue(frontmatter.contains("description: "))
        listOf(
            "arrange_alarm",
            "arrange_calendar",
            "time",
            "days",
            "durationSeconds",
            "allDay",
            "未确认保存",
            "无法读取",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
        assertFalse(skill.markdown.contains("todo"))
    }

    @Test
    fun runActionSurfacesLaunchFailure() = runTest {
        launcher.failure = "没有找到可用的系统应用"
        val error = runCatching { plugin().runAction("alarm", NoopUi) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message.orEmpty().contains("没有找到可用的系统应用"))
    }

    private fun plugin(): ArrangePlugin = ArrangePlugin(context, launcher)

    private class FakeLauncher : ArrangeLauncher {
        lateinit var last: ArrangeIntentSpec
        var launches: Int = 0
        var failure: String? = null

        override fun launch(spec: ArrangeIntentSpec): ArrangeLaunchResult {
            last = spec
            launches += 1
            failure?.let { return ArrangeLaunchResult(launched = false, errorMessage = it) }
            return ArrangeLaunchResult(launched = true)
        }
    }

    private object NoopUi : PluginUi {
        override fun stage(
            titleRes: Int,
            messageRes: Int?,
            url: String?,
            progress: Int?,
            imagePath: String?,
        ) = Unit

        override fun progress(percent: Int?, labelRes: Int) = Unit

        override fun cancelled(): Boolean = false
    }

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources
    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }
}
