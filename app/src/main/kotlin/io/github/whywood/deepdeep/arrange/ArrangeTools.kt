package io.github.whywood.deepdeep.arrange

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val CALENDAR_CALENDARS = "calendars"
private const val CALENDAR_LIST = "list"
private const val CALENDAR_GET = "get"
private const val CALENDAR_CREATE = "create"
private const val CALENDAR_UPDATE = "update"
private const val CALENDAR_DELETE = "delete"

private const val DEFAULT_CALENDAR_LIMIT = 50
private const val MAX_CALENDAR_LIMIT = 200
private const val MAX_REMINDER_MINUTES = 7 * 24 * 60
private const val DEFAULT_LIST_DAYS = 30L
private const val MAX_LIST_DAYS = 366L

private val EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

private val calendarParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put(
                "description",
                "calendars lists writable calendars; list queries events in a time range; get reads one event; create, update and delete change calendar events.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive(CALENDAR_CALENDARS))
                add(JsonPrimitive(CALENDAR_LIST))
                add(JsonPrimitive(CALENDAR_GET))
                add(JsonPrimitive(CALENDAR_CREATE))
                add(JsonPrimitive(CALENDAR_UPDATE))
                add(JsonPrimitive(CALENDAR_DELETE))
            }
        }
        putJsonObject("id") {
            put("type", "string")
            put("description", "Calendar event id for get, update or delete.")
        }
        putJsonObject("calendarId") {
            put("type", "integer")
            put("description", "Target calendar id from action=calendars. Defaults to the primary writable calendar.")
        }
        putJsonObject("title") {
            put("type", "string")
            put("description", "Event title for create, or a new title for update.")
        }
        putJsonObject("start") {
            put("type", "string")
            put(
                "description",
                "Event start (create/update) or range start (list). Accepts yyyy-MM-dd HH:mm, yyyy-MM-dd, ISO 8601 or epoch milliseconds.",
            )
        }
        putJsonObject("end") {
            put("type", "string")
            put(
                "description",
                "Event end (create/update) or range end (list), same formats as start. Defaults to one hour after start.",
            )
        }
        putJsonObject("allDay") {
            put("type", "boolean")
            put("description", "Whether the event lasts the whole day; defaults to false.")
        }
        putJsonObject("location") {
            put("type", "string")
            put("description", "Optional event location; empty string clears it on update.")
        }
        putJsonObject("description") {
            put("type", "string")
            put("description", "Optional event notes; empty string clears them on update.")
        }
        putJsonObject("reminders") {
            put("type", "array")
            put(
                "description",
                "Alerts in minutes before the event. Pass an empty array on update to remove existing reminders.",
            )
            putJsonObject("items") {
                put("type", "integer")
                put("minimum", 0)
                put("maximum", MAX_REMINDER_MINUTES)
            }
        }
        putJsonObject("query") {
            put("type", "string")
            put("description", "Optional title filter for action=list.")
        }
        putJsonObject("limit") {
            put("type", "integer")
            put("description", "Maximum events for action=list, 1-$MAX_CALENDAR_LIMIT. Defaults to $DEFAULT_CALENDAR_LIMIT.")
            put("minimum", 1)
            put("maximum", MAX_CALENDAR_LIMIT)
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

fun arrangeTools(
    calendar: ArrangeCalendarGateway,
    zone: ZoneId = ZoneId.systemDefault(),
): List<CodingTool> = listOf(arrangeCalendarTool(calendar, zone))

internal const val ARRANGE_CALENDAR_GUIDELINE =
    "Calendar writes change the user's schedule; confirm with the user before creating, updating or deleting an event."

private fun arrangeCalendarTool(
    calendar: ArrangeCalendarGateway,
    zone: ZoneId,
): CodingTool = CodingTool(
    name = "arrange_calendar",
    description = "Read, create, update or delete calendar events in the phone calendar.",
    parameters = calendarParameters,
    label = "arrange_calendar",
    promptGuidelines = listOf(ARRANGE_CALENDAR_GUIDELINE),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        arrangeToolResult {
            val action = args.requiredString("action").lowercase()
            withContext(Dispatchers.IO) {
                when (action) {
                    CALENDAR_CALENDARS -> {
                        val list = calendar.calendars()
                        textResult(
                            buildJsonObject {
                                putJsonArray("calendars") {
                                    list.forEach { info ->
                                        add(
                                            buildJsonObject {
                                                put("id", info.id)
                                                put("name", info.name)
                                                put("account", info.accountName)
                                                put("accountType", info.accountType)
                                                put("writable", info.writable)
                                                put("primary", info.primary)
                                            },
                                        )
                                    }
                                }
                            }.toString(),
                        )
                    }

                    CALENDAR_LIST -> {
                        val now = Instant.now().toEpochMilli()
                        val start = args.stringOrNull("start")?.takeIf { it.isNotBlank() }
                            ?.let { parseCalendarMillis(it, zone) } ?: now
                        val end = args.stringOrNull("end")?.takeIf { it.isNotBlank() }
                            ?.let { parseCalendarMillis(it, zone) }
                            ?: (start + DEFAULT_LIST_DAYS * 24L * 60L * 60L * 1_000L)
                        if (start >= end) throw IllegalArgumentException("end must be after start")
                        val maxSpan = MAX_LIST_DAYS * 24L * 60L * 60L * 1_000L
                        if (end - start > maxSpan) throw IllegalArgumentException("list range must not exceed $MAX_LIST_DAYS days")
                        val limit = args.intOrNull("limit") ?: DEFAULT_CALENDAR_LIMIT
                        if (limit !in 1..MAX_CALENDAR_LIMIT) {
                            throw IllegalArgumentException("limit must be between 1 and $MAX_CALENDAR_LIMIT")
                        }
                        val query = args.stringOrNull("query")?.trim()?.takeIf { it.isNotEmpty() }
                        val events = calendar.list(start, end, query, limit)
                        textResult(
                            buildJsonObject {
                                put("rangeStart", start)
                                put("rangeStartText", formatCalendarTime(start, allDay = false, zone = zone))
                                put("rangeEnd", end)
                                put("rangeEndText", formatCalendarTime(end, allDay = false, zone = zone))
                                put("count", events.size)
                                putJsonArray("events") { events.forEach { add(eventJson(it, zone)) } }
                            }.toString(),
                        )
                    }

                    CALENDAR_GET -> {
                        val id = args.requiredLong("id")
                        val event = calendar.get(id)
                            ?: throw IllegalArgumentException("Event $id not found")
                        textResult(buildJsonObject { put("event", eventJson(event, zone)) }.toString())
                    }

                    CALENDAR_CREATE -> {
                        val title = args.optionalText("title", MAX_LABEL_LENGTH)
                            ?: throw IllegalArgumentException("action=create requires title")
                        val start = parseCalendarMillis(args.requiredString("start"), zone)
                        val end = args.optionalText("end", MAX_TEXT_LENGTH)
                            ?.let { parseCalendarMillis(it, zone) }
                        if (end != null && end <= start) throw IllegalArgumentException("end must be after start")
                        val allDay = args.booleanOrNull("allDay") ?: false
                        val draft = ArrangeEventDraft(
                            title = title,
                            startMillis = start,
                            endMillis = end,
                            allDay = allDay,
                            location = args.optionalText("location", MAX_TEXT_LENGTH),
                            description = args.optionalText("description", MAX_TEXT_LENGTH),
                            calendarId = args.longOrNull("calendarId"),
                            reminderMinutes = args.reminderMinutes(),
                        )
                        val created = calendar.create(draft)
                        textResult(
                            buildJsonObject {
                                put("created", true)
                                put("event", eventJson(created, zone))
                            }.toString(),
                        )
                    }

                    CALENDAR_UPDATE -> {
                        val id = args.requiredLong("id")
                        val patch = ArrangeEventPatch(
                            title = if (args.containsKey("title")) args.optionalText("title", MAX_LABEL_LENGTH) else null,
                            startMillis = args.stringOrNull("start")?.takeIf { it.isNotBlank() }
                                ?.let { parseCalendarMillis(it, zone) },
                            endMillis = args.stringOrNull("end")?.takeIf { it.isNotBlank() }
                                ?.let { parseCalendarMillis(it, zone) },
                            allDay = args.booleanOrNull("allDay"),
                            location = if (args.containsKey("location")) args.stringOrNull("location") else null,
                            description = if (args.containsKey("description")) args.stringOrNull("description") else null,
                            calendarId = args.longOrNull("calendarId"),
                            reminderMinutes = if (args.containsKey("reminders")) args.reminderMinutes() else null,
                        )
                        val updated = calendar.update(id, patch)
                            ?: throw IllegalArgumentException("Event $id not found")
                        textResult(
                            buildJsonObject {
                                put("updated", true)
                                put("event", eventJson(updated, zone))
                            }.toString(),
                        )
                    }

                    CALENDAR_DELETE -> {
                        val id = args.requiredLong("id")
                        val removed = calendar.delete(id)
                        if (!removed) throw IllegalArgumentException("Event $id not found")
                        textResult(buildJsonObject { put("deleted", id) }.toString())
                    }

                    else -> throw IllegalArgumentException("action must be calendars, list, get, create, update or delete")
                }
            }
        }
    },
)

private fun eventJson(event: ArrangeCalendarEvent, zone: ZoneId): JsonObject = buildJsonObject {
    put("id", event.id)
    put("calendarId", event.calendarId)
    put("title", event.title)
    put("start", event.startMillis)
    put("end", event.endMillis)
    put("startText", formatCalendarTime(event.startMillis, event.allDay, zone))
    put("endText", formatCalendarTime(event.endMillis, event.allDay, zone))
    put("allDay", event.allDay)
    event.location?.let { put("location", it) }
    event.description?.let { put("description", it) }
    event.timezone?.let { put("timezone", it) }
    put("recurring", event.recurring)
    put("eventId", event.eventId)
}

private suspend fun arrangeToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: ArrangeCalendarPermissionException) {
    textResult(error.message ?: "Calendar permission not granted", isError = true)
} catch (error: Exception) {
    textResult(error.message ?: "Arrange action failed", isError = true)
}

private fun JsonObject.reminderMinutes(): List<Int>? {
    val array = this["reminders"] as? JsonArray ?: return null
    return array.map { element ->
        val raw = (element as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("reminders must be integer minutes")
        val value = raw.toIntOrNull() ?: throw IllegalArgumentException("reminders must be integer minutes")
        if (value !in 0..MAX_REMINDER_MINUTES) {
            throw IllegalArgumentException("reminders must be between 0 and $MAX_REMINDER_MINUTES minutes")
        }
        value
    }.distinct().sorted()
}

private fun JsonObject.requiredLong(name: String): Long =
    longOrNull(name) ?: throw IllegalArgumentException("Missing required argument: $name")

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required argument: $name")

private fun JsonObject.optionalText(name: String, maxLength: Int): String? =
    boundedOptionalText(stringOrNull(name), maxLength, name)

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
