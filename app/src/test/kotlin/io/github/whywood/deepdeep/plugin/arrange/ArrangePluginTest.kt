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
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangePluginTest {
    private val context = FakeContext()
    private val launcher = FakeLauncher()
    private val calendar = FakeCalendarGateway()
    private val alarms = FakeAlarmEngine()

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()

        assertEquals("arrange", plugin.id)
        assertEquals("2.0.0", plugin.version)
        assertEquals(DdIcons.Clock, plugin.icon)
        assertEquals(R.string.plugin_arrange_name, plugin.nameRes)
        assertEquals(R.string.plugin_arrange_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_arrange_size, plugin.sizeLabelRes)
        assertEquals(listOf("arrange_alarm", "arrange_calendar"), plugin.tools().map { it.name })
    }

    @Test
    fun availabilityIsAlwaysAvailable() = runTest {
        assertEquals(PluginAvailability.Available, plugin().availability())
    }

    @Test
    fun statusReflectsCalendarPermission() = runTest {
        assertEquals("res:${R.string.plugin_arrange_status_ready}", plugin().status().label)

        calendar.readAllowed = false
        assertEquals(
            "res:${R.string.plugin_arrange_status_calendar_permission}",
            plugin().status().label,
        )
    }

    @Test
    fun actionsIncludePermissionAndExactAlarmEntryPoints() = runTest {
        val ready = plugin()
        assertEquals(listOf("alarm", "calendar"), ready.actions(PluginStatus.Ready("x")).map { it.id })

        calendar.readAllowed = false
        alarms.exactAllowed = false
        val actions = ready.actions(PluginStatus.Ready("x"))
        assertEquals(
            listOf("alarm", "calendar", "permission", "exact_alarm"),
            actions.map { it.id },
        )
        assertEquals(
            listOf(
                R.string.plugin_arrange_action_alarm,
                R.string.plugin_arrange_action_calendar,
                R.string.plugin_arrange_action_permission,
                R.string.plugin_arrange_action_exact_alarm,
            ),
            actions.map { it.labelRes },
        )
    }

    @Test
    fun runActionOpensClockAndCalendar() = runTest {
        val plugin = plugin()
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
    fun detailRowsShowManagedAlarmsCalendarAndSystemLimits() = runTest {
        val rows = plugin().detailRows(PluginStatus.Ready("x"))

        assertEquals(4, rows.size)
        assertEquals(
            listOf(
                R.string.plugin_arrange_detail_alarms,
                R.string.plugin_arrange_detail_calendar,
                R.string.plugin_arrange_detail_system,
                R.string.plugin_arrange_detail_exact,
            ),
            rows.map { it.labelRes },
        )
        assertEquals("res:${R.string.plugin_arrange_detail_calendar_granted}", rows[1].value)
        assertEquals("res:${R.string.plugin_arrange_detail_exact_granted}", rows[3].value)

        calendar.readAllowed = false
        alarms.exactAllowed = false
        val missing = plugin().detailRows(PluginStatus.Ready("x"))
        assertEquals("res:${R.string.plugin_arrange_detail_calendar_missing}", missing[1].value)
        assertEquals("res:${R.string.plugin_arrange_detail_exact_missing}", missing[3].value)
    }

    @Test
    fun promptBodyMentionsCrudAndSystemClockLimit() {
        val body = plugin().promptBody(PluginStatus.Ready("x")).orEmpty()

        assertTrue(body.contains("arrange_alarm"))
        assertTrue(body.contains("arrange_calendar"))
        assertTrue(body.contains("系统时钟"))
        assertTrue(body.contains("无法读取"))
    }

    @Test
    fun skillHasFrontmatterAndCoversCrud() {
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
            "list",
            "update",
            "delete",
            "timer",
            "reminders",
            "READ_CALENDAR",
            "系统时钟",
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

    private fun plugin(): ArrangePlugin = ArrangePlugin(context, launcher, alarms, calendar)

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

    private class FakeAlarmEngine : ArrangeAlarmEngine {
        var exactAllowed = true

        override fun list(): List<ArrangeAlarm> = emptyList()

        override fun createAlarm(draft: ArrangeAlarmDraft): ArrangeAlarm =
            ArrangeAlarm("id", draft.label.orEmpty(), draft.hour, draft.minute, draft.days, draft.enabled, false, 0L, 0L)

        override fun updateAlarm(id: String, draft: ArrangeAlarmDraft): ArrangeAlarm? = null

        override fun deleteAlarm(id: String): Boolean = false

        override fun createTimer(durationSeconds: Int, label: String?): ArrangeAlarm =
            ArrangeAlarm("timer", label.orEmpty(), 0, 0, emptyList(), true, true, 0L, 0L)

        override fun nextSystemAlarm(): ArrangeSystemAlarm? = null

        override fun canScheduleExactAlarms(): Boolean = exactAllowed

        override fun rescheduleAll() = Unit
    }

    private class FakeCalendarGateway : ArrangeCalendarGateway {
        var readAllowed = true
        var writeAllowed = true

        override fun hasReadPermission(): Boolean = readAllowed

        override fun hasWritePermission(): Boolean = writeAllowed

        override fun calendars(): List<ArrangeCalendarInfo> = emptyList()

        override fun list(startMillis: Long, endMillis: Long, query: String?, limit: Int): List<ArrangeCalendarEvent> =
            emptyList()

        override fun get(id: Long): ArrangeCalendarEvent? = null

        override fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent =
            ArrangeCalendarEvent(1, 1, draft.title, draft.startMillis, draft.startMillis + 60_000, false)

        override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? = null

        override fun delete(id: Long): Boolean = false
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
