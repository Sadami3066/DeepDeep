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

private val TIMER_ACTION_VALUES = buildJsonArray {
    add(JsonPrimitive("create"))
    add(JsonPrimitive("list"))
    add(JsonPrimitive("update"))
    add(JsonPrimitive("delete"))
}

private val timerParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("enum", TIMER_ACTION_VALUES)
            put("description", "create, list, update or delete")
        }
        putJsonObject("id") {
            put("type", "string")
            put("description", "Timer id, required for update and delete")
        }
        putJsonObject("prompt") {
            put("type", "string")
            put("description", "Message sent to the current conversation when the timer fires")
        }
        putJsonObject("frequency") {
            put("type", "string")
            put("enum", FREQUENCY_VALUES)
            put("description", "Trigger kind: once for a single date and time, cron for a five-field cron expression")
        }
        putJsonObject("name") {
            put("type", "string")
            put("description", "Display name for the timer (defaults to the prompt preview)")
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
            put("description", "Whether the timer is enabled")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

fun timerTools(service: TimerService, binding: SessionBinding): List<CodingTool> =
    timerTool(service, binding).let { delegate ->
        val editable = setOf("prompt", "name", "frequency", "date", "time", "cron", "enabled")
        listOf(
            toolVariant(delegate, "timer_create", "Create a timer in this conversation. once needs date/time, cron needs a five-field expression. enabled defaults to true; false creates a disabled timer.", editable, setOf("prompt", "frequency"), "create"),
            toolVariant(delegate, "timer_list", "List timers in this conversation with their ids, trigger details, enabled state and delivery results.", emptySet(), fixedAction = "list"),
            toolVariant(delegate, "timer_update", "Update only supplied timer fields using an id from timer_list. The conversation cannot change.", editable + "id", setOf("id"), "update"),
            toolVariant(delegate, "timer_delete", "Delete a timer by its id from timer_list.", setOf("id"), setOf("id"), "delete"),
        )
    }

private fun timerTool(service: TimerService, binding: SessionBinding): CodingTool = CodingTool(
    name = "timer",
    description = "Create, list, update or delete timers bound to this conversation. A timer posts a message here when it fires. frequency=once needs date/time; frequency=cron needs a five-field expression.",
    parameters = timerParameters,
    label = "timer",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        timerToolResult {
            when (val action = args.requiredString("action").lowercase()) {
                "create" -> createTimer(service, binding, args)
                "list" -> listTimers(service, binding)
                "update" -> updateTimer(service, binding, args)
                "delete" -> deleteTimer(service, binding, args)
                else -> textResult("action must be create, list, update or delete.", isError = true)
            }
        }
    },
)

private suspend fun createTimer(
    service: TimerService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
    val session = requireSession(binding)
    val sessionFile = session.sessionFile
        ?: throw IllegalArgumentException("Current conversation is not saved yet; cannot create a timer")
    val prompt = args.requiredString("prompt")
    val frequency = args.requiredString("frequency")
    val name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: promptPreview(prompt)
    val trigger = parseTrigger(args, base = null, requestedFrequency = frequency)
    val created = service.create(
        NewTimer(
            name = name,
            prompt = prompt,
            trigger = trigger,
            enabled = args.boolOrNull("enabled") ?: true,
            sessionId = session.sessionId,
            sessionFile = sessionFile,
            cwd = session.cwd,
        ),
    )
    return textResult(timerJson(created).toString())
}

private suspend fun listTimers(service: TimerService, binding: SessionBinding): AgentToolResult {
    val session = requireSession(binding)
    val timers = service.list()
        .filter { it.sessionId == session.sessionId }
        .sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.createdAt }))
    val result = buildJsonObject {
        putJsonArray("timers") {
            timers.forEach { add(timerJson(it)) }
        }
    }
    return textResult(result.toString())
}

private suspend fun updateTimer(
    service: TimerService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
    val session = requireSession(binding)
    val id = args.requiredString("id")
    val existing = service.list()
        .firstOrNull { it.id == id && it.sessionId == session.sessionId }
        ?: throw IllegalArgumentException("Timer not found: $id")
    val hasTriggerArgs = listOf("frequency", "date", "time", "cron").any { args.containsKey(it) }
    val trigger = if (hasTriggerArgs) {
        parseTrigger(args, base = existing.trigger, requestedFrequency = args.stringOrNull("frequency"))
    } else {
        null
    }
    require(args.keys.any { it !in setOf("id", "action") }) { "timer_update requires at least one changed field" }
    val patch = TimerPatch(
        name = args.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() },
        prompt = args.stringOrNull("prompt")?.takeIf { it.isNotBlank() },
        trigger = trigger,
        enabled = args.boolOrNull("enabled"),
    )
    val updated = service.update(id, patch)
        ?: throw IllegalArgumentException("Timer not found: $id")
    return textResult(timerJson(updated).toString())
}

private suspend fun deleteTimer(
    service: TimerService,
    binding: SessionBinding,
    args: JsonObject,
): AgentToolResult {
    val session = requireSession(binding)
    val id = args.requiredString("id")
    val existing = service.list()
        .firstOrNull { it.id == id && it.sessionId == session.sessionId }
        ?: throw IllegalArgumentException("Timer not found: $id")
    if (!service.delete(existing.id)) {
        throw IllegalArgumentException("Timer not found: $id")
    }
    val result = buildJsonObject {
        put("deleted", true)
        put("id", id)
    }
    return textResult(result.toString())
}

private fun requireSession(binding: SessionBinding): BoundSession =
    binding.current() ?: throw IllegalArgumentException("Current conversation is not bound")

private suspend fun timerToolResult(block: suspend () -> AgentToolResult): AgentToolResult =
    try {
        block()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        textResult(error.message ?: "Timer operation failed", isError = true)
    }

private fun timerJson(timer: Timer): JsonObject = buildJsonObject {
    put("id", timer.id)
    put("name", timer.name)
    put("trigger", timer.trigger.toJsonObject())
    if (timer.nextRunAt == null) {
        put("nextRunAt", JsonNull)
    } else {
        put("nextRunAt", timer.nextRunAt)
    }
    put("enabled", timer.enabled)
    if (timer.lastDeliveredAt == null) {
        put("lastDeliveredAt", JsonNull)
    } else {
        put("lastDeliveredAt", timer.lastDeliveredAt)
    }
    if (timer.lastError == null) {
        put("lastError", JsonNull)
    } else {
        put("lastError", timer.lastError.name)
    }
}

private fun parseTrigger(args: JsonObject, base: TimerTrigger?, requestedFrequency: String?): TimerTrigger {
    val frequency = requestedFrequency?.trim()?.lowercase()
        ?: args.stringOrNull("frequency")?.trim()?.lowercase()
        ?: when (base) {
            is TimerTrigger.Cron -> "cron"
            is TimerTrigger.Once -> "once"
            else -> null
        }
        ?: throw IllegalArgumentException("Missing frequency (once or cron)")
    if (frequency == "once" && args.containsKey("cron")) throw IllegalArgumentException("once does not accept cron")
    if (frequency == "cron" && (args.containsKey("date") || args.containsKey("time"))) throw IllegalArgumentException("cron does not accept date/time")
    return when (frequency) {
        "once" -> {
            val baseOnce = base as? TimerTrigger.Once
            val date = args.stringOrNull("date")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::parseDate)
                ?: baseOnce?.date
                ?: throw IllegalArgumentException("once requires date (YYYY-MM-DD)")
            val time = parseTimeArg(args, baseOnce?.time)
                ?: throw IllegalArgumentException("once requires time (HH:mm)")
            TimerTrigger.Once(date, time)
        }
        "cron" -> {
            val baseCron = base as? TimerTrigger.Cron
            val expression = args.stringOrNull("cron")?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseCron?.expression
                ?: throw IllegalArgumentException("cron requires a five-field cron expression")
            TimerTrigger.Cron(expression)
        }
        else -> throw IllegalArgumentException("frequency must be once or cron")
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
        throw IllegalArgumentException("Invalid time: $raw (use HH:mm)")
    }
}

private fun parseDate(raw: String): LocalDate = try {
    LocalDate.parse(raw)
} catch (error: DateTimeParseException) {
    throw IllegalArgumentException("Invalid date: $raw (use YYYY-MM-DD)")
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
        ?: throw IllegalArgumentException("Missing required parameter: $name")
