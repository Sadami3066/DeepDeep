package io.github.whywood.deepdeep.plugin.arrange

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val MAX_LABEL_LENGTH = 200
internal const val MAX_TEXT_LENGTH = 1_024

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

internal fun boundedOptionalText(raw: String?, maxLength: Int, field: String): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.length > maxLength) throw IllegalArgumentException("$field must be at most $maxLength characters")
    return text
}

internal fun parseCalendarMillis(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long {
    val text = raw.trim()
    if (text.isEmpty()) throw IllegalArgumentException("Time must not be empty")
    text.toLongOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(text) }.getOrNull()?.let { return it.toInstant().toEpochMilli() }
    val normalized = text.replace(' ', 'T')
    runCatching { LocalDateTime.parse(normalized) }.getOrNull()?.let {
        return it.atZone(zone).toInstant().toEpochMilli()
    }
    runCatching { LocalDate.parse(normalized) }.getOrNull()?.let {
        return it.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    throw IllegalArgumentException("Cannot parse time \"$raw\"; expected yyyy-MM-dd HH:mm, yyyy-MM-dd, ISO 8601 or epoch milliseconds")
}

internal fun formatCalendarTime(millis: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    if (allDay) return DATE_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC))
    return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(zone))
}
