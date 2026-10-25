package io.github.whywood.deepdeep.plugin.arrange

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeToolsTest {
    private val launcher = FakeLauncher()
    private val tools = arrangeTools(launcher, ZoneId.of("Asia/Shanghai"))

    @Test
    fun exposesAlarmAndCalendarTools() {
        assertEquals(listOf("arrange_alarm", "arrange_calendar"), tools.map { it.name })
    }

    @Test
    fun alarmSchemaDocumentsActionsAndBounds() {
        val alarm = tools.first { it.name == "arrange_alarm" }
        val properties = alarm.parameters["properties"] as JsonObject
        val action = properties["action"] as JsonObject

        assertEquals(listOf("set", "timer", "show"), (action["enum"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("action", (alarm.parameters["required"] as JsonArray).single().jsonPrimitive.content)

        val days = properties["days"] as JsonObject
        assertEquals("array", (days["type"] as JsonPrimitive).content)
        assertEquals(1, ((days["items"] as JsonObject)["minimum"] as JsonPrimitive).int)
        assertEquals(7, ((days["items"] as JsonObject)["maximum"] as JsonPrimitive).int)

        val duration = properties["durationSeconds"] as JsonObject
        assertEquals(MIN_TIMER_SECONDS, (duration["minimum"] as JsonPrimitive).int)
        assertEquals(MAX_TIMER_SECONDS, (duration["maximum"] as JsonPrimitive).int)

        assertEquals(0, ((properties["hour"] as JsonObject)["minimum"] as JsonPrimitive).int)
        assertEquals(23, ((properties["hour"] as JsonObject)["maximum"] as JsonPrimitive).int)
        assertEquals(0, ((properties["minute"] as JsonObject)["minimum"] as JsonPrimitive).int)
        assertEquals(59, ((properties["minute"] as JsonObject)["maximum"] as JsonPrimitive).int)
    }

    @Test
    fun calendarSchemaDocumentsActionsAndFields() {
        val calendar = tools.first { it.name == "arrange_calendar" }
        val properties = calendar.parameters["properties"] as JsonObject
        val action = properties["action"] as JsonObject

        assertEquals(listOf("create", "open"), (action["enum"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("action", (calendar.parameters["required"] as JsonArray).single().jsonPrimitive.content)
        listOf("title", "start", "end", "allDay", "location", "description", "time").forEach { field ->
            assertTrue(field, properties.containsKey(field))
        }
    }

    @Test
    fun setAlarmLaunchesAlarmClockSpec() = runTest {
        val result = run(
            "arrange_alarm",
            buildJsonObject {
                put("action", "set")
                put("time", "07:30")
                put("label", "起床")
                put(
                    "days",
                    buildJsonArray {
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(3))
                    },
                )
                put("skipUi", true)
            },
        )

        assertNull(result.isError)
        val spec = launcher.last
        assertEquals(AlarmClock.ACTION_SET_ALARM, spec.action)
        assertEquals(7, spec.extras[AlarmClock.EXTRA_HOUR] as Int)
        assertEquals(30, spec.extras[AlarmClock.EXTRA_MINUTES] as Int)
        assertEquals("起床", spec.extras[AlarmClock.EXTRA_MESSAGE])
        assertEquals(true, spec.extras[AlarmClock.EXTRA_SKIP_UI])
        assertTrue(result.text().contains("07:30"))
        assertTrue(result.text().contains("周一、周三"))
    }

    @Test
    fun setAlarmAcceptsHourAndMinute() = runTest {
        val result = run(
            "arrange_alarm",
            buildJsonObject {
                put("action", "set")
                put("hour", 6)
                put("minute", 5)
            },
        )

        assertNull(result.isError)
        assertEquals(6, launcher.last.extras[AlarmClock.EXTRA_HOUR] as Int)
        assertEquals(5, launcher.last.extras[AlarmClock.EXTRA_MINUTES] as Int)
        assertTrue(result.text().contains("仅一次"))
    }

    @Test
    fun setAlarmValidatesTimeInputs() = runTest {
        assertTrue(run("arrange_alarm", buildJsonObject { put("action", "set") }).isError == true)
        assertTrue(
            run(
                "arrange_alarm",
                buildJsonObject {
                    put("action", "set")
                    put("time", "07:30")
                    put("hour", 7)
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "arrange_alarm",
                buildJsonObject {
                    put("action", "set")
                    put("hour", 7)
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "arrange_alarm",
                buildJsonObject {
                    put("action", "set")
                    put("time", "25:00")
                },
            ).isError == true,
        )
        assertEquals(0, launcher.launches)
    }

    @Test
    fun setAlarmRejectsInvalidDays() = runTest {
        listOf(0, 8).forEach { day ->
            val result = run(
                "arrange_alarm",
                buildJsonObject {
                    put("action", "set")
                    put("time", "07:30")
                    put(
                        "days",
                        buildJsonArray { add(JsonPrimitive(day)) },
                    )
                },
            )
            assertEquals(true, result.isError)
        }
        assertEquals(0, launcher.launches)
    }

    @Test
    fun timerLaunchesAndFormatsDuration() = runTest {
        val result = run(
            "arrange_alarm",
            buildJsonObject {
                put("action", "timer")
                put("durationSeconds", 600)
                put("label", "泡面")
            },
        )

        assertNull(result.isError)
        assertEquals(AlarmClock.ACTION_SET_TIMER, launcher.last.action)
        assertEquals(600, launcher.last.extras[AlarmClock.EXTRA_LENGTH] as Int)
        assertTrue(result.text().contains("10 分"))
        assertTrue(result.text().contains("泡面"))
    }

    @Test
    fun timerRequiresValidDuration() = runTest {
        assertTrue(run("arrange_alarm", buildJsonObject { put("action", "timer") }).isError == true)
        assertTrue(
            run(
                "arrange_alarm",
                buildJsonObject {
                    put("action", "timer")
                    put("durationSeconds", 0)
                },
            ).isError == true,
        )
        assertEquals(0, launcher.launches)
    }

    @Test
    fun showLaunchesAlarmList() = runTest {
        val result = run("arrange_alarm", buildJsonObject { put("action", "show") })

        assertNull(result.isError)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, launcher.last.action)
        assertTrue(result.text().contains("已打开系统闹钟"))
    }

    @Test
    fun unknownAlarmActionFails() = runTest {
        val result = run("arrange_alarm", buildJsonObject { put("action", "snooze") })

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("set、timer、show"))
    }

    @Test
    fun launcherFailureBecomesError() = runTest {
        launcher.failure = "没有找到可用的系统应用"
        val result = run(
            "arrange_alarm",
            buildJsonObject {
                put("action", "set")
                put("time", "07:30")
            },
        )

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("没有找到可用的系统应用"))
    }

    @Test
    fun calendarCreateLaunchesInsertSpec() = runTest {
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "create")
                put("title", "团队会议")
                put("start", "2026-10-26 10:00")
                put("end", "2026-10-26 11:30")
                put("location", "3 楼")
                put("description", "带电脑")
            },
        )

        assertNull(result.isError)
        val spec = launcher.last
        assertEquals(Intent.ACTION_INSERT, spec.action)
        assertEquals(CALENDAR_EVENTS_URI, spec.data)
        assertEquals(
            parseCalendarMillis("2026-10-26 10:00", ZoneId.of("Asia/Shanghai")),
            spec.extras[CalendarContract.EXTRA_EVENT_BEGIN_TIME],
        )
        assertEquals("团队会议", spec.extras[CalendarContract.Events.TITLE])
        assertEquals("3 楼", spec.extras[CalendarContract.Events.EVENT_LOCATION])
        assertTrue(result.text().contains("未确认保存"))
        assertTrue(result.text().contains("2026-10-26 10:00"))
    }

    @Test
    fun calendarCreateSupportsAllDay() = runTest {
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "create")
                put("title", "国庆")
                put("start", "2026-10-01")
                put("allDay", true)
            },
        )

        assertNull(result.isError)
        assertEquals(true, launcher.last.extras[CalendarContract.EXTRA_EVENT_ALL_DAY])
        assertTrue(result.text().contains("全天"))
    }

    @Test
    fun calendarCreateValidatesInputs() = runTest {
        assertTrue(
            run(
                "arrange_calendar",
                buildJsonObject {
                    put("action", "create")
                    put("start", "2026-10-26 10:00")
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "arrange_calendar",
                buildJsonObject {
                    put("action", "create")
                    put("title", "会议")
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "arrange_calendar",
                buildJsonObject {
                    put("action", "create")
                    put("title", "会议")
                    put("start", "下周")
                },
            ).isError == true,
        )
        assertTrue(
            run(
                "arrange_calendar",
                buildJsonObject {
                    put("action", "create")
                    put("title", "会议")
                    put("start", "2026-10-26 11:00")
                    put("end", "2026-10-26 10:00")
                },
            ).isError == true,
        )
        assertEquals(0, launcher.launches)
    }

    @Test
    fun calendarOpenLaunchesCalendarApp() = runTest {
        val result = run("arrange_calendar", buildJsonObject { put("action", "open") })

        assertNull(result.isError)
        assertEquals(Intent.ACTION_MAIN, launcher.last.action)
        assertEquals(listOf(Intent.CATEGORY_APP_CALENDAR), launcher.last.categories)
        assertTrue(result.text().contains("已打开系统日历"))
    }

    @Test
    fun calendarOpenSupportsTime() = runTest {
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "open")
                put("time", "2026-10-26 10:00")
            },
        )

        assertNull(result.isError)
        assertEquals(Intent.ACTION_VIEW, launcher.last.action)
        assertTrue(launcher.last.data.orEmpty().startsWith(CALENDAR_TIME_URI))
        assertTrue(result.text().contains("2026-10-26 10:00"))
    }

    @Test
    fun unknownCalendarActionFails() = runTest {
        val result = run("arrange_calendar", buildJsonObject { put("action", "delete") })

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("create、open"))
    }

    private suspend fun run(name: String, args: JsonObject): AgentToolResult =
        tools.first { it.name == name }.execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

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
}
