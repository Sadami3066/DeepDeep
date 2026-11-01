package io.github.whywood.deepdeep.plugin.arrange

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    private val calendar = FakeCalendarGateway()
    private val tools = arrangeTools(calendar, zone)

    @Test
    fun exposesOnlyCalendarTool() {
        assertEquals(listOf("arrange_calendar"), tools.map { it.name })
    }

    @Test
    fun toolTextFollowsTaskLanguage() {
        val tool = tools.single()
        assertNull(tool.promptSnippet)
        assertNull(tool.promptGuidelines)
        val texts = buildList {
            add(tool.description)
            val properties = tool.parameters["properties"] as JsonObject
            properties.values.forEach { property ->
                val description = ((property as? JsonObject)?.get("description") as? JsonPrimitive)?.contentOrNull
                description?.let(::add)
            }
        }
        texts.forEach { text ->
            bannedWords.forEach { word ->
                assertFalse("$word in $text", text.contains(word, ignoreCase = true))
            }
        }
        assertFalse(tool.description.contains("CalendarContract"))
        assertFalse(tool.description.contains("provider"))
        assertTrue(tool.description.contains("calendar"))
    }

    @Test
    fun schemaDocumentsCrudActions() {
        val properties = tools.single().parameters["properties"] as JsonObject
        assertEquals(
            listOf("calendars", "list", "get", "create", "update", "delete"),
            ((properties["action"] as JsonObject)["enum"] as JsonArray).map { it.jsonPrimitive.content },
        )
        listOf("id", "calendarId", "title", "start", "end", "allDay", "location", "description", "reminders", "query", "limit")
            .forEach { field -> assertTrue(field, properties.containsKey(field)) }
        assertEquals(200, ((properties["limit"] as JsonObject)["maximum"] as JsonPrimitive).int)
    }

    @Test
    fun calendarsListsWritableCalendars() = runTest {
        val result = run("calendars")
        assertNull(result.isError)
        val list = result.json()["calendars"] as JsonArray
        assertEquals(1, list.size)
        assertEquals(1L, list.single().jsonObject["id"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun createWritesDraftAndReturnsEvent() = runTest {
        val result = run(
            "create",
            buildJsonObject {
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
    fun allDayCreateAndReadBackKeepRequestedDate() = runTest {
        listOf("Asia/Shanghai", "America/New_York", "UTC", "America/Santiago").forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val calendar = FakeCalendarGateway(zone)
            val tool = arrangeTools(calendar, zone).single()
            val expectedStart = LocalDate.of(2026, 10, 30)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            val created = tool.execute(
                "test-call",
                buildJsonObject {
                    put("action", "create")
                    put("title", "全天")
                    put("start", "2026-10-30")
                    put("allDay", true)
                },
                null,
                null,
            )

            assertNull(created.isError)
            assertEquals(zoneId, parseCalendarMillis("2026-10-30", zone), calendar.lastDraft!!.startMillis)
            val event = created.json()["event"]!!.jsonObject
            assertEquals(zoneId, expectedStart, event["start"]!!.jsonPrimitive.content.toLong())
            assertEquals(zoneId, "2026-10-30", event["startText"]!!.jsonPrimitive.content)
            val id = event["id"]!!.jsonPrimitive.content.toLong()

            val fetched = tool.execute(
                "test-call",
                buildJsonObject {
                    put("action", "get")
                    put("id", id)
                },
                null,
                null,
            )

            assertNull(fetched.isError)
            val readBack = fetched.json()["event"]!!.jsonObject
            assertEquals(zoneId, expectedStart, readBack["start"]!!.jsonPrimitive.content.toLong())
            assertEquals(zoneId, "2026-10-30", readBack["startText"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun listsEventsInRange() = runTest {
        calendar.create(ArrangeEventDraft("会议", parseCalendarMillis("2026-10-26 10:00", zone), null, false, null, null, 1, null))
        val result = run(
            "list",
            buildJsonObject {
                put("start", "2026-10-26 00:00")
                put("end", "2026-10-27 00:00")
                put("limit", 10)
            },
        )

        assertNull(result.isError)
        val json = result.json()
        assertEquals(1, (json["events"] as JsonArray).size)
        assertEquals(1, json["count"]!!.jsonPrimitive.int)
        assertEquals("2026-10-26 00:00", json["rangeStartText"]!!.jsonPrimitive.content)
        assertEquals("2026-10-27 00:00", json["rangeEndText"]!!.jsonPrimitive.content)
        assertEquals(10, calendar.lastLimit)
    }

    @Test
    fun emptyRangeReportsCountAndReadableBounds() = runTest {
        val result = run(
            "list",
            buildJsonObject {
                put("start", "2026-11-01 00:00")
                put("end", "2026-11-02 00:00")
            },
        )

        assertNull(result.isError)
        val json = result.json()
        assertEquals(0, (json["events"] as JsonArray).size)
        assertEquals(0, json["count"]!!.jsonPrimitive.int)
        assertEquals("2026-11-01 00:00", json["rangeStartText"]!!.jsonPrimitive.content)
        assertEquals("2026-11-02 00:00", json["rangeEndText"]!!.jsonPrimitive.content)
    }

    @Test
    fun getsSingleEvent() = runTest {
        val event = calendar.create(ArrangeEventDraft("会议", 1_000L, 2_000L, false, null, null, 1, null))
        val result = run("get", buildJsonObject { put("id", event.id) })

        assertNull(result.isError)
        assertEquals("会议", result.json()["event"]!!.jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun updateUsesPatchSemantics() = runTest {
        val event = calendar.create(ArrangeEventDraft("旧标题", 1_000L, 2_000L, false, null, null, 1, null))
        val result = run(
            "update",
            buildJsonObject {
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
    fun deletesEvent() = runTest {
        val event = calendar.create(ArrangeEventDraft("旧标题", 1_000L, 2_000L, false, null, null, 1, null))
        val result = run("delete", buildJsonObject { put("id", event.id) })

        assertNull(result.isError)
        assertNull(calendar.get(event.id))
    }

    @Test
    fun permissionFailureBecomesToolError() = runTest {
        calendar.readAllowed = false
        val result = run("calendars")
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("日历"))
    }

    @Test
    fun inputValidation() = runTest {
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
            assertTrue(run("lists", args).isError == true)
        }
    }

    private suspend fun run(action: String, extra: JsonObject = buildJsonObject { }): AgentToolResult {
        val args = buildJsonObject {
            put("action", action)
            extra.forEach { (key, value) -> put(key, value) }
        }
        return tools.single().execute("test-call", args, null, null)
    }

    private val bannedWords = listOf(
        "helper",
        "daemon",
        "uid",
        "uiautomator",
        "displayId",
        "taskDisplayId",
        "nodeBackend",
        "inputBackend",
        "CalendarContract",
        "backend",
        "engine",
    )

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun AgentToolResult.json(): JsonObject =
        kotlinx.serialization.json.Json.parseToJsonElement(text()).jsonObject

    private class FakeCalendarGateway(private val zone: ZoneId = ZoneId.systemDefault()) : ArrangeCalendarGateway {
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
            val range = resolveEventRange(draft.startMillis, draft.endMillis, draft.allDay, zone)
            val event = ArrangeCalendarEvent(
                id = nextId++,
                calendarId = draft.calendarId ?: 1,
                title = draft.title,
                startMillis = range.first,
                endMillis = range.second,
                allDay = draft.allDay,
                location = draft.location,
                description = draft.description,
                timezone = "Asia/Shanghai",
                recurring = false,
            )
            events[event.id] = event
            return event
        }

        override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? {
            checkWrite()
            lastPatch = patch
            val current = events[id] ?: return null
            val update = mergeEventPatch(current, patch, zone)
            val updated = current.copy(
                title = update.title ?: current.title,
                startMillis = update.startMillis,
                endMillis = update.endMillis,
                allDay = update.allDay,
                location = update.location ?: current.location,
                description = update.description ?: current.description,
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
