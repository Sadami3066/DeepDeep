package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val TOOL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private val FREQUENCY_VALUES = buildJsonArray {
    add(JsonPrimitive("once"))
    add(JsonPrimitive("cron"))
}

private val SCHEDULE_ACTION_VALUES = buildJsonArray {
    add(JsonPrimitive("create"))
    add(JsonPrimitive("list"))
    add(JsonPrimitive("update"))
    add(JsonPrimitive("delete"))
}

private val scheduleParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("enum", SCHEDULE_ACTION_VALUES)
            put("description", "create, list, update or delete")
        }
        putJsonObject("id") {
            put("type", "string")
            put("description", "Schedule id, required for update and delete")
        }
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
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether the reminder is enabled")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

fun scheduleTools(service: ScheduleService, binding: SessionBinding): List<CodingTool> =
    scheduleTool(service, binding).let { delegate ->
        val editable = setOf("prompt", "name", "frequency", "date", "time", "cron", "enabled")
        listOf(
            toolVariant(delegate, "schedule_create", "Create a reminder in this conversation. once needs date/time, cron needs a five-field expression. enabled defaults to true; false creates a disabled reminder.", editable, setOf("prompt", "frequency"), "create"),
            toolVariant(delegate, "schedule_list", "List reminders in this conversation with their ids, schedules, enabled state and delivery results.", emptySet(), fixedAction = "list"),
            toolVariant(delegate, "schedule_update", "Update only supplied reminder fields using an id from schedule_list. The conversation cannot change.", editable + "id", setOf("id"), "update"),
            toolVariant(delegate, "schedule_delete", "Delete a reminder by its id from schedule_list.", setOf("id"), setOf("id"), "delete"),
        )
    }

private fun scheduleTool(service: ScheduleService, binding: SessionBinding): CodingTool = CodingTool(
    name = "schedule",
    description = "Create, list, update or delete reminders bound to this conversation. A reminder posts a message here when it fires. frequency=once needs date/time; frequency=cron needs a five-field expression.",
    parameters = scheduleParameters,
    label = "schedule",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        scheduleToolResult {
            when (val action = args.requiredString("action").lowercase()) {
                "create" -> createSchedule(service, binding, args)
                "list" -> listSchedules(service, binding)
                "update" -> updateSchedule(service, binding, args)
                "delete" -> deleteSchedule(service, binding, args)
                else -> textResult("action must be create, list, update or delete.", isError = true)
            }
        }
    },
)

private suspend fun createSchedule(
    service: ScheduleService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
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
            enabled = args.boolOrNull("enabled") ?: true,
            sessionId = session.sessionId,
            sessionFile = sessionFile,
            cwd = session.cwd,
        ),
    )
    return textResult(scheduleJson(created).toString())
}

private suspend fun listSchedules(service: ScheduleService, binding: SessionBinding): AgentToolResult {
    val session = requireSession(binding)
    val schedules = service.list()
        .filter { it.sessionId == session.sessionId }
        .sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.createdAt }))
    val result = buildJsonObject {
        putJsonArray("schedules") {
            schedules.forEach { add(scheduleJson(it)) }
        }
    }
    return textResult(result.toString())
}

private suspend fun updateSchedule(
    service: ScheduleService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
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
    require(args.keys.any { it !in setOf("id", "action") }) { "schedule_update requires at least one changed field" }
    val patch = SchedulePatch(
        name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() },
        prompt = args.stringOrNull("prompt")?.takeIf { it.isNotBlank() },
        trigger = trigger,
        enabled = args.boolOrNull("enabled"),
    )
    val updated = service.update(id, patch)
        ?: throw IllegalArgumentException("未找到定时任务：$id")
    return textResult(scheduleJson(updated).toString())
}

private suspend fun deleteSchedule(
    service: ScheduleService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
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
    return textResult(result.toString())
}

private fun requireSession(binding: SessionBinding): BoundSession =
    binding.current() ?: throw IllegalArgumentException("当前会话未绑定")

private suspend fun scheduleToolResult(block: suspend () -> AgentToolResult): AgentToolResult =
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
    if (frequency == "once" && args.containsKey("cron")) throw IllegalArgumentException("once does not accept cron")
    if (frequency == "cron" && (args.containsKey("date") || args.containsKey("time"))) throw IllegalArgumentException("cron does not accept date/time")
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
