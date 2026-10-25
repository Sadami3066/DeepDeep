package io.github.whywood.deepdeep.plugin.arrange

import android.provider.AlarmClock
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeToolsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val launcher = FakeLauncher()
    private val alarms = FakeAlarmEngine(zone)
    private val calendar = FakeCalendarGateway()
    private val tools = arrangeTools(launcher, alarms, calendar, zone)

    @Test
    fun exposesAlarmAndCalendarTools() {
        assertEquals(listOf("arrange_alarm", "arrange_calendar"), tools.map { it.name })
    }

    @Test
    fun schemasDocumentCrudActions() {
        val alarm = tools.first { it.name == "arrange_alarm" }
        val alarmProperties = alarm.parameters["properties"] as JsonObject
        assertEquals(
            listOf("list", "set", "update", "delete", "timer", "show"),
            (alarmProperties["action"] as JsonObject).let { (it["enum"] as JsonArray).map { value -> value.jsonPrimitive.content } },
        )
        listOf("id", "time", "hour", "minute", "label", "days", "enabled", "durationSeconds").forEach { field ->
            assertTrue(field, alarmProperties.containsKey(field))
        }

        val calendarTool = tools.first { it.name == "arrange_calendar" }
        val calendarProperties = calendarTool.parameters["properties"] as JsonObject
        assertEquals(
            listOf("calendars", "list", "get", "create", "update", "delete", "open"),
            ((calendarProperties["action"] as JsonObject)["enum"] as JsonArray).map { it.jsonPrimitive.content },
        )
        listOf("id", "calendarId", "title", "start", "end", "allDay", "location", "description", "reminders", "query", "limit", "time")
            .forEach { field -> assertTrue(field, calendarProperties.containsKey(field)) }
        assertEquals(200, ((calendarProperties["limit"] as JsonObject)["maximum"] as JsonPrimitive).int)
    }

    @Test
    fun setAlarmCreatesManagedAlarm() = runTest {
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
            },
        )

        assertNull(result.isError)
        val created = alarms.list().single()
        assertEquals(7, created.hour)
        assertEquals(30, created.minute)
        assertEquals(listOf(1, 3), created.days)
        val body = result.json()
        assertEquals(true, body["created"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(created.id, body["alarm"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(0, launcher.launches)
    }

    @Test
    fun updateAlarmChangesOnlyProvidedFields() = runTest {
        val created = alarms.createAlarm(ArrangeAlarmDraft(7, 30, "起床", listOf(1), true))
        val result = run(
            "arrange_alarm",
            buildJsonObject {
                put("action", "update")
                put("id", created.id)
                put("label", "上班")
                put("enabled", false)
            },
        )

        assertNull(result.isError)
        val updated = alarms.list().single()
        assertEquals("上班", updated.label)
        assertEquals(7, updated.hour)
        assertEquals(30, updated.minute)
        assertEquals(listOf(1), updated.days)
        assertFalse(updated.enabled)
    }

    @Test
    fun deleteAlarmRemovesEntry() = runTest {
        val created = alarms.createAlarm(ArrangeAlarmDraft(7, 30, "起床", emptyList(), true))
        val result = run("arrange_alarm", buildJsonObject {
            put("action", "delete")
            put("id", created.id)
        })

        assertNull(result.isError)
        assertTrue(alarms.list().isEmpty())
        assertEquals("""{"deleted":"${created.id}"}""", result.text())
    }

    @Test
    fun deleteMissingAlarmFails() = runTest {
        val result = run("arrange_alarm", buildJsonObject {
            put("action", "delete")
            put("id", "missing")
        })

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("没有找到闹钟"))
    }

    @Test
    fun timerCreatesManagedTimer() = runTest {
        val result = run("arrange_alarm", buildJsonObject {
            put("action", "timer")
            put("durationSeconds", 600)
            put("label", "泡面")
        })

        assertNull(result.isError)
        val created = alarms.list().single()
        assertTrue(created.timer)
        assertEquals("泡面", created.label)
        assertTrue(created.triggerAt > System.currentTimeMillis())
        assertTrue(result.json()["alarm"]!!.jsonObject["kind"]!!.jsonPrimitive.content == "timer")
    }

    @Test
    fun listReturnsAlarmsAndSystemNext() = runTest {
        alarms.createAlarm(ArrangeAlarmDraft(7, 30, "起床", emptyList(), true))
        alarms.systemNext = ArrangeSystemAlarm(1_800_000_000_000, "系统闹钟")
        val result = run("arrange_alarm", buildJsonObject { put("action", "list") })

        assertNull(result.isError)
        val body = result.json()
        assertEquals(1, (body["alarms"] as JsonArray).size)
        val system = body["systemNext"]!!.jsonObject
        assertEquals(1_800_000_000_000L, system["at"]!!.jsonPrimitive.content.toLong())
        assertEquals("系统闹钟", system["label"]!!.jsonPrimitive.content)
        assertTrue(system["readOnly"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun exactAlarmFailureBecomesToolError() = runTest {
        alarms.failCreate = ArrangeExactAlarmException("未授予精确闹钟权限")
        val result = run("arrange_alarm", buildJsonObject {
            put("action", "set")
            put("time", "07:30")
        })

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("精确闹钟"))
    }

    @Test
    fun alarmInputValidation() = runTest {
        listOf(
            buildJsonObject { put("action", "set") },
            buildJsonObject { put("action", "set"); put("hour", 7) },
            buildJsonObject { put("action", "set"); put("time", "25:00") },
            buildJsonObject { put("action", "update") },
            buildJsonObject { put("action", "timer") },
            buildJsonObject { put("action", "snooze") },
        ).forEach { args ->
            assertTrue(run("arrange_alarm", args).isError == true)
        }
        assertEquals(0, launcher.launches)
    }

    @Test
    fun showOpensSystemClock() = runTest {
        val result = run("arrange_alarm", buildJsonObject { put("action", "show") })

        assertNull(result.isError)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, launcher.last.action)
        assertTrue(result.text().contains("已打开系统闹钟"))
    }

    @Test
    fun calendarsListsWritableCalendars() = runTest {
        val result = run("arrange_calendar", buildJsonObject { put("action", "calendars") })

        assertNull(result.isError)
        val list = result.json()["calendars"] as JsonArray
        assertEquals(1, list.size)
        assertEquals(1L, list.single().jsonObject["id"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun calendarCreateWritesDraftAndReturnsEvent() = runTest {
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "create")
                put("title", "团队会议")
                put("start", "2026-10-26 10:00")
                put("end", "2026-10-26 11:30")
                put("location", "3 楼")
                put("description", "带电脑")
                put("calendarId", 1)
                put("reminders", buildJsonArray { add(JsonPrimitive(15)) })
            },
        )

        assertNull(result.isError)
        val draft = calendar.lastDraft!!
        assertEquals("团队会议", draft.title)
        assertEquals(listOf(15), draft.reminderMinutes)
        assertEquals(parseCalendarMillis("2026-10-26 10:00", zone), draft.startMillis)
        assertEquals(parseCalendarMillis("2026-10-26 11:30", zone), draft.endMillis)
        val event = result.json()["event"]!!.jsonObject
        assertEquals("团队会议", event["title"]!!.jsonPrimitive.content)
        assertEquals(1L, event["calendarId"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun calendarUpdateUsesPatchSemantics() = runTest {
        val event = calendar.create(ArrangeEventDraft("旧标题", 1_000L, 2_000L, false, null, null, 1, null))
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "update")
                put("id", event.id)
                put("title", "新标题")
                put("location", "")
                put("reminders", buildJsonArray { })
            },
        )

        assertNull(result.isError)
        val patch = calendar.lastPatch!!
        assertEquals("新标题", patch.title)
        assertEquals("", patch.location)
        assertEquals(emptyList<Int>(), patch.reminderMinutes)
        assertEquals("新标题", calendar.get(event.id)!!.title)
    }

    @Test
    fun calendarDeleteRemovesEvent() = runTest {
        val event = calendar.create(ArrangeEventDraft("旧标题", 1_000L, 2_000L, false, null, null, 1, null))
        val result = run("arrange_calendar", buildJsonObject {
            put("action", "delete")
            put("id", event.id)
        })

        assertNull(result.isError)
        assertNull(calendar.get(event.id))
    }

    @Test
    fun calendarListUsesRangeAndLimit() = runTest {
        calendar.create(ArrangeEventDraft("会议", parseCalendarMillis("2026-10-26 10:00", zone), null, false, null, null, 1, null))
        val result = run(
            "arrange_calendar",
            buildJsonObject {
                put("action", "list")
                put("start", "2026-10-26 00:00")
                put("end", "2026-10-27 00:00")
                put("limit", 10)
            },
        )

        assertNull(result.isError)
        assertEquals(1, (result.json()["events"] as JsonArray).size)
        assertEquals(10, calendar.lastLimit)
    }

    @Test
    fun calendarPermissionFailureBecomesToolError() = runTest {
        calendar.readAllowed = false
        val result = run("arrange_calendar", buildJsonObject { put("action", "calendars") })

        assertEquals(true, result.isError)
        assertTrue(result.text().contains("日历"))
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
        assertEquals(android.content.Intent.ACTION_VIEW, launcher.last.action)
        assertTrue(launcher.last.data.orEmpty().startsWith(CALENDAR_TIME_URI))
    }

    @Test
    fun calendarInputValidation() = runTest {
        listOf(
            buildJsonObject { put("action", "create"); put("start", "2026-10-26 10:00") },
            buildJsonObject { put("action", "create"); put("title", "会议") },
            buildJsonObject { put("action", "create"); put("title", "会议"); put("start", "下周") },
            buildJsonObject {
                put("action", "create")
                put("title", "会议")
                put("start", "2026-10-26 11:00")
                put("end", "2026-10-26 10:00")
            },
            buildJsonObject { put("action", "get") },
            buildJsonObject { put("action", "list"); put("start", "2026-10-27 00:00"); put("end", "2026-10-26 00:00") },
            buildJsonObject { put("action", "list"); put("limit", 0) },
            buildJsonObject { put("action", "remove") },
        ).forEach { args ->
            assertTrue(run("arrange_calendar", args).isError == true)
        }
    }

    private suspend fun run(name: String, args: JsonObject): AgentToolResult =
        tools.first { it.name == name }.execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun AgentToolResult.json(): JsonObject =
        kotlinx.serialization.json.Json.parseToJsonElement(text()).jsonObject

    private class FakeLauncher : ArrangeLauncher {
        lateinit var last: ArrangeIntentSpec
        var launches: Int = 0

        override fun launch(spec: ArrangeIntentSpec): ArrangeLaunchResult {
            last = spec
            launches += 1
            return ArrangeLaunchResult(launched = true)
        }
    }

    private class FakeAlarmEngine(private val zone: ZoneId) : ArrangeAlarmEngine {
        private val alarms = mutableListOf<ArrangeAlarm>()
        private var nextId = 0
        var failCreate: Exception? = null
        var systemNext: ArrangeSystemAlarm? = null
        var exactAllowed = true

        override fun list(): List<ArrangeAlarm> = alarms.toList()

        override fun createAlarm(draft: ArrangeAlarmDraft): ArrangeAlarm {
            failCreate?.let { throw it }
            val now = System.currentTimeMillis()
            val alarm = ArrangeAlarm(
                id = "alarm-${++nextId}",
                label = draft.label.orEmpty(),
                hour = draft.hour,
                minute = draft.minute,
                days = draft.days,
                enabled = draft.enabled,
                timer = false,
                triggerAt = if (draft.enabled) nextAlarmTrigger(draft.hour, draft.minute, draft.days, now, zone) else 0L,
                createdAt = now,
            )
            alarms += alarm
            return alarm
        }

        override fun updateAlarm(id: String, draft: ArrangeAlarmDraft): ArrangeAlarm? {
            val index = alarms.indexOfFirst { it.id == id }
            if (index < 0) return null
            val existing = alarms[index]
            val updated = existing.copy(
                label = draft.label.orEmpty(),
                hour = draft.hour,
                minute = draft.minute,
                days = draft.days,
                enabled = draft.enabled,
                triggerAt = if (draft.enabled) nextAlarmTrigger(draft.hour, draft.minute, draft.days, System.currentTimeMillis(), zone) else 0L,
            )
            alarms[index] = updated
            return updated
        }

        override fun deleteAlarm(id: String): Boolean = alarms.removeAll { it.id == id }

        override fun createTimer(durationSeconds: Int, label: String?): ArrangeAlarm {
            val now = System.currentTimeMillis()
            val alarm = ArrangeAlarm(
                id = "timer-${++nextId}",
                label = label.orEmpty(),
                hour = 0,
                minute = 0,
                days = emptyList(),
                enabled = true,
                timer = true,
                triggerAt = now + durationSeconds * 1_000L,
                createdAt = now,
            )
            alarms += alarm
            return alarm
        }

        override fun nextSystemAlarm(): ArrangeSystemAlarm? = systemNext

        override fun canScheduleExactAlarms(): Boolean = exactAllowed

        override fun rescheduleAll() = Unit
    }

    private class FakeCalendarGateway : ArrangeCalendarGateway {
        private val events = mutableMapOf<Long, ArrangeCalendarEvent>()
        private var nextId = 10L
        var readAllowed = true
        var writeAllowed = true
        var lastDraft: ArrangeEventDraft? = null
        var lastPatch: ArrangeEventPatch? = null
        var lastLimit: Int? = null
        private val calendars = listOf(ArrangeCalendarInfo(1, "小米日历", "account", "com.xiaomi", true, true))

        private fun checkRead() {
            if (!readAllowed) throw ArrangeCalendarPermissionException("缺少日历读取权限")
        }

        private fun checkWrite() {
            if (!writeAllowed) throw ArrangeCalendarPermissionException("缺少日历写入权限")
        }

        override fun hasReadPermission(): Boolean = readAllowed

        override fun hasWritePermission(): Boolean = writeAllowed

        override fun calendars(): List<ArrangeCalendarInfo> {
            checkRead()
            return calendars
        }

        override fun list(startMillis: Long, endMillis: Long, query: String?, limit: Int): List<ArrangeCalendarEvent> {
            checkRead()
            lastLimit = limit
            return events.values
                .filter { it.startMillis < endMillis && it.endMillis > startMillis }
                .filter { query.isNullOrBlank() || it.title.contains(query) }
                .sortedBy { it.startMillis }
                .take(limit)
        }

        override fun get(id: Long): ArrangeCalendarEvent? {
            checkRead()
            return events[id]
        }

        override fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent {
            checkWrite()
            lastDraft = draft
            val end = draft.endMillis ?: (draft.startMillis + 60_000L)
            val event = ArrangeCalendarEvent(
                id = nextId++,
                calendarId = draft.calendarId ?: 1,
                title = draft.title,
                startMillis = draft.startMillis,
                endMillis = end,
                allDay = draft.allDay,
                location = draft.location,
                description = draft.description,
                timezone = "Asia/Shanghai",
                recurring = false,
                eventId = nextId,
            )
            events[event.id] = event
            return event
        }

        override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? {
            checkWrite()
            lastPatch = patch
            val current = events[id] ?: return null
            val updated = current.copy(
                title = patch.title ?: current.title,
                startMillis = patch.startMillis ?: current.startMillis,
                endMillis = patch.endMillis ?: current.endMillis,
                allDay = patch.allDay ?: current.allDay,
                location = patch.location ?: current.location,
                description = patch.description ?: current.description,
            )
            events[id] = updated
            return updated
        }

        override fun delete(id: Long): Boolean {
            checkWrite()
            return events.remove(id) != null
        }
    }
}
