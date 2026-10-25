package io.github.whywood.deepdeep.plugin.arrange

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
        assertEquals(1, (result.json()["events"] as JsonArray).size)
        assertEquals(10, calendar.lastLimit)
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

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun AgentToolResult.json(): JsonObject =
        kotlinx.serialization.json.Json.parseToJsonElement(text()).jsonObject

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
            val event = ArrangeCalendarEvent(
                id = nextId++,
                calendarId = draft.calendarId ?: 1,
                title = draft.title,
                startMillis = draft.startMillis,
                endMillis = draft.endMillis ?: (draft.startMillis + 60_000L),
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
