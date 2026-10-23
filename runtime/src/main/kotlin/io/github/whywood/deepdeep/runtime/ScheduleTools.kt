package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
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

private const val SCHEDULE_EXECUTION = "not_enabled"

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

private val createParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("prompt") {
            put("type", "string")
            put("description", "Prompt text sent to a new session when the schedule fires")
        }
        putJsonObject("frequency") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("once"))
                add(JsonPrimitive("daily"))
                add(JsonPrimitive("weekdays"))
                add(JsonPrimitive("weekends"))
                add(JsonPrimitive("weekly"))
            })
            put("description", "Trigger kind: once, daily, weekdays (Monday to Friday), weekends (Saturday and Sunday) or weekly")
        }
        putJsonObject("name") {
            put("type", "string")
            put("description", "Display name for the schedule (defaults to the prompt preview)")
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
        putJsonObject("workspace") {
            put("type", "string")
            put("description", "Workspace id, name or path (defaults to the current workspace)")
        }
        putJsonObject("notify") {
            put("type", "boolean")
            put("description", "Notify when the run finishes (default true)")
        }
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether the schedule is enabled (default true)")
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
            put("description", "New prompt text")
        }
        putJsonObject("frequency") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("once"))
                add(JsonPrimitive("daily"))
                add(JsonPrimitive("weekdays"))
                add(JsonPrimitive("weekends"))
                add(JsonPrimitive("weekly"))
            })
            put("description", "New trigger kind: once, daily, weekdays, weekends or weekly")
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
        putJsonObject("workspace") {
            put("type", "string")
            put("description", "Workspace id, name or path")
        }
        putJsonObject("notify") {
            put("type", "boolean")
            put("description", "Notify when the run finishes")
        }
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether the schedule is enabled")
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

fun scheduleTools(cwd: Path, context: AppToolContext): List<CodingTool> = listOf(
    scheduleCreateTool(cwd, context),
    scheduleListTool(cwd, context),
    scheduleUpdateTool(cwd, context),
    scheduleDeleteTool(cwd, context),
)

private fun scheduleCreateTool(cwd: Path, context: AppToolContext): CodingTool = CodingTool(
    name = "schedule_create",
    description = "Create a scheduled task for this app. The scheduler is not active yet: the task is saved and shown, but execution is disabled until a later release.",
    parameters = createParameters,
    label = "schedule_create",
    promptSnippet = "Create a scheduled task",
    promptGuidelines = listOf(
        "Use schedule_create when the user asks to run a prompt once at a specific time, every day, or on selected weekdays.",
        "Only create schedules when the user explicitly asks for a scheduled or recurring task.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val prompt = args.requiredString("prompt")
            val frequency = args.requiredString("frequency")
            val name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: promptPreview(prompt)
            val workspace = resolveWorkspace(cwd, context, args.stringOrNull("workspace"))
                ?: throw IllegalArgumentException("未找到工作区：${args.stringOrNull("workspace") ?: "当前工作区"}")
            val trigger = parseTrigger(args, base = null, requestedFrequency = frequency)
            val schedule = context.scheduleStore.create(
                NewSchedule(
                    name = name,
                    workspaceId = workspace.id,
                    prompt = prompt,
                    trigger = trigger,
                    enabled = args.boolOrNull("enabled") ?: true,
                    notify = args.boolOrNull("notify") ?: true,
                ),
            )
            textResult(scheduleJson(schedule).toString())
        }
    },
)

private fun scheduleListTool(cwd: Path, context: AppToolContext): CodingTool = CodingTool(
    name = "schedule_list",
    description = "List saved scheduled tasks with trigger, next run time, workspace and enabled state. Execution is not active yet.",
    parameters = listParameters,
    label = "schedule_list",
    promptSnippet = "List scheduled tasks",
    promptGuidelines = listOf("Use schedule_list before updating or deleting a schedule so you have the id."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val schedules = context.scheduleStore.list()
            val result = buildJsonObject {
                putJsonArray("schedules") {
                    schedules.forEach { add(scheduleJson(it)) }
                }
                put("execution", SCHEDULE_EXECUTION)
            }
            textResult(result.toString())
        }
    },
)

private fun scheduleUpdateTool(cwd: Path, context: AppToolContext): CodingTool = CodingTool(
    name = "schedule_update",
    description = "Update a saved scheduled task. Only provided fields change. The scheduler is not active yet.",
    parameters = updateParameters,
    label = "schedule_update",
    promptSnippet = "Update a scheduled task",
    promptGuidelines = listOf("Use schedule_list to find the schedule id before calling schedule_update."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val id = args.requiredString("id")
            val existing = context.scheduleStore.list().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            val workspaceArg = args.stringOrNull("workspace")?.trim()?.takeIf { it.isNotEmpty() }
            val workspace = workspaceArg?.let {
                resolveWorkspace(cwd, context, it) ?: throw IllegalArgumentException("未找到工作区：$it")
            }
            val hasTriggerArgs = listOf("frequency", "date", "time", "weekdays").any { args.containsKey(it) }
            val trigger = if (hasTriggerArgs) parseTrigger(args, base = existing.trigger, requestedFrequency = null) else null
            val patch = SchedulePatch(
                name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() },
                workspaceId = workspace?.id,
                prompt = args.stringOrNull("prompt")?.takeIf { it.isNotBlank() },
                trigger = trigger,
                enabled = args.boolOrNull("enabled"),
                notify = args.boolOrNull("notify"),
            )
            val updated = context.scheduleStore.update(id, patch)
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            textResult(scheduleJson(updated).toString())
        }
    },
)

private fun scheduleDeleteTool(cwd: Path, context: AppToolContext): CodingTool = CodingTool(
    name = "schedule_delete",
    description = "Delete a saved scheduled task by id.",
    parameters = deleteParameters,
    label = "schedule_delete",
    promptSnippet = "Delete a scheduled task",
    promptGuidelines = listOf("Confirm with the user before deleting a scheduled task."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            val id = args.requiredString("id")
            if (!context.scheduleStore.delete(id)) {
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
    put("workspaceId", schedule.workspaceId)
    put("enabled", schedule.enabled)
    put("notify", schedule.notify)
    put("execution", SCHEDULE_EXECUTION)
}

private suspend fun resolveWorkspace(cwd: Path, context: AppToolContext, raw: String?): RuntimeWorkspace? {
    val home = RuntimeWorkspace(
        id = AgentRuntimeHost.HOME_ID,
        name = AgentRuntimeHost.HOME_NAME,
        cwd = context.homeDir.absolutePath,
        isHome = true,
    )
    val workspaces = buildList {
        add(home)
        addAll(context.workspaceStore.read().filter { it.id != home.id })
    }
    val query = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return workspaceForPath(workspaces, cwd)
    return workspaces.firstOrNull { it.id == query }
        ?: workspaces.firstOrNull { it.name.equals(query, ignoreCase = true) }
        ?: workspaces.firstOrNull { samePath(it.cwd, query) }
}

private fun workspaceForPath(workspaces: List<RuntimeWorkspace>, cwd: Path): RuntimeWorkspace? {
    val current = cwd.toString()
    return workspaces.firstOrNull { samePath(it.cwd, current) }
}

private fun samePath(left: String, right: String): Boolean =
    runCatching { File(left).canonicalFile == File(right).canonicalFile }.getOrDefault(left == right)

private fun parseTrigger(args: JsonObject, base: ScheduleTrigger?, requestedFrequency: String?): ScheduleTrigger {
    val frequency = requestedFrequency?.trim()?.lowercase()
        ?: args.stringOrNull("frequency")?.trim()?.lowercase()
        ?: base?.let(::triggerKind)
        ?: throw IllegalArgumentException("缺少 frequency（once/daily/weekdays/weekends/weekly）")
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
        else -> throw IllegalArgumentException("frequency 只能是 once/daily/weekdays/weekends/weekly")
    }
}

private fun ScheduleTrigger.timeOf(): LocalTime = when (this) {
    is ScheduleTrigger.Once -> time
    is ScheduleTrigger.Daily -> time
    is ScheduleTrigger.Weekly -> time
}

private fun triggerKind(trigger: ScheduleTrigger): String = when (trigger) {
    is ScheduleTrigger.Once -> "once"
    is ScheduleTrigger.Daily -> "daily"
    is ScheduleTrigger.Weekly -> when (trigger.days) {
        WEEKDAY_DAYS -> "weekdays"
        WEEKEND_DAYS -> "weekends"
        else -> "weekly"
    }
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
    return if (normalized.length <= 28) normalized.ifEmpty { "定时任务" } else normalized.take(28) + "…"
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

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
