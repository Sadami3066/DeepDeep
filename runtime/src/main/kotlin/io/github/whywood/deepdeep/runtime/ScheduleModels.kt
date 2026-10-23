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

class CorruptScheduleDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface ScheduleTrigger {
    data class Once(val date: LocalDate, val time: LocalTime) : ScheduleTrigger

    data class Daily(val time: LocalTime) : ScheduleTrigger

    data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : ScheduleTrigger
}

enum class ScheduleRunStatus {
    RUNNING,
    DONE,
    STOPPED,
    FAILED,
}

data class Schedule(
    val id: String,
    val name: String,
    val workspaceId: String,
    val prompt: String,
    val trigger: ScheduleTrigger,
    val enabled: Boolean = true,
    val notify: Boolean = true,
    val nextRunAt: Long? = null,
    val lastRunAt: Long? = null,
    val lastStatus: ScheduleRunStatus? = null,
    val lastSessionId: String? = null,
    val lastError: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun nextRun(now: ZonedDateTime): ZonedDateTime? = trigger.nextRun(now)
}

fun ScheduleTrigger.nextRun(now: ZonedDateTime): ZonedDateTime? = when (this) {
    is ScheduleTrigger.Once -> ZonedDateTime.of(date, time, now.zone).takeIf { it.isAfter(now) }
    is ScheduleTrigger.Daily -> {
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        if (today.isAfter(now)) {
            today
        } else {
            ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
        }
    }
    is ScheduleTrigger.Weekly -> {
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
}

fun ScheduleTrigger.toJsonObject(): JsonObject = buildJsonObject {
    when (val trigger = this@toJsonObject) {
        is ScheduleTrigger.Once -> {
            put("type", "once")
            put("date", trigger.date.toString())
            put("time", trigger.time.toString())
        }
        is ScheduleTrigger.Daily -> {
            put("type", "daily")
            put("time", trigger.time.toString())
        }
        is ScheduleTrigger.Weekly -> {
            put("type", "weekly")
            putJsonArray("days") {
                trigger.days.sortedBy { it.value }.forEach { add(JsonPrimitive(it.value)) }
            }
            put("time", trigger.time.toString())
        }
    }
}

fun Schedule.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("workspaceId", workspaceId)
    put("prompt", prompt)
    put("trigger", trigger.toJsonObject())
    put("enabled", enabled)
    put("notify", notify)
    if (nextRunAt == null) {
        put("nextRunAt", JsonNull)
    } else {
        put("nextRunAt", nextRunAt)
    }
    put("lastRunAt", lastRunAt)
    put("lastStatus", lastStatus?.name)
    put("lastSessionId", lastSessionId)
    put("lastError", lastError)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun scheduleTriggerFromJsonObject(json: JsonObject): ScheduleTrigger {
    val type = json.stringOrCorrupt("type")
    val time = json.timeOrCorrupt("time")
    return when (type) {
        "once" -> ScheduleTrigger.Once(
            date = json.dateOrCorrupt("date"),
            time = time,
        )
        "daily" -> ScheduleTrigger.Daily(time)
        "weekly" -> {
            val days = json["days"] as? JsonArray
                ?: throw CorruptScheduleDataException("weekly trigger 缺少 days")
            val parsed = days.map { element -> weekdayFromJsonElement(element) }.toSet()
            ScheduleTrigger.Weekly(parsed, time)
        }
        else -> throw CorruptScheduleDataException("未知 trigger 类型：$type")
    }
}

fun scheduleFromJsonObject(json: JsonObject): Schedule = Schedule(
    id = json.stringOrCorrupt("id"),
    name = json.stringOrCorrupt("name"),
    workspaceId = json.stringOrCorrupt("workspaceId"),
    prompt = json.stringOrCorrupt("prompt"),
    trigger = scheduleTriggerFromJsonObject(json.objectOrCorrupt("trigger")),
    enabled = json.booleanOrCorrupt("enabled"),
    notify = json.booleanOrCorrupt("notify"),
    nextRunAt = json.optionalLong("nextRunAt"),
    lastRunAt = json.optionalLong("lastRunAt"),
    lastStatus = json.optionalString("lastStatus")?.let { value ->
        runCatching { ScheduleRunStatus.valueOf(value) }
            .getOrElse { throw CorruptScheduleDataException("lastStatus 无效：$value") }
    },
    lastSessionId = json.optionalString("lastSessionId"),
    lastError = json.optionalString("lastError"),
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
        ?: throw CorruptScheduleDataException("weekday 无效")
    val iso = raw.trim().toIntOrNull()
    if (iso != null) {
        return runCatching { DayOfWeek.of(iso) }
            .getOrElse { throw CorruptScheduleDataException("weekday 无效：$raw") }
    }
    return LEGACY_WEEKDAYS[raw.trim().lowercase()]
        ?: throw CorruptScheduleDataException("weekday 无效：$raw")
}

private fun JsonObject.stringOrCorrupt(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull
        ?: throw CorruptScheduleDataException("缺少字段：$name")

private fun JsonObject.booleanOrCorrupt(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        ?: throw CorruptScheduleDataException("字段无效：$name")

private fun JsonObject.longOrCorrupt(name: String): Long =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw CorruptScheduleDataException("字段无效：$name")

private fun JsonObject.optionalLong(name: String): Long? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw CorruptScheduleDataException("字段无效：$name")
}

private fun JsonObject.optionalString(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.objectOrCorrupt(name: String): JsonObject =
    this[name] as? JsonObject ?: throw CorruptScheduleDataException("缺少字段：$name")

private fun JsonObject.timeOrCorrupt(name: String): LocalTime {
    val raw = stringOrCorrupt(name)
    return try {
        LocalTime.parse(raw)
    } catch (error: DateTimeParseException) {
        throw CorruptScheduleDataException("时间格式无效：$raw", error)
    }
}

private fun JsonObject.dateOrCorrupt(name: String): LocalDate {
    val raw = stringOrCorrupt(name)
    return try {
        LocalDate.parse(raw)
    } catch (error: DateTimeParseException) {
        throw CorruptScheduleDataException("日期格式无效：$raw", error)
    }
}
