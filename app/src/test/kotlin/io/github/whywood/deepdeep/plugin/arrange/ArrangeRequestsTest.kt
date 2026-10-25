package io.github.whywood.deepdeep.plugin.arrange

import android.provider.AlarmClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeRequestsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesClockTimeFormats() {
        assertEquals(ClockTime(7, 30), parseClockTime("07:30"))
        assertEquals(ClockTime(7, 5), parseClockTime("7:05"))
        assertEquals(ClockTime(0, 0), parseClockTime("00:00"))
        assertEquals(ClockTime(23, 59), parseClockTime(" 23:59 "))
    }

    @Test
    fun rejectsInvalidClockTimes() {
        listOf("24:00", "7:5", "07:60", "0730", "", "ab:cd").forEach { raw ->
            assertThrows(raw, IllegalArgumentException::class.java) { parseClockTime(raw) }
        }
        assertThrows(IllegalArgumentException::class.java) { clockTime(24, 0) }
        assertThrows(IllegalArgumentException::class.java) { clockTime(0, 60) }
    }

    @Test
    fun normalizesAndFormatsIsoDays() {
        assertEquals(listOf(1, 3), normalizeIsoDays(listOf(3, 1, 3)))
        assertEquals("仅一次", formatIsoDays(emptyList()))
        assertEquals("周一、周三", formatIsoDays(listOf(3, 1, 3)))
        assertEquals("每天", formatIsoDays((1..7).toList()))
        assertThrows(IllegalArgumentException::class.java) { normalizeIsoDays(listOf(1, 9)) }
    }

    @Test
    fun boundsOptionalText() {
        assertEquals("abc", boundedOptionalText(" abc ", 3, "label"))
        assertEquals(null, boundedOptionalText("   ", 3, "label"))
        assertThrows(IllegalArgumentException::class.java) {
            boundedOptionalText("abcd", 3, "label")
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
    fun buildsShowAndOpenSpecs() {
        val show = alarmShowRequest()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, show.action)

        val open = calendarOpenRequest(null)
        assertEquals(android.content.Intent.ACTION_MAIN, open.action)
        assertTrue(open.categories.contains(android.content.Intent.CATEGORY_APP_CALENDAR))

        val at = parseCalendarMillis("2026-10-26 10:00", zone)
        val timed = calendarOpenRequest(at)
        assertEquals(android.content.Intent.ACTION_VIEW, timed.action)
        assertEquals("$CALENDAR_TIME_URI/$at", timed.data)
    }

    @Test
    fun formatsDurationsAndCalendarRanges() {
        assertEquals("10 分", formatDuration(600))
        assertEquals("1 小时", formatDuration(3_600))
        assertEquals("1 小时 1 分 1 秒", formatDuration(3_661))
        assertEquals("0 秒", formatDuration(0))

        val start = parseCalendarMillis("2026-10-26 10:00", zone)
        assertEquals("2026-10-26 10:00–2026-10-26 11:00", formatCalendarRange(start, start + 3_600_000, false, zone))
        assertEquals("2026-10-26", formatCalendarRange(start, null, true, zone))
    }

    @Test
    fun computesCalendarDayRange() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)
        val (begin, end) = calendarRangeForDay(start, zone)
        assertEquals(parseCalendarMillis("2026-10-26", zone), begin)
        assertEquals(parseCalendarMillis("2026-10-27", zone), end)
    }
}
