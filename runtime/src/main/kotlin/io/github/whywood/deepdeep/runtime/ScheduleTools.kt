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

private val TOOL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private val FREQUENCY_VALUES = buildJsonArray {
    add(JsonPrimitive("once"))
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
            put("description", "Trigger kind: once for a single date and time, cron for a five-field cron expression")
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
            put("description", "Local time in HH:mm 24-hour format, required for once")
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
            put("description", "New trigger kind: once or cron")
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
            put("description", "Local time in HH:mm 24-hour format for once triggers")
        }
        putJsonObject("cron") {
            put("type", "string")
            put("description", "Five-field cron expression in local time for cron triggers")
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

fun scheduleTools(cwd: Path, service: ScheduleService, binding: SessionBinding): List<CodingTool> = listOf(
    scheduleCreateTool(service, binding),
    scheduleListTool(service, binding),
    scheduleUpdateTool(service, binding),
    scheduleDeleteTool(service, binding),
)

private fun scheduleCreateTool(service: ScheduleService, binding: SessionBinding): CodingTool = CodingTool(
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
            val created = service.create(
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

private fun scheduleListTool(service: ScheduleService, binding: SessionBinding): CodingTool = CodingTool(
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
            val schedules = service.list()
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

private fun scheduleUpdateTool(service: ScheduleService, binding: SessionBinding): CodingTool = CodingTool(
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
            val existing = service.list()
                .firstOrNull { it.id == id && it.sessionId == session.sessionId }
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            val hasTriggerArgs = listOf("frequency", "date", "time", "cron").any { args.containsKey(it) }
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
            val updated = service.update(id, patch)
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            textResult(scheduleJson(updated).toString())
        }
    },
)

private fun scheduleDeleteTool(service: ScheduleService, binding: SessionBinding): CodingTool = CodingTool(
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
            val existing = service.list()
                .firstOrNull { it.id == id && it.sessionId == session.sessionId }
                ?: throw IllegalArgumentException("未找到定时任务：$id")
            if (!service.delete(existing.id)) {
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
        ?: when (base) {
            is ScheduleTrigger.Cron -> "cron"
            is ScheduleTrigger.Once -> "once"
            else -> null
        }
        ?: throw IllegalArgumentException("缺少 frequency（once/cron）")
    return when (frequency) {
        "once" -> {
            val baseOnce = base as? ScheduleTrigger.Once
            val date = args.stringOrNull("date")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::parseDate)
                ?: baseOnce?.date
                ?: throw IllegalArgumentException("once 需要 date（YYYY-MM-DD）")
            val time = parseTimeArg(args, baseOnce?.time)
                ?: throw IllegalArgumentException("once 需要 time（HH:mm）")
            ScheduleTrigger.Once(date, time)
        }
        "cron" -> {
            val baseCron = base as? ScheduleTrigger.Cron
            val expression = args.stringOrNull("cron")?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseCron?.expression
                ?: throw IllegalArgumentException("cron 需要 cron（5 段表达式）")
            ScheduleTrigger.Cron(expression)
        }
        else -> throw IllegalArgumentException("frequency 只能是 once/cron")
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

private fun promptPreview(prompt: String): String {
    val normalized = prompt.trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= 28) normalized.ifEmpty { "定时提醒" } else normalized.take(28) + "…"
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")

