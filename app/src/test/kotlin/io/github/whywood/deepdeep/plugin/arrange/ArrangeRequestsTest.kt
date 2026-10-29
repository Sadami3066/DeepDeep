package io.github.whywood.deepdeep.plugin.arrange

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArrangeRequestsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun boundsOptionalText() {
        assertEquals("abc", boundedOptionalText(" abc ", 3, "title"))
        assertEquals(null, boundedOptionalText("   ", 3, "title"))
        assertThrows(IllegalArgumentException::class.java) {
            boundedOptionalText("abcd", 3, "title")
        }
    }

    @Test
    fun parsesCalendarTimes() {
        val dateTime = LocalDateTime.of(2026, 10, 26, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val date = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(dateTime, parseCalendarMillis("2026-10-26 10:00", zone))
        assertEquals(dateTime, parseCalendarMillis("2026-10-26T10:00", zone))
        assertEquals(date, parseCalendarMillis("2026-10-26", zone))
        assertEquals(1_793_000_000_000L, parseCalendarMillis("1793000000000", zone))
        assertEquals(dateTime, parseCalendarMillis("2026-10-26T10:00:00+08:00", zone))
    }

    @Test
    fun rejectsUnparsableCalendarTime() {
        assertThrows(IllegalArgumentException::class.java) { parseCalendarMillis("明天", zone) }
        assertThrows(IllegalArgumentException::class.java) { parseCalendarMillis("  ", zone) }
    }

    @Test
    fun formatsCalendarTime() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)
        assertEquals("2026-10-26 10:00", formatCalendarTime(start, allDay = false, zone = zone))
    }

    @Test
    fun formatsAllDayDatesInUtcAcrossZones() {
        listOf("Asia/Shanghai", "America/New_York", "UTC", "America/Santiago").forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val utcMidnight = LocalDate.of(2026, 10, 30)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            assertEquals(zoneId, "2026-10-30", formatCalendarTime(utcMidnight, allDay = true, zone = zone))
            val parsed = parseCalendarMillis("2026-10-30", zone)
            val start = resolveEventRange(parsed, null, allDay = true, zone = zone).first
            assertEquals(zoneId, "2026-10-30", formatCalendarTime(start, allDay = true, zone = zone))
        }
    }
}
