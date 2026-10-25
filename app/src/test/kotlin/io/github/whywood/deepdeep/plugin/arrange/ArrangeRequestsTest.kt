package io.github.whywood.deepdeep.plugin.arrange

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun mapsIsoDaysToAlarmClockDays() {
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 1), (1..7).map(::isoToAlarmClockDay))
        assertEquals(listOf(2, 4), alarmClockDays(listOf(3, 1, 3)))
        assertThrows(IllegalArgumentException::class.java) { isoToAlarmClockDay(0) }
        assertThrows(IllegalArgumentException::class.java) { isoToAlarmClockDay(8) }
        assertThrows(IllegalArgumentException::class.java) { normalizeIsoDays(listOf(1, 9)) }
    }

    @Test
    fun formatsIsoDays() {
        assertEquals("仅一次", formatIsoDays(emptyList()))
        assertEquals("周一、周三", formatIsoDays(listOf(3, 1, 3)))
        assertEquals("每天", formatIsoDays((1..7).toList()))
    }

    @Test
    fun buildsAlarmSetSpecWithExtras() {
        val spec = alarmSetRequest(ClockTime(7, 30), " 起床 ", listOf(3, 1), skipUi = true)

        assertEquals(AlarmClock.ACTION_SET_ALARM, spec.action)
        assertEquals(7, spec.extras[AlarmClock.EXTRA_HOUR] as Int)
        assertEquals(30, spec.extras[AlarmClock.EXTRA_MINUTES] as Int)
        assertEquals("起床", spec.extras[AlarmClock.EXTRA_MESSAGE])
        assertEquals(listOf(2, 4), spec.extras[AlarmClock.EXTRA_DAYS])
        assertEquals(true, spec.extras[AlarmClock.EXTRA_SKIP_UI])
    }

    @Test
    fun alarmSetOmitsOptionalExtras() {
        val spec = alarmSetRequest(ClockTime(6, 5), "  ", emptyList(), skipUi = false)

        assertFalse(spec.extras.containsKey(AlarmClock.EXTRA_MESSAGE))
        assertFalse(spec.extras.containsKey(AlarmClock.EXTRA_DAYS))
        assertFalse(spec.extras.containsKey(AlarmClock.EXTRA_SKIP_UI))
    }

    @Test
    fun alarmSetRejectsOverlongLabel() {
        assertThrows(IllegalArgumentException::class.java) {
            alarmSetRequest(ClockTime(6, 5), "a".repeat(MAX_LABEL_LENGTH + 1), emptyList(), skipUi = false)
        }
    }

    @Test
    fun buildsTimerSpecAndValidatesDuration() {
        val spec = alarmTimerRequest(600, "泡面")

        assertEquals(AlarmClock.ACTION_SET_TIMER, spec.action)
        assertEquals(600, spec.extras[AlarmClock.EXTRA_LENGTH] as Int)
        assertEquals("泡面", spec.extras[AlarmClock.EXTRA_MESSAGE])
        assertThrows(IllegalArgumentException::class.java) { alarmTimerRequest(0, null) }
        assertThrows(IllegalArgumentException::class.java) { alarmTimerRequest(MAX_TIMER_SECONDS + 1, null) }
    }

    @Test
    fun buildsShowAlarmSpec() {
        val spec = alarmShowRequest()

        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, spec.action)
        assertTrue(spec.extras.isEmpty())
    }

    @Test
    fun parsesCalendarTimes() {
        val dateTime = LocalDateTime.of(2026, 10, 26, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val date = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(dateTime, parseCalendarMillis("2026-10-26 10:00", zone))
        assertEquals(dateTime, parseCalendarMillis("2026-10-26T10:00", zone))
        assertEquals(date, parseCalendarMillis("2026-10-26", zone))
        assertEquals(1_793_000_000_000L, parseCalendarMillis("1793000000000", zone))
        assertEquals(
            dateTime,
            parseCalendarMillis("2026-10-26T10:00:00+08:00", zone),
        )
    }

    @Test
    fun rejectsUnparsableCalendarTime() {
        assertThrows(IllegalArgumentException::class.java) { parseCalendarMillis("明天", zone) }
        assertThrows(IllegalArgumentException::class.java) { parseCalendarMillis("  ", zone) }
    }

    @Test
    fun buildsCalendarCreateSpecWithPrefilledExtras() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)
        val end = parseCalendarMillis("2026-10-26 11:30", zone)

        val spec = calendarCreateRequest("团队会议", start, end, allDay = false, location = "3 楼", description = "带电脑")

        assertEquals(Intent.ACTION_INSERT, spec.action)
        assertEquals(CALENDAR_EVENTS_URI, spec.data)
        assertEquals(start, spec.extras[CalendarContract.EXTRA_EVENT_BEGIN_TIME])
        assertEquals(end, spec.extras[CalendarContract.EXTRA_EVENT_END_TIME])
        assertEquals("团队会议", spec.extras[CalendarContract.Events.TITLE])
        assertEquals("3 楼", spec.extras[CalendarContract.Events.EVENT_LOCATION])
        assertEquals("带电脑", spec.extras[CalendarContract.Events.DESCRIPTION])
        assertFalse(spec.extras.containsKey(CalendarContract.EXTRA_EVENT_ALL_DAY))
    }

    @Test
    fun calendarCreateDefaultsToOneHourAndSupportsAllDay() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)

        val defaultEnd = calendarCreateRequest("A", start, null, allDay = false, location = null, description = null)
        assertEquals(start + DEFAULT_CALENDAR_DURATION_MILLIS, defaultEnd.extras[CalendarContract.EXTRA_EVENT_END_TIME])

        val allDay = calendarCreateRequest("B", start, null, allDay = true, location = null, description = null)
        assertEquals(true, allDay.extras[CalendarContract.EXTRA_EVENT_ALL_DAY])
        assertFalse(allDay.extras.containsKey(CalendarContract.EXTRA_EVENT_END_TIME))
    }

    @Test
    fun calendarCreateValidatesInputs() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)

        assertThrows(IllegalArgumentException::class.java) {
            calendarCreateRequest("  ", start, null, allDay = false, location = null, description = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calendarCreateRequest("A", start, start - 1, allDay = false, location = null, description = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calendarCreateRequest("a".repeat(MAX_LABEL_LENGTH + 1), start, null, allDay = false, location = null, description = null)
        }
    }

    @Test
    fun calendarOpenSpecSupportsAppAndTimeUri() {
        val main = calendarOpenRequest(null)
        assertEquals(Intent.ACTION_MAIN, main.action)
        assertEquals(listOf(Intent.CATEGORY_APP_CALENDAR), main.categories)
        assertNull(main.data)

        val timed = calendarOpenRequest(1_793_000_000_000L)
        assertEquals(Intent.ACTION_VIEW, timed.action)
        assertEquals("$CALENDAR_TIME_URI/1793000000000", timed.data)
    }

    @Test
    fun formatsDuration() {
        assertEquals("1 分", formatDuration(60))
        assertEquals("1 小时", formatDuration(3_600))
        assertEquals("1 分 30 秒", formatDuration(90))
        assertEquals("1 小时 1 分 1 秒", formatDuration(3_661))
    }

    @Test
    fun formatsCalendarRange() {
        val start = parseCalendarMillis("2026-10-26 10:00", zone)
        val end = parseCalendarMillis("2026-10-26 11:30", zone)

        assertEquals("2026-10-26 10:00–2026-10-26 11:30", formatCalendarRange(start, end, allDay = false, zone))
        assertEquals("2026-10-26", formatCalendarRange(start, end, allDay = true, zone))
    }
}
