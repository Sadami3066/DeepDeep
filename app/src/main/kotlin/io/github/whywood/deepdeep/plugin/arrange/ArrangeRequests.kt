package io.github.whywood.deepdeep.plugin.arrange

import android.content.Intent
import android.provider.AlarmClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val CALENDAR_TIME_URI = "content://com.android.calendar/time"
internal const val DEFAULT_CALENDAR_DURATION_MILLIS = 60L * 60L * 1_000L
internal const val MAX_LABEL_LENGTH = 200
internal const val MAX_TEXT_LENGTH = 1_024
internal const val MIN_TIMER_SECONDS = 1
internal const val MAX_TIMER_SECONDS = 24 * 60 * 60

private val CLOCK_PATTERN = Regex("([0-9]{1,2}):([0-9]{2})")
private val ISO_DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

internal data class ClockTime(
    val hour: Int,
    val minute: Int,
) {
    val label: String get() = "%02d:%02d".format(hour, minute)
}

internal fun clockTime(hour: Int, minute: Int): ClockTime {
    if (hour !in 0..23) throw IllegalArgumentException("hour 需要在 0..23 之间")
    if (minute !in 0..59) throw IllegalArgumentException("minute 需要在 0..59 之间")
    return ClockTime(hour, minute)
}

internal fun parseClockTime(raw: String): ClockTime {
    val match = CLOCK_PATTERN.matchEntire(raw.trim())
        ?: throw IllegalArgumentException("时间格式应为 HH:mm，例如 07:30")
    return clockTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

internal fun normalizeIsoDays(days: List<Int>): List<Int> {
    days.forEach { day ->
        if (day !in 1..7) throw IllegalArgumentException("days 使用 ISO 星期：1=周一，7=周日")
    }
    return days.distinct().sorted()
}

internal fun formatIsoDays(days: List<Int>): String {
    val normalized = normalizeIsoDays(days)
    return when {
        normalized.isEmpty() -> "仅一次"
        normalized.size == 7 -> "每天"
        else -> normalized.joinToString("、") { ISO_DAY_LABELS[it - 1] }
    }
}

internal fun boundedOptionalText(raw: String?, maxLength: Int, field: String): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.length > maxLength) throw IllegalArgumentException("$field 不能超过 $maxLength 个字符")
    return text
}

internal fun alarmShowRequest(): ArrangeIntentSpec = ArrangeIntentSpec(action = AlarmClock.ACTION_SHOW_ALARMS)

internal fun parseCalendarMillis(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long {
    val text = raw.trim()
    if (text.isEmpty()) throw IllegalArgumentException("时间不能为空")
    text.toLongOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(text) }.getOrNull()?.let { return it.toInstant().toEpochMilli() }
    val normalized = text.replace(' ', 'T')
    runCatching { LocalDateTime.parse(normalized) }.getOrNull()?.let {
        return it.atZone(zone).toInstant().toEpochMilli()
    }
    runCatching { LocalDate.parse(normalized) }.getOrNull()?.let {
        return it.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    throw IllegalArgumentException("无法解析时间「$raw」：支持 yyyy-MM-dd HH:mm、yyyy-MM-dd、ISO 8601 或毫秒时间戳")
}

internal fun calendarOpenRequest(startMillis: Long?): ArrangeIntentSpec = if (startMillis == null) {
    ArrangeIntentSpec(action = Intent.ACTION_MAIN, categories = listOf(Intent.CATEGORY_APP_CALENDAR))
} else {
    ArrangeIntentSpec(action = Intent.ACTION_VIEW, data = "$CALENDAR_TIME_URI/$startMillis")
}

internal fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    val parts = buildList {
        if (hours > 0) add("$hours 小时")
        if (minutes > 0) add("$minutes 分")
        if (seconds > 0 || isEmpty()) add("$seconds 秒")
    }
    return parts.joinToString(" ")
}

internal fun formatCalendarTime(millis: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    return if (allDay) DATE_FORMATTER.format(dateTime) else DATE_TIME_FORMATTER.format(dateTime)
}

internal fun formatCalendarRange(
    startMillis: Long,
    endMillis: Long?,
    allDay: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val start = formatCalendarTime(startMillis, allDay, zone)
    if (allDay) return start
    val end = endMillis?.let { formatCalendarTime(it, allDay = false, zone = zone) }
    return if (end == null) start else "$start–$end"
}

internal fun calendarRangeForDay(startMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val date = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    return start to start + 24L * 60L * 60L * 1_000L
}
