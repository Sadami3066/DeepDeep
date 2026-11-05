package io.github.whywood.deepdeep.runtime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class CorruptTimerDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class TimerError {
    SESSION_MISSING,
    OPEN_FAILED,
    DELIVERY_FAILED,
}

sealed interface TimerTrigger {
    data class Once(val date: LocalDate, val time: LocalTime) : TimerTrigger

    data class Daily(val time: LocalTime) : TimerTrigger

    data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : TimerTrigger

    data class Cron(val expression: String) : TimerTrigger
}

data class Timer(
    val id: String,
    val name: String,
    val prompt: String,
    val trigger: TimerTrigger,
    val enabled: Boolean = true,
    val sessionId: String,
    val sessionFile: String,
    val cwd: String,
    val nextRunAt: Long? = null,
    val lastDeliveredAt: Long? = null,
    val lastError: TimerError? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun nextRun(now: ZonedDateTime): ZonedDateTime? = trigger.nextRun(now)
}

fun TimerTrigger.nextRun(now: ZonedDateTime): ZonedDateTime? = when (this) {
    is TimerTrigger.Once -> ZonedDateTime.of(date, time, now.zone).takeIf { it.isAfter(now) }
    is TimerTrigger.Daily -> {
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        if (today.isAfter(now)) {
            today
        } else {
            ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
        }
    }
    is TimerTrigger.Weekly -> {
        if (days.isEmpty()) {
            null
        } else {
            var result: ZonedDateTime? = null
            for (offset in 0..7) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (date.dayOfWeek !in days) continue
                val candidate = ZonedDateTime.of(date, time, now.zone)
                if (candidate.isAfter(now)) {
                    result = candidate
                    break
                }
            }
            result
        }
    }
    is TimerTrigger.Cron -> nextRunForCron(now)
}

fun TimerTrigger.validationError(now: ZonedDateTime): String? = when (this) {
    is TimerTrigger.Once -> if (nextRun(now) == null) "once time must be in the future" else null
    is TimerTrigger.Daily -> null
    is TimerTrigger.Weekly -> if (days.isEmpty()) "weekly needs at least one weekday" else null
    is TimerTrigger.Cron -> {
        val parsed = try {
            CronExpression.parse(expression)
        } catch (error: InvalidCronExpressionException) {
            return "Invalid cron expression: ${error.message}"
        }
        if (parsed.nextRun(now) == null) "cron has no matching time within the next 5 years" else null
    }
}

private fun TimerTrigger.Cron.nextRunForCron(now: ZonedDateTime): ZonedDateTime? =
    try {
        CronExpression.parse(expression).nextRun(now)
    } catch (_: InvalidCronExpressionException) {
        null
    }

fun TimerTrigger.toJsonObject(): JsonObject = buildJsonObject {
    when (val trigger = this@toJsonObject) {
        is TimerTrigger.Once -> {
            put("type", "once")
            put("date", trigger.date.toString())
            put("time", trigger.time.toString())
        }
        is TimerTrigger.Daily -> {
            put("type", "daily")
            put("time", trigger.time.toString())
        }
        is TimerTrigger.Weekly -> {
            put("type", "weekly")
            putJsonArray("days") {
                trigger.days.sortedBy { it.value }.forEach { add(JsonPrimitive(it.value)) }
            }
            put("time", trigger.time.toString())
        }
        is TimerTrigger.Cron -> {
            put("type", "cron")
            put("expression", trigger.expression)
        }
    }
}

fun Timer.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("prompt", prompt)
    put("trigger", trigger.toJsonObject())
    put("enabled", enabled)
    put("sessionId", sessionId)
    put("sessionFile", sessionFile)
    put("cwd", cwd)
    if (nextRunAt == null) {
        put("nextRunAt", JsonNull)
    } else {
        put("nextRunAt", nextRunAt)
    }
    if (lastDeliveredAt == null) {
        put("lastDeliveredAt", JsonNull)
    } else {
        put("lastDeliveredAt", lastDeliveredAt)
    }
    if (lastError == null) {
        put("lastError", JsonNull)
    } else {
        put("lastError", lastError.name)
    }
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun timerTriggerFromJsonObject(json: JsonObject): TimerTrigger {
    val type = json.stringOrCorrupt("type")
    return when (type) {
        "once" -> TimerTrigger.Once(
            date = json.dateOrCorrupt("date"),
            time = json.timeOrCorrupt("time"),
        )
        "daily" -> TimerTrigger.Daily(json.timeOrCorrupt("time"))
        "weekly" -> {
            val days = json["days"] as? JsonArray
                ?: throw CorruptTimerDataException("weekly trigger 缺少 days")
            val parsed = days.map { element -> weekdayFromJsonElement(element) }.toSet()
            TimerTrigger.Weekly(parsed, json.timeOrCorrupt("time"))
        }
        "cron" -> {
            val expression = json.stringOrCorrupt("expression").trim()
            try {
                CronExpression.parse(expression)
            } catch (error: InvalidCronExpressionException) {
                throw CorruptTimerDataException("Invalid cron expression: ${error.message}", error)
            }
            TimerTrigger.Cron(expression)
        }
        else -> throw CorruptTimerDataException("未知 trigger 类型：$type")
    }
}

fun timerFromJsonObject(json: JsonObject): Timer = Timer(
    id = json.stringOrCorrupt("id"),
    name = json.stringOrCorrupt("name"),
    prompt = json.stringOrCorrupt("prompt"),
    trigger = timerTriggerFromJsonObject(json.objectOrCorrupt("trigger")),
    enabled = json.booleanOrCorrupt("enabled"),
    sessionId = json.stringOrCorrupt("sessionId"),
    sessionFile = json.stringOrCorrupt("sessionFile"),
    cwd = json.stringOrCorrupt("cwd"),
    nextRunAt = json.optionalLong("nextRunAt"),
    lastDeliveredAt = json.optionalLong("lastDeliveredAt"),
    lastError = json.optionalString("lastError")?.let { value ->
        runCatching { TimerError.valueOf(value) }
            .getOrElse { throw CorruptTimerDataException("lastError 无效：$value") }
    },
    createdAt = json.longOrCorrupt("createdAt"),
    updatedAt = json.longOrCorrupt("updatedAt"),
)

private val LEGACY_WEEKDAYS: Map<String, DayOfWeek> = buildMap {
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

private fun weekdayFromJsonElement(element: kotlinx.serialization.json.JsonElement): DayOfWeek {
    val raw = (element as? JsonPrimitive)?.contentOrNull
        ?: throw CorruptTimerDataException("weekday 无效")
    val iso = raw.trim().toIntOrNull()
    if (iso != null) {
        return runCatching { DayOfWeek.of(iso) }
            .getOrElse { throw CorruptTimerDataException("weekday 无效：$raw") }
    }
    return LEGACY_WEEKDAYS[raw.trim().lowercase()]
        ?: throw CorruptTimerDataException("weekday 无效：$raw")
}

private fun JsonObject.stringOrCorrupt(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull
        ?: throw CorruptTimerDataException("缺少字段：$name")

private fun JsonObject.booleanOrCorrupt(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        ?: throw CorruptTimerDataException("字段无效：$name")

private fun JsonObject.longOrCorrupt(name: String): Long =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw CorruptTimerDataException("字段无效：$name")

private fun JsonObject.optionalLong(name: String): Long? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw CorruptTimerDataException("字段无效：$name")
}

private fun JsonObject.optionalString(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.objectOrCorrupt(name: String): JsonObject =
    this[name] as? JsonObject ?: throw CorruptTimerDataException("缺少字段：$name")

private fun JsonObject.timeOrCorrupt(name: String): LocalTime {
    val raw = stringOrCorrupt(name)
    return try {
        LocalTime.parse(raw)
    } catch (error: DateTimeParseException) {
        throw CorruptTimerDataException("时间格式无效：$raw", error)
    }
}

private fun JsonObject.dateOrCorrupt(name: String): LocalDate {
    val raw = stringOrCorrupt(name)
    return try {
        LocalDate.parse(raw)
    } catch (error: DateTimeParseException) {
        throw CorruptTimerDataException("日期格式无效：$raw", error)
    }
}
