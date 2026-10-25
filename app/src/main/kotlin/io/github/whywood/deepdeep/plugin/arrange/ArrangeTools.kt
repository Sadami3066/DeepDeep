package io.github.whywood.deepdeep.plugin.arrange

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
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

private const val ALARM_LIST = "list"
private const val ALARM_SET = "set"
private const val ALARM_UPDATE = "update"
private const val ALARM_DELETE = "delete"
private const val ALARM_TIMER = "timer"
private const val ALARM_SHOW = "show"

private const val CALENDAR_CALENDARS = "calendars"
private const val CALENDAR_LIST = "list"
private const val CALENDAR_GET = "get"
private const val CALENDAR_CREATE = "create"
private const val CALENDAR_UPDATE = "update"
private const val CALENDAR_DELETE = "delete"
private const val CALENDAR_OPEN = "open"

private const val DEFAULT_CALENDAR_LIMIT = 50
private const val MAX_CALENDAR_LIMIT = 200
private const val MAX_REMINDER_MINUTES = 7 * 24 * 60
private const val DEFAULT_LIST_DAYS = 30L
private const val MAX_LIST_DAYS = 366L

private val EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

private val alarmParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put(
                "description",
                "list shows managed alarms; set creates one; update changes fields of an existing one; delete removes one; timer starts a countdown; show opens the system clock.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive(ALARM_LIST))
                add(JsonPrimitive(ALARM_SET))
                add(JsonPrimitive(ALARM_UPDATE))
                add(JsonPrimitive(ALARM_DELETE))
                add(JsonPrimitive(ALARM_TIMER))
                add(JsonPrimitive(ALARM_SHOW))
            }
        }
        putJsonObject("id") {
            put("type", "string")
            put("description", "Alarm id for action=update or delete, as returned by list or set.")
        }
        putJsonObject("time") {
            put("type", "string")
            put("description", "Alarm time in 24-hour local time as HH:mm for set or update. Cannot be combined with hour/minute.")
        }
        putJsonObject("hour") {
            put("type", "integer")
            put("description", "Alarm hour 0-23 for set or update; requires minute.")
            put("minimum", 0)
            put("maximum", 23)
        }
        putJsonObject("minute") {
            put("type", "integer")
            put("description", "Alarm minute 0-59 for set or update; requires hour.")
            put("minimum", 0)
            put("maximum", 59)
        }
        putJsonObject("label") {
            put("type", "string")
            put("description", "Optional label shown when the alarm rings; empty string clears it on update.")
        }
        putJsonObject("days") {
            put("type", "array")
            put(
                "description",
                "ISO weekdays for a repeating alarm: 1=Monday ... 7=Sunday. Omit or pass an empty array for a one-time alarm.",
            )
            putJsonObject("items") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 7)
            }
        }
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether the alarm is scheduled; defaults to true for set and is unchanged when omitted on update.")
        }
        putJsonObject("durationSeconds") {
            put("type", "integer")
            put(
                "description",
                "Timer length in seconds for action=timer, $MIN_TIMER_SECONDS-$MAX_TIMER_SECONDS.",
            )
            put("minimum", MIN_TIMER_SECONDS)
            put("maximum", MAX_TIMER_SECONDS)
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

private val calendarParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put(
                "description",
                "calendars lists writable calendars; list queries events in a time range; get reads one event; create, update and delete change the provider; open launches the system calendar.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive(CALENDAR_CALENDARS))
                add(JsonPrimitive(CALENDAR_LIST))
                add(JsonPrimitive(CALENDAR_GET))
                add(JsonPrimitive(CALENDAR_CREATE))
                add(JsonPrimitive(CALENDAR_UPDATE))
                add(JsonPrimitive(CALENDAR_DELETE))
                add(JsonPrimitive(CALENDAR_OPEN))
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
        putJsonObject("time") {
            put("type", "string")
            put(
                "description",
                "Optional time to focus, for action=open. Accepts the same formats as start.",
            )
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

private val alarmGuidelines = listOf(
    "Use arrange_alarm for wake-up calls, reminders and countdowns that the agent must manage by itself.",
    "Call list before update or delete, then pass the id returned by list, set or timer.",
    "After set, update, delete or timer, report exactly what the result says; never invent a different time or id.",
    "Days are ISO weekdays: 1=Monday ... 7=Sunday; omit days or pass [] for a one-time alarm.",
    "Alarms already stored in the system clock app cannot be read, changed or deleted; show only opens that app.",
)

private val calendarGuidelines = listOf(
    "Use arrange_calendar for appointments and events in the system calendar.",
    "Call calendars first when the user has several calendars or when no calendarId is known.",
    "Call list or get before update or delete so the id is current.",
    "Resolve relative dates into yyyy-MM-dd HH:mm local time using the current date.",
    "Calendar events are written directly to the system provider and sync through the account; report the returned event id and time.",
)

fun arrangeTools(
    launcher: ArrangeLauncher,
    alarms: ArrangeAlarmEngine,
    calendar: ArrangeCalendarGateway,
    zone: ZoneId = ZoneId.systemDefault(),
): List<CodingTool> = listOf(
    arrangeAlarmTool(launcher, alarms, zone),
    arrangeCalendarTool(launcher, calendar, zone),
)

private fun arrangeAlarmTool(
    launcher: ArrangeLauncher,
    alarms: ArrangeAlarmEngine,
    zone: ZoneId,
): CodingTool = CodingTool(
    name = "arrange_alarm",
    description = "Create, list, update and delete agent-managed alarms and timers, or open the system clock.",
    parameters = alarmParameters,
    label = "arrange_alarm",
    promptSnippet = "Manage alarms and timers",
    promptGuidelines = alarmGuidelines,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        arrangeToolResult {
            val action = args.requiredString("action").lowercase()
            withContext(Dispatchers.IO) {
                when (action) {
                    ALARM_LIST -> {
                        val items = alarms.list().sortedWith(compareBy({ it.triggerAt }, { it.label }))
                        val body = buildJsonObject {
                            putJsonArray("alarms") { items.forEach { add(alarmJson(it, zone)) } }
                            alarms.nextSystemAlarm()?.let { system ->
                                putJsonObject("systemNext") {
                                    put("at", system.triggerAt)
                                    put("atText", formatInstant(system.triggerAt, zone))
                                    system.label?.let { put("label", it) }
                                    put("readOnly", true)
                                }
                            }
                        }
                        textResult(body.toString())
                    }

                    ALARM_SET -> {
                        val time = args.requiredClockTime()
                        val label = args.optionalText("label", MAX_LABEL_LENGTH)
                        val days = normalizeIsoDays(args.isoDays())
                        val enabled = args.booleanOrNull("enabled") ?: true
                        val created = alarms.createAlarm(ArrangeAlarmDraft(time.hour, time.minute, label, days, enabled))
                        textResult(
                            buildJsonObject {
                                put("created", true)
                                put("alarm", alarmJson(created, zone))
                            }.toString(),
                        )
                    }

                    ALARM_UPDATE -> {
                        val id = args.requiredString("id")
                        val current = alarms.list().firstOrNull { it.id == id }
                            ?: throw ArrangeAlarmNotFoundException("没有找到闹钟 $id")
                        val time = args.optionalClockTime() ?: ClockTime(current.hour, current.minute)
                        val label = if (args.containsKey("label")) args.optionalText("label", MAX_LABEL_LENGTH)
                        else current.label
                        val days = if (args.containsKey("days")) normalizeIsoDays(args.isoDays())
                        else current.days
                        val enabled = args.booleanOrNull("enabled") ?: current.enabled
                        val updated = alarms.updateAlarm(
                            id,
                            ArrangeAlarmDraft(time.hour, time.minute, label, days, enabled),
                        ) ?: throw ArrangeAlarmNotFoundException("没有找到闹钟 $id")
                        textResult(
                            buildJsonObject {
                                put("updated", true)
                                put("alarm", alarmJson(updated, zone))
                            }.toString(),
                        )
                    }

                    ALARM_DELETE -> {
                        val id = args.requiredString("id")
                        val removed = alarms.deleteAlarm(id)
                        if (!removed) throw ArrangeAlarmNotFoundException("没有找到闹钟 $id")
                        textResult(buildJsonObject { put("deleted", id) }.toString())
                    }

                    ALARM_TIMER -> {
                        val duration = args.intOrNull("durationSeconds")
                            ?: throw IllegalArgumentException("action=timer 需要提供 durationSeconds")
                        val label = args.optionalText("label", MAX_LABEL_LENGTH)
                        val created = alarms.createTimer(duration, label)
                        textResult(
                            buildJsonObject {
                                put("created", true)
                                put("alarm", alarmJson(created, zone))
                            }.toString(),
                        )
                    }

                    ALARM_SHOW -> {
                        launch(launcher, alarmShowRequest())
                        textResult("已打开系统闹钟。系统时钟里已有的闹钟无法读取、修改或删除；App 只管理自己创建的闹钟。")
                    }

                    else -> throw IllegalArgumentException("action 只支持 list、set、update、delete、timer、show")
                }
            }
        }
    },
)

private fun arrangeCalendarTool(
    launcher: ArrangeLauncher,
    calendar: ArrangeCalendarGateway,
    zone: ZoneId,
): CodingTool = CodingTool(
    name = "arrange_calendar",
    description = "Read, create, update and delete calendar events through the system CalendarContract provider, or open the calendar app.",
    parameters = calendarParameters,
    label = "arrange_calendar",
    promptSnippet = "Manage system calendar events",
    promptGuidelines = calendarGuidelines,
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
                        if (start >= end) throw IllegalArgumentException("end 必须晚于 start")
                        val maxSpan = MAX_LIST_DAYS * 24L * 60L * 60L * 1_000L
                        if (end - start > maxSpan) throw IllegalArgumentException("list 范围不能超过 $MAX_LIST_DAYS 天")
                        val limit = args.intOrNull("limit") ?: DEFAULT_CALENDAR_LIMIT
                        if (limit !in 1..MAX_CALENDAR_LIMIT) {
                            throw IllegalArgumentException("limit 需要在 1..$MAX_CALENDAR_LIMIT 之间")
                        }
                        val query = args.stringOrNull("query")?.trim()?.takeIf { it.isNotEmpty() }
                        val events = calendar.list(start, end, query, limit)
                        textResult(
                            buildJsonObject {
                                put("rangeStart", start)
                                put("rangeEnd", end)
                                putJsonArray("events") { events.forEach { add(eventJson(it, zone)) } }
                            }.toString(),
                        )
                    }

                    CALENDAR_GET -> {
                        val id = args.requiredLong("id")
                        val event = calendar.get(id)
                            ?: throw IllegalArgumentException("没有找到日程 $id")
                        textResult(buildJsonObject { put("event", eventJson(event, zone)) }.toString())
                    }

                    CALENDAR_CREATE -> {
                        val title = args.optionalText("title", MAX_LABEL_LENGTH)
                            ?: throw IllegalArgumentException("action=create 需要提供 title")
                        val start = parseCalendarMillis(args.requiredString("start"), zone)
                        val end = args.optionalText("end", MAX_TEXT_LENGTH)
                            ?.let { parseCalendarMillis(it, zone) }
                        if (end != null && end <= start) throw IllegalArgumentException("end 必须晚于 start")
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
                            ?: throw IllegalArgumentException("没有找到日程 $id")
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
                        if (!removed) throw IllegalArgumentException("没有找到日程 $id")
                        textResult(buildJsonObject { put("deleted", id) }.toString())
                    }

                    CALENDAR_OPEN -> {
                        val time = args.stringOrNull("time")?.takeIf { it.isNotBlank() }
                            ?.let { parseCalendarMillis(it, zone) }
                        launch(launcher, calendarOpenRequest(time))
                        if (time == null) {
                            textResult("已打开系统日历。")
                        } else {
                            textResult("已打开系统日历；请求定位到 ${formatCalendarTime(time, allDay = false, zone = zone)}。")
                        }
                    }

                    else -> throw IllegalArgumentException("action 只支持 calendars、list、get、create、update、delete、open")
                }
            }
        }
    },
)

private fun alarmJson(alarm: ArrangeAlarm, zone: ZoneId): JsonObject = buildJsonObject {
    put("id", alarm.id)
    put("time", "%02d:%02d".format(alarm.hour, alarm.minute))
    put("label", alarm.label)
    putJsonArray("days") { alarm.days.forEach { add(JsonPrimitive(it)) } }
    put("enabled", alarm.enabled)
    put("kind", if (alarm.timer) "timer" else "alarm")
    put("triggerAt", alarm.triggerAt)
    put("triggerAtText", formatInstant(alarm.triggerAt, zone))
}

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

private fun formatInstant(millis: Long, zone: ZoneId): String =
    EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(zone))

private suspend fun arrangeToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: ArrangeExactAlarmException) {
    textResult(error.message ?: "需要允许精确闹钟", isError = true)
} catch (error: ArrangeCalendarPermissionException) {
    textResult(error.message ?: "日历权限未授予", isError = true)
} catch (error: ArrangeAlarmNotFoundException) {
    textResult(error.message ?: "没有找到这个闹钟", isError = true)
} catch (error: Exception) {
    textResult(error.message ?: "安排操作失败", isError = true)
}

private fun launch(launcher: ArrangeLauncher, spec: ArrangeIntentSpec) {
    val result = launcher.launch(spec)
    if (!result.launched) throw IllegalStateException(result.errorMessage ?: "无法打开系统应用")
}

private fun JsonObject.requiredClockTime(): ClockTime = optionalClockTime()
    ?: throw IllegalArgumentException("action=set 需要提供 time，或同时提供 hour 与 minute")

private fun JsonObject.optionalClockTime(): ClockTime? {
    val time = stringOrNull("time")?.takeIf { it.isNotBlank() }
    val hour = intOrNull("hour")
    val minute = intOrNull("minute")
    if (time != null) {
        if (hour != null || minute != null) throw IllegalArgumentException("time 与 hour、minute 只能提供一种")
        return parseClockTime(time)
    }
    if (hour == null && minute == null) return null
    if (hour == null || minute == null) throw IllegalArgumentException("hour 与 minute 需要同时提供")
    return clockTime(hour, minute)
}

private fun JsonObject.isoDays(): List<Int> {
    val array = this["days"] as? JsonArray ?: return emptyList()
    return array.map { element ->
        val raw = (element as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("days 需要使用 1..7 的整数")
        raw.toIntOrNull() ?: throw IllegalArgumentException("days 需要使用 1..7 的整数")
    }
}

private fun JsonObject.reminderMinutes(): List<Int>? {
    val array = this["reminders"] as? JsonArray ?: return null
    return array.map { element ->
        val raw = (element as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("reminders 需要使用分钟整数")
        val value = raw.toIntOrNull() ?: throw IllegalArgumentException("reminders 需要使用分钟整数")
        if (value !in 0..MAX_REMINDER_MINUTES) {
            throw IllegalArgumentException("reminders 需要在 0..$MAX_REMINDER_MINUTES 分钟之间")
        }
        value
    }.distinct().sorted()
}

private fun JsonObject.requiredLong(name: String): Long =
    longOrNull(name) ?: throw IllegalArgumentException("缺少参数 $name")

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")

private fun JsonObject.optionalText(name: String, maxLength: Int): String? =
    boundedOptionalText(stringOrNull(name), maxLength, name)

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
