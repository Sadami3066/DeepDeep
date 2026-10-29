package io.github.whywood.deepdeep.shell

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private const val FALLBACK_ABBREVIATION = "LOC"
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

fun posixTimeZone(zone: TimeZone, nowMillis: Long): String {
    val abbreviation = zone
        .getDisplayName(zone.inDaylightTime(Date(nowMillis)), TimeZone.SHORT, Locale.ROOT)
        .filter { it in 'A'..'Z' || it in 'a'..'z' }
        .uppercase(Locale.ROOT)
        .takeIf { it.length >= 3 }
        ?: FALLBACK_ABBREVIATION
    return abbreviation + formatOffset(-zone.getOffset(nowMillis) / 1000)
}

private fun formatOffset(offsetSeconds: Int): String {
    if (offsetSeconds == 0) return "0"
    val sign = if (offsetSeconds > 0) "+" else "-"
    val absolute = abs(offsetSeconds)
    val hours = absolute / SECONDS_PER_HOUR
    val minutes = absolute % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return if (minutes == 0) {
        "$sign$hours"
    } else {
        "$sign$hours:${minutes.toString().padStart(2, '0')}"
    }
}
