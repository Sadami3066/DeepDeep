package io.github.whywood.deepdeep.plugin.arrange

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val ACTION_SET = "set"
private const val ACTION_TIMER = "timer"
private const val ACTION_SHOW = "show"
private const val ACTION_CREATE = "create"
private const val ACTION_OPEN = "open"

private val alarmParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("description", "Alarm operation: set creates an alarm, timer starts a countdown, show opens the alarm list.")
            putJsonArray("enum") {
                add(JsonPrimitive(ACTION_SET))
                add(JsonPrimitive(ACTION_TIMER))
                add(JsonPrimitive(ACTION_SHOW))
            }
        }
        putJsonObject("time") {
            put("type", "string")
            put("description", "Alarm time in 24-hour local time as HH:mm, for action=set. Cannot be combined with hour/minute.")
        }
        putJsonObject("hour") {
            put("type", "integer")
            put("description", "Alarm hour 0-23 for action=set; requires minute.")
            put("minimum", 0)
            put("maximum", 23)
        }
        putJsonObject("minute") {
            put("type", "integer")
            put("description", "Alarm minute 0-59 for action=set; requires hour.")
            put("minimum", 0)
            put("maximum", 59)
        }
        putJsonObject("label") {
            put("type", "string")
            put("description", "Optional alarm or timer label shown by the system clock app.")
        }
        putJsonObject("days") {
            put("type", "array")
            put(
                "description",
                "ISO weekdays for a repeating alarm: 1=Monday ... 7=Sunday. Omit for a one-time alarm.",
            )
            putJsonObject("items") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 7)
            }
        }
        putJsonObject("skipUi") {
            put("type", "boolean")
            put("description", "Request silent creation without the confirmation screen; some system clock apps ignore it.")
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
            put("description", "create opens a prefilled system calendar insert screen; open launches the calendar app.")
            putJsonArray("enum") {
                add(JsonPrimitive(ACTION_CREATE))
                add(JsonPrimitive(ACTION_OPEN))
            }
        }
        putJsonObject("title") {
            put("type", "string")
            put("description", "Event title, for action=create.")
        }
        putJsonObject("start") {
            put("type", "string")
            put(
                "description",
                "Event start, for action=create. Accepts yyyy-MM-dd HH:mm, yyyy-MM-dd, ISO 8601 or epoch milliseconds.",
            )
        }
        putJsonObject("end") {
            put("type", "string")
            put(
                "description",
                "Optional event end in the same formats as start; defaults to one hour after start when allDay is false.",
            )
        }
        putJsonObject("allDay") {
            put("type", "boolean")
            put("description", "Whether the event lasts the whole day; defaults to false.")
        }
        putJsonObject("location") {
            put("type", "string")
            put("description", "Optional event location.")
        }
        putJsonObject("description") {
            put("type", "string")
            put("description", "Optional event notes.")
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

fun arrangeTools(launcher: ArrangeLauncher, zone: ZoneId = ZoneId.systemDefault()): List<CodingTool> = listOf(
    arrangeAlarmTool(launcher),
    arrangeCalendarTool(launcher, zone),
)

private fun arrangeAlarmTool(launcher: ArrangeLauncher): CodingTool = CodingTool(
    name = "arrange_alarm",
    description = "Create a system clock alarm or timer, or open the system alarm list, through the standard AlarmClock intents.",
    parameters = alarmParameters,
    label = "arrange_alarm",
    promptSnippet = "Set a system alarm or timer",
    promptGuidelines = listOf(
        "Use arrange_alarm for wake-up calls, reminders and countdowns that must ring through the system clock app.",
        "Never claim an alarm or timer was saved: the system may show a confirmation screen and DeepDeep cannot read or delete saved alarms.",
        "Pass skipUi=true only when the user asks to create silently; some system clock apps still ask for confirmation.",
        "Days are ISO weekdays: 1=Monday ... 7=Sunday; omit days for a one-time alarm.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        arrangeToolResult {
            when (args.requiredString("action").lowercase()) {
                ACTION_SET -> {
                    val time = args.resolveClockTime()
                    val label = args.optionalText("label", MAX_LABEL_LENGTH)
                    val days = normalizeIsoDays(args.isoDays())
                    val skipUi = args.booleanOrNull("skipUi") ?: false
                    launch(launcher, alarmSetRequest(time, label, days, skipUi))
                    textResult(alarmSetMessage(time, label, days, skipUi))
                }

                ACTION_TIMER -> {
                    val durationSeconds = args.intOrNull("durationSeconds")
                        ?: throw IllegalArgumentException("action=timer 需要提供 durationSeconds")
                    val label = args.optionalText("label", MAX_LABEL_LENGTH)
                    launch(launcher, alarmTimerRequest(durationSeconds, label))
                    textResult(timerMessage(durationSeconds, label))
                }

                ACTION_SHOW -> {
                    launch(launcher, alarmShowRequest())
                    textResult("已打开系统闹钟。")
                }

                else -> throw IllegalArgumentException("action 只支持 set、timer、show")
            }
        }
    },
)

private fun arrangeCalendarTool(launcher: ArrangeLauncher, zone: ZoneId): CodingTool = CodingTool(
    name = "arrange_calendar",
    description = "Open the system calendar on a prefilled insert screen, or launch the calendar app, through CalendarContract.",
    parameters = calendarParameters,
    label = "arrange_calendar",
    promptSnippet = "Create or open a system calendar event",
    promptGuidelines = listOf(
        "Use arrange_calendar when the user wants an appointment or event in the system calendar.",
        "create only opens the system insert screen; the event is not saved until the user confirms it in the calendar app.",
        "Never claim the event was saved: DeepDeep cannot read, modify or delete saved calendar events.",
        "Resolve relative dates from the user into yyyy-MM-dd HH:mm local time using the current date.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        arrangeToolResult {
            when (args.requiredString("action").lowercase()) {
                ACTION_CREATE -> {
                    val title = args.optionalText("title", MAX_LABEL_LENGTH)
                        ?: throw IllegalArgumentException("action=create 需要提供 title")
                    val start = parseCalendarMillis(args.requiredString("start"), zone)
                    val end = args.stringOrNull("end")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parseCalendarMillis(it, zone) }
                    val allDay = args.booleanOrNull("allDay") ?: false
                    val location = args.optionalText("location", MAX_TEXT_LENGTH)
                    val description = args.optionalText("description", MAX_TEXT_LENGTH)
                    launch(launcher, calendarCreateRequest(title, start, end, allDay, location, description))
                    textResult(calendarCreateMessage(title, start, end, allDay, zone))
                }

                ACTION_OPEN -> {
                    val time = args.stringOrNull("time")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parseCalendarMillis(it, zone) }
                    launch(launcher, calendarOpenRequest(time))
                    textResult(calendarOpenMessage(time, zone))
                }

                else -> throw IllegalArgumentException("action 只支持 create、open")
            }
        }
    },
)

private fun alarmSetMessage(time: ClockTime, label: String?, days: List<Int>, skipUi: Boolean): String = buildString {
    append("已请求设置闹钟 ").append(time.label)
    if (!label.isNullOrBlank()) append("「").append(label).append("」")
    append("（").append(formatIsoDays(days)).append("）")
    if (skipUi) {
        append("，skipUi=true：请求系统静默创建，部分系统仍会显示确认界面")
    } else {
        append("，系统可能会打开确认界面")
    }
    append("；请在系统闹钟中确认。App 无法读取或删除已保存的闹钟。")
}

private fun timerMessage(durationSeconds: Int, label: String?): String = buildString {
    append("已请求创建计时器 ").append(formatDuration(durationSeconds))
    if (!label.isNullOrBlank()) append("「").append(label).append("」")
    append("；系统可能会打开确认界面，请在系统时钟中确认。")
}

private fun calendarCreateMessage(
    title: String,
    startMillis: Long,
    endMillis: Long?,
    allDay: Boolean,
    zone: ZoneId,
): String {
    val resolvedEnd = endMillis ?: if (allDay) null else startMillis + DEFAULT_CALENDAR_DURATION_MILLIS
    return buildString {
        append("已打开系统日历新建页，未确认保存：").append(title)
        append("（").append(formatCalendarRange(startMillis, resolvedEnd, allDay, zone))
        if (allDay) append("，全天")
        append("）。请在系统日历中确认保存；App 无法读取或删除已保存的日程。")
    }
}

private fun calendarOpenMessage(timeMillis: Long?, zone: ZoneId): String = if (timeMillis == null) {
    "已打开系统日历。"
} else {
    "已打开系统日历；请求定位到 ${formatCalendarTime(timeMillis, allDay = false, zone = zone)}，是否生效取决于系统日历。"
}

private suspend fun arrangeToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "安排操作失败", isError = true)
}

private fun launch(launcher: ArrangeLauncher, spec: ArrangeIntentSpec) {
    val result = launcher.launch(spec)
    if (!result.launched) throw IllegalStateException(result.errorMessage ?: "无法打开系统应用")
}

private fun JsonObject.resolveClockTime(): ClockTime {
    val time = stringOrNull("time")?.takeIf { it.isNotBlank() }
    val hour = intOrNull("hour")
    val minute = intOrNull("minute")
    if (time != null) {
        if (hour != null || minute != null) throw IllegalArgumentException("time 与 hour、minute 只能提供一种")
        return parseClockTime(time)
    }
    if (hour == null || minute == null) {
        throw IllegalArgumentException("action=set 需要提供 time，或同时提供 hour 与 minute")
    }
    return clockTime(hour, minute)
}

private fun JsonObject.isoDays(): List<Int> {
    val array = this["days"] as? JsonArray ?: return emptyList()
    return array.map { element ->
        val raw = (element as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("days 需要使用 1..7 的整数")
        raw.toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException("days 需要使用 1..7 的整数")
    }
}

private fun JsonObject.optionalText(name: String, maxLength: Int): String? =
    boundedOptionalText(stringOrNull(name), maxLength, name)

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")
