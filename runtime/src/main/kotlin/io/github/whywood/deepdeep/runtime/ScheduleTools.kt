package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okio.Path

private val DAY_ALIASES: Map<String, DayOfWeek> = buildMap {
    DayOfWeek.entries.forEach { day ->
        put(day.name.lowercase(), day)
        put(day.name.take(3).lowercase(), day)
    }
    put("周一", DayOfWeek.MONDAY)
    put("周二", DayOfWeek.TUESDAY)
    put("周三", DayOfWeek.WEDNESDAY)
    put("周四", DayOfWeek.THURSDAY)
    put("周五", DayOfWeek.FRIDAY)
    put("周六", DayOfWeek.SATURDAY)
    put("周日", DayOfWeek.SUNDAY)
    put("周天", DayOfWeek.SUNDAY)
}

private val WEEKDAY_DAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private val WEEKEND_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private val TOOL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private val FREQUENCY_VALUES = buildJsonArray {
    add(JsonPrimitive("once"))
    add(JsonPrimitive("daily"))
    add(JsonPrimitive("weekdays"))
    add(JsonPrimitive("weekends"))
    add(JsonPrimitive("weekly"))
    add(JsonPrimitive("interval"))
    add(JsonPrimitive("monthly"))
    add(JsonPrimitive("cron"))
}

private val createParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("prompt") {
            put("type", "string")
            put("description", "Message sent to the current conversation when the reminder fires")
        }
        putJsonObject("frequency") {
            put("type", "string")
            put("enum", FREQUENCY_VALUES)
            put("description", "Trigger kind: once, daily, weekdays (Monday to Friday), weekends (Saturday and Sunday), weekly, interval, monthly or cron")
        }
        putJsonObject("name") {
            put("type", "string")
            put("description", "Display name for the reminder (defaults to the prompt preview)")
        }
        putJsonObject("date") {
            put("type", "string")
            put("description", "Date in YYYY-MM-DD format, required for once")
        }
        putJsonObject("time") {
            put("type", "string")
            put("description", "Local time in HH:mm 24-hour format, required unless updating an existing trigger")
        }
        putJsonObject("weekdays") {
            put("type", "array")
            put("items", buildJsonObject { put("type", "string") })
            put("description", "Weekdays for weekly triggers: ISO numbers 1-7 (1=Monday) or names like MONDAY, WEDNESDAY, 周一")
        }
        putJsonObject("interval") {
            put("type", "number")
            put("description", "Repeat every N units for interval triggers: 1..1440 minutes, 1..24 hours or 1..365 days")
        }
        putJsonObject("interval_unit") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("minutes"))
                add(JsonPrimitive("hours"))
                add(JsonPrimitive("days"))
            })
            put("description", "Unit for interval triggers: minutes, hours or days")
        }
        putJsonObject("start_date") {
            put("type", "string")
            put("description", "YYYY-MM-DD start date for interval triggers in days; defaults to today")
        }
        putJsonObject("day_of_month") {
            put("type", "number")
            put("description", "Day of month 1..31 for monthly triggers; shorter months use their last day")
        }
        putJsonObject("cron") {
            put("type", "string")
            put("description", "Five-field cron expression in local time: minute hour day-of-month month day-of-week, e.g. 0 9 * * 1-5")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("prompt"))
        add(JsonPrimitive("frequency"))
    })
}

private val updateParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("id") {
            put("type", "string")
            put("description", "Schedule id")
        }
        putJsonObject("prompt") {
            put("type", "string")
            put("description", "New message text")
        }
        putJsonObject("frequency") {
            put("type", "string")
            put("enum", FREQUENCY_VALUES)
            put("description", "New trigger kind: once, daily, weekdays, weekends, weekly, interval, monthly or cron")
        }
        putJsonObject("name") {
            put("type", "string")
            put("description", "New display name")
        }
        putJsonObject("date") {
            put("type", "string")
            put("description", "Date in YYYY-MM-DD format for once triggers")
        }
        putJsonObject("time") {
            put("type", "string")
            put("description", "Local time in HH:mm 24-hour format")
        }
        putJsonObject("weekdays") {
            put("type", "array")
            put("items", buildJsonObject { put("type", "string") })
            put("description", "Weekdays for weekly triggers: ISO numbers 1-7 (1=Monday) or names like MONDAY, WEDNESDAY, 周一")
        }
        putJsonObject("interval") {
            put("type", "number")
            put("description", "Repeat every N units for interval triggers: 1..1440 minutes, 1..24 hours or 1..365 days")
        }
        putJsonObject("interval_unit") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("minutes"))
                add(JsonPrimitive("hours"))
                add(JsonPrimitive("days"))
            })
            put("description", "Unit for interval triggers: minutes, hours or days")
        }
        putJsonObject("start_date") {
            put("type", "string")
            put("description", "YYYY-MM-DD start date for interval triggers in days")
        }
        putJsonObject("day_of_month") {
            put("type", "number")
            put("description", "Day of month 1..31 for monthly triggers")
        }
        putJsonObject("cron") {
            put("type", "string")
            put("description", "Five-field cron expression in local time")
        }
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether the reminder is enabled")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("id")) })
}

private val listParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val deleteParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("id") {
            put("type", "string")
            put("description", "Schedule id")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("id")) })
}

fun scheduleTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = listOf(
    scheduleCreateTool(cwd, context, binding),
    scheduleListTool(cwd, context, binding),
    scheduleUpdateTool(cwd, context, binding),
    scheduleDeleteTool(cwd, context, binding),
)

private fun scheduleCreateTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(
    name = "schedule_create",
    description = "Create a reminder bound to the current conversation. When it fires, a reminder message is posted into this same conversation and starts a run here; no separate session or workspace is created.",
    parameters = createParameters,
    label = "schedule_create",
    promptSnippet = "Create a reminder in this conversation",
    promptGuidelines = listOf(
        "Use schedule_create when the user asks to be reminded once at a specific time, every day, or on selected weekdays.",
        "The reminder fires in the current conversation, so never ask for a target workspace.",
        "Only create reminders when the user explicitly asks for a scheduled or recurring reminder.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val session = requireSession(binding)
            val sessionFile = session.sessionFile
                ?: throw IllegalArgumentException("当前会话尚未保存，无法创建定时提醒")
            val prompt = args.requiredString("prompt")
            val frequency = args.requiredString("frequency")
            val name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: promptPreview(prompt)
            val trigger = parseTrigger(args, base = null, requestedFrequency = frequency)
            val created = context.scheduleService.create(
                NewSchedule(
                    name = name,
                    prompt = prompt,
                    trigger = trigger,
                    enabled = true,
                    sessionId = session.sessionId,
                    sessionFile = sessionFile,
                    cwd = session.cwd,
                ),
            )
            textResult(scheduleJson(created).toString())
        }
    },
)

private fun scheduleListTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(
    name = "schedule_list",
    description = "List the reminders bound to the current conversation with trigger, next run time, enabled state and last delivery result.",
    parameters = listParameters,
    label = "schedule_list",
    promptSnippet = "List reminders in this conversation",
    promptGuidelines = listOf("Use schedule_list before updating or deleting a reminder so you have the id."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val session = requireSession(binding)
            val schedules = context.scheduleService.list()
                .filter { it.sessionId == session.sessionId }
                .sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.createdAt }))
            val result = buildJsonObject {
                putJsonArray("schedules") {
                    schedules.forEach { add(scheduleJson(it)) }
                }
            }
            textResult(result.toString())
        }
    },
)

private fun scheduleUpdateTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(
    name = "schedule_update",
    description = "Update a reminder bound to the current conversation. Only provided fields change; the reminder cannot be rebound to another conversation.",
    parameters = updateParameters,
    label = "schedule_update",
    promptSnippet = "Update a reminder",
    promptGuidelines = listOf("Use schedule_list to find the reminder id before calling schedule_update."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val session = requireSession(binding)
            val id = args.requiredString("id")
            val existing = context.scheduleService.list()
                .firstOrNull { it.id == id && it.sessionId == session.sessionId }
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            val hasTriggerArgs = listOf(
                "frequency", "date", "time", "weekdays",
                "interval", "interval_unit", "start_date", "day_of_month", "cron",
            ).any { args.containsKey(it) }
            val trigger = if (hasTriggerArgs) {
                parseTrigger(args, base = existing.trigger, requestedFrequency = args.stringOrNull("frequency"))
            } else {
                null
            }
            val patch = SchedulePatch(
                name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() },
                prompt = args.stringOrNull("prompt")?.takeIf { it.isNotBlank() },
                trigger = trigger,
                enabled = args.boolOrNull("enabled"),
            )
            val updated = context.scheduleService.update(id, patch)
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            textResult(scheduleJson(updated).toString())
        }
    },
)

private fun scheduleDeleteTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(
    name = "schedule_delete",
    description = "Delete a reminder bound to the current conversation by id.",
    parameters = deleteParameters,
    label = "schedule_delete",
    promptSnippet = "Delete a reminder",
    promptGuidelines = listOf("Confirm with the user before deleting a reminder."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val session = requireSession(binding)
            val id = args.requiredString("id")
            val existing = context.scheduleService.list()
                .firstOrNull { it.id == id && it.sessionId == session.sessionId }
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            if (!context.scheduleService.delete(existing.id)) {
                throw IllegalArgumentException("未找到定时任务：$id")
            }
            val result = buildJsonObject {
                put("deleted", true)
                put("id", id)
            }
            textResult(result.toString())
        }
    },
)

private fun requireSession(binding: SessionBinding): BoundSession =
    binding.current() ?: throw IllegalArgumentException("当前会话未绑定")

private suspend fun scheduleToolResult(block: suspend () -> dev.pi.agent.AgentToolResult): dev.pi.agent.AgentToolResult =
    try {
        block()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        textResult(error.message ?: "定时任务操作失败", isError = true)
    }

private fun scheduleJson(schedule: Schedule): JsonObject = buildJsonObject {
    put("id", schedule.id)
    put("name", schedule.name)
    put("trigger", schedule.trigger.toJsonObject())
    if (schedule.nextRunAt == null) {
        put("nextRunAt", JsonNull)
    } else {
        put("nextRunAt", schedule.nextRunAt)
    }
    put("enabled", schedule.enabled)
    if (schedule.lastDeliveredAt == null) {
        put("lastDeliveredAt", JsonNull)
    } else {
        put("lastDeliveredAt", schedule.lastDeliveredAt)
    }
    if (schedule.lastError == null) {
        put("lastError", JsonNull)
    } else {
        put("lastError", schedule.lastError.name)
    }
    put("sessionId", schedule.sessionId)
}

private fun parseTrigger(args: JsonObject, base: ScheduleTrigger?, requestedFrequency: String?): ScheduleTrigger {
    val frequency = requestedFrequency?.trim()?.lowercase()
        ?: args.stringOrNull("frequency")?.trim()?.lowercase()
        ?: base?.let(::triggerKind)
        ?: throw IllegalArgumentException("缺少 frequency（once/daily/weekdays/weekends/weekly/interval/monthly/cron）")
    val baseTime = base?.timeOf()
    return when (frequency) {
        "once" -> {
            val baseOnce = base as? ScheduleTrigger.Once
            val date = args.stringOrNull("date")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::parseDate)
                ?: baseOnce?.date
                ?: throw IllegalArgumentException("once 需要 date（YYYY-MM-DD）")
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("once 需要 time（HH:mm）")
            ScheduleTrigger.Once(date, time)
        }
        "daily" -> {
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("daily 需要 time（HH:mm）")
            ScheduleTrigger.Daily(time)
        }
        "weekdays" -> {
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("weekdays 需要 time（HH:mm）")
            ScheduleTrigger.Weekly(WEEKDAY_DAYS, time)
        }
        "weekends" -> {
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("weekends 需要 time（HH:mm）")
            ScheduleTrigger.Weekly(WEEKEND_DAYS, time)
        }
        "weekly" -> {
            val baseWeekly = base as? ScheduleTrigger.Weekly
            val days = args.weekdaysOrNull()?.map(::parseWeekday)?.toSet()
                ?: baseWeekly?.days?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("weekly 需要 weekdays（ISO 1-7 或 MONDAY..SUNDAY）")
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("weekly 需要 time（HH:mm）")
            ScheduleTrigger.Weekly(days, time)
        }
        "interval" -> {
            val baseInterval = base as? ScheduleTrigger.Interval
            val every = args.intOrNull("interval")
                ?: baseInterval?.every
                ?: throw IllegalArgumentException("interval 需要 interval（重复次数）")
            val unit = args.stringOrNull("interval_unit")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::parseIntervalUnit)
                ?: baseInterval?.unit
                ?: throw IllegalArgumentException("interval 需要 interval_unit（minutes/hours/days）")
            if (unit == IntervalUnit.DAYS) {
                val time = parseTimeArg(args, baseInterval?.time ?: baseTime)
                    ?: throw IllegalArgumentException("interval 天需要 time（HH:mm）")
                val startDate = args.stringOrNull("start_date")?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let(::parseDate)
                    ?: baseInterval?.startDate
                    ?: LocalDate.now()
                ScheduleTrigger.Interval(every, unit, time, startDate)
            } else {
                ScheduleTrigger.Interval(every, unit)
            }
        }
        "monthly" -> {
            val baseMonthly = base as? ScheduleTrigger.Monthly
            val day = args.intOrNull("day_of_month")
                ?: baseMonthly?.day
                ?: throw IllegalArgumentException("monthly 需要 day_of_month（1..31）")
            val time = parseTimeArg(args, baseTime)
                ?: throw IllegalArgumentException("monthly 需要 time（HH:mm）")
            ScheduleTrigger.Monthly(day, time)
        }
        "cron" -> {
            val baseCron = base as? ScheduleTrigger.Cron
            val expression = args.stringOrNull("cron")?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseCron?.expression
                ?: throw IllegalArgumentException("cron 需要 cron（5 段表达式）")
            ScheduleTrigger.Cron(expression)
        }
        else -> throw IllegalArgumentException(
            "frequency 只能是 once/daily/weekdays/weekends/weekly/interval/monthly/cron",
        )
    }
}

private fun ScheduleTrigger.timeOf(): LocalTime? = when (this) {
    is ScheduleTrigger.Once -> time
    is ScheduleTrigger.Daily -> time
    is ScheduleTrigger.Weekly -> time
    is ScheduleTrigger.Interval -> time
    is ScheduleTrigger.Monthly -> time
    is ScheduleTrigger.Cron -> null
}

private fun triggerKind(trigger: ScheduleTrigger): String = when (trigger) {
    is ScheduleTrigger.Once -> "once"
    is ScheduleTrigger.Daily -> "daily"
    is ScheduleTrigger.Weekly -> when (trigger.days) {
        WEEKDAY_DAYS -> "weekdays"
        WEEKEND_DAYS -> "weekends"
        else -> "weekly"
    }
    is ScheduleTrigger.Interval -> "interval"
    is ScheduleTrigger.Monthly -> "monthly"
    is ScheduleTrigger.Cron -> "cron"
}

private fun parseTimeArg(args: JsonObject, fallback: LocalTime?): LocalTime? {
    val raw = args.stringOrNull("time")?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback
    return parseTime(raw)
}

private fun parseTime(raw: String): LocalTime {
    val iso = runCatching { LocalTime.parse(raw) }.getOrNull()
    if (iso != null) return iso
    return try {
        LocalTime.parse(raw, TOOL_TIME_FORMAT)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("时间格式无效：$raw（使用 HH:mm）")
    }
}

private fun parseIntervalUnit(raw: String): IntervalUnit = when (raw.trim().lowercase()) {
    "minutes", "minute", "min" -> IntervalUnit.MINUTES
    "hours", "hour" -> IntervalUnit.HOURS
    "days", "day" -> IntervalUnit.DAYS
    else -> throw IllegalArgumentException("interval_unit 只能是 minutes/hours/days")
}

private fun parseDate(raw: String): LocalDate = try {
    LocalDate.parse(raw)
} catch (error: DateTimeParseException) {
    throw IllegalArgumentException("日期格式无效：$raw（使用 YYYY-MM-DD）")
}

private fun parseWeekday(raw: String): DayOfWeek {
    val key = raw.trim().lowercase()
    val iso = key.toIntOrNull()
    if (iso != null) {
        return runCatching { DayOfWeek.of(iso) }
            .getOrElse { throw IllegalArgumentException("星期无效：$raw（使用 1..7）") }
    }
    return DAY_ALIASES[key]
        ?: throw IllegalArgumentException("星期无效：$raw（使用 1..7 或 MONDAY..SUNDAY / 周一..周日）")
}

private fun promptPreview(prompt: String): String {
    val normalized = prompt.trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= 28) normalized.ifEmpty { "定时提醒" } else normalized.take(28) + "…"
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")

private fun JsonObject.weekdaysOrNull(): List<String>? {
    val element = this["weekdays"] ?: return null
    val values = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> (element.contentOrNull ?: "").split(',')
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotEmpty() }
    return values.takeIf { it.isNotEmpty() }
}
