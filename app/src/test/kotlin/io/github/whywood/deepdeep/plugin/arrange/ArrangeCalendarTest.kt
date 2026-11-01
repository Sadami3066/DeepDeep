package io.github.whywood.deepdeep.plugin.arrange

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeCalendarTest {
    private val allDayZones = listOf("Asia/Shanghai", "America/New_York", "UTC", "America/Santiago")

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun timedRangeDefaultsToOneHour() {
        val start = 10 * ARRANGE_HOUR_MILLIS
        assertEquals(start to start + ARRANGE_HOUR_MILLIS, resolveEventRange(start, null, allDay = false))
    }

    @Test
    fun timedRangeKeepsExplicitEnd() {
        val start = 10 * ARRANGE_HOUR_MILLIS
        val end = start + 3 * ARRANGE_HOUR_MILLIS
        assertEquals(start to end, resolveEventRange(start, end, allDay = false))
    }

    @Test
    fun timedRangeRejectsNonPositiveDuration() {
        val start = 10 * ARRANGE_HOUR_MILLIS
        assertThrows(IllegalArgumentException::class.java) {
            resolveEventRange(start, start, allDay = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveEventRange(start, start - 1, allDay = false)
        }
    }

    @Test
    fun allDayRangeEncodesRequestedDateAtUtcMidnight() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val date = LocalDate.of(2026, 10, 30)
            val start = parseCalendarMillis("2026-10-30", zone)
            val range = resolveEventRange(start, null, allDay = true, zone = zone)
            assertEquals(zoneId, utcMidnight(date), range.first)
            assertEquals(zoneId, utcMidnight(date.plusDays(1)), range.second)
            assertEquals(zoneId, "2026-10-30", formatCalendarTime(range.first, allDay = true, zone = zone))
            assertEquals(zoneId, "2026-10-31", formatCalendarTime(range.second, allDay = true, zone = zone))
        }
    }

    @Test
    fun allDayRangeCoversInclusiveEndDate() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val start = parseCalendarMillis("2026-10-30", zone)
            val end = parseCalendarMillis("2026-11-01", zone)
            val range = resolveEventRange(start, end, allDay = true, zone = zone)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 30)), range.first)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 11, 2)), range.second)
        }
    }

    @Test
    fun allDayRangeExplicitSameDayEndBecomesNextMidnight() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val start = parseCalendarMillis("2026-10-30", zone)
            val end = parseCalendarMillis("2026-10-30", zone)
            val range = resolveEventRange(start, end, allDay = true, zone = zone)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 30)), range.first)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 31)), range.second)
        }
    }

    @Test
    fun allDayRangeNeverEndsBeforeStart() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val start = parseCalendarMillis("2026-10-30", zone)
            val end = parseCalendarMillis("2026-10-28", zone)
            val range = resolveEventRange(start, end, allDay = true, zone = zone)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 30)), range.first)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 31)), range.second)
        }
    }

    @Test
    fun accessLevelWritableBoundary() {
        assertFalse(accessLevelIsWritable(0))
        assertFalse(accessLevelIsWritable(499))
        assertTrue(accessLevelIsWritable(500))
        assertTrue(accessLevelIsWritable(700))
    }

    @Test
    fun knownResourceKeyBecomesReadableCalendarName() {
        assertEquals("小米日历", calendarDisplayName("calendar_displayname_xiaomi"))
        assertEquals("我的日历", calendarDisplayName(" 我的日历 "))
        assertEquals("calendar_displayname_other", calendarDisplayName("calendar_displayname_other"))
        assertEquals("", calendarDisplayName(""))
    }

    @Test
    fun limitedSortOrderAppendsValidatedLimit() {
        assertEquals("BEGIN ASC LIMIT 5", limitedSortOrder("BEGIN ASC", 5))
        assertEquals("BEGIN ASC LIMIT 1", limitedSortOrder("BEGIN ASC", 1))
        assertEquals("BEGIN ASC LIMIT 200", limitedSortOrder("BEGIN ASC", 200))
    }

    @Test
    fun limitedSortOrderRejectsOutOfRangeLimit() {
        listOf(0, -1, 201, 1_000).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) {
                limitedSortOrder("BEGIN ASC", limit)
            }
        }
    }

    @Test
    fun validatedReminderMinutesEnforcesCountAndNonNegative() {
        assertEquals(emptyList<Int>(), validatedReminderMinutes(emptyList()))
        val five = listOf(0, 5, 10, 15, 30)
        assertEquals(five, validatedReminderMinutes(five))
        assertThrows(IllegalArgumentException::class.java) {
            validatedReminderMinutes(listOf(0, 5, 10, 15, 30, 60))
        }
        assertThrows(IllegalArgumentException::class.java) { validatedReminderMinutes(listOf(-1)) }
    }

    @Test
    fun mergeWithoutChangesKeepsExistingEvent() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 11 * ARRANGE_HOUR_MILLIS)
        val update = mergeEventPatch(current, ArrangeEventPatch())
        assertNull(update.title)
        assertNull(update.location)
        assertNull(update.description)
        assertNull(update.calendarId)
        assertNull(update.reminderMinutes)
        assertFalse(update.timesChanged)
        assertEquals(current.startMillis, update.startMillis)
        assertEquals(current.endMillis, update.endMillis)
        assertFalse(update.allDay)
    }

    @Test
    fun mergeClearsTextAndRemindersWithEmptyValues() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 11 * ARRANGE_HOUR_MILLIS)
        val update = mergeEventPatch(
            current,
            ArrangeEventPatch(
                title = "",
                location = "",
                description = "",
                reminderMinutes = emptyList(),
            ),
        )
        assertEquals("", update.title)
        assertEquals("", update.location)
        assertEquals("", update.description)
        assertEquals(emptyList<Int>(), update.reminderMinutes)
        assertFalse(update.timesChanged)
    }

    @Test
    fun mergeStartOnlyDefaultsToOneHour() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 12 * ARRANGE_HOUR_MILLIS)
        val newStart = 20 * ARRANGE_HOUR_MILLIS
        val update = mergeEventPatch(current, ArrangeEventPatch(startMillis = newStart))
        assertTrue(update.timesChanged)
        assertEquals(newStart, update.startMillis)
        assertEquals(newStart + ARRANGE_HOUR_MILLIS, update.endMillis)
    }

    @Test
    fun mergeEndOnlyKeepsCurrentStart() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 12 * ARRANGE_HOUR_MILLIS)
        val newEnd = 15 * ARRANGE_HOUR_MILLIS
        val update = mergeEventPatch(current, ArrangeEventPatch(endMillis = newEnd))
        assertTrue(update.timesChanged)
        assertEquals(current.startMillis, update.startMillis)
        assertEquals(newEnd, update.endMillis)
    }

    @Test
    fun mergeAllDayEncodesLocalDatesAtUtcMidnight() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val current = timedEvent(
                startMillis = parseCalendarMillis("2026-10-26 10:00", zone),
                endMillis = parseCalendarMillis("2026-10-26 11:00", zone),
            )
            val update = mergeEventPatch(current, ArrangeEventPatch(allDay = true), zone = zone)
            assertTrue(update.allDay)
            assertTrue(update.timesChanged)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 26)), update.startMillis)
            assertEquals(zoneId, utcMidnight(LocalDate.of(2026, 10, 27)), update.endMillis)
        }
    }

    @Test
    fun mergeAllDayKeepsExistingDatesAcrossZones() {
        allDayZones.forEach { zoneId ->
            val zone = ZoneId.of(zoneId)
            val start = utcMidnight(LocalDate.of(2026, 10, 30))
            val end = utcMidnight(LocalDate.of(2026, 10, 31))
            val current = ArrangeCalendarEvent(
                id = 1,
                calendarId = 1,
                title = "全天",
                startMillis = start,
                endMillis = end,
                allDay = true,
            )
            val update = mergeEventPatch(current, ArrangeEventPatch(allDay = true), zone = zone)
            assertEquals(zoneId, start, update.startMillis)
            assertEquals(zoneId, end, update.endMillis)
        }
    }

    @Test
    fun mergeTitleOnlyKeepsRange() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 11 * ARRANGE_HOUR_MILLIS)
        val update = mergeEventPatch(current, ArrangeEventPatch(title = "新标题"))
        assertEquals("新标题", update.title)
        assertFalse(update.timesChanged)
        assertEquals(current.startMillis, update.startMillis)
        assertEquals(current.endMillis, update.endMillis)
    }

    @Test
    fun mergeRejectsInvalidTimedRange() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 11 * ARRANGE_HOUR_MILLIS)
        assertThrows(IllegalArgumentException::class.java) {
            mergeEventPatch(current, ArrangeEventPatch(endMillis = current.startMillis))
        }
    }

    @Test
    fun mergeValidatesReminderCount() {
        val current = timedEvent(startMillis = 10 * ARRANGE_HOUR_MILLIS, endMillis = 11 * ARRANGE_HOUR_MILLIS)
        assertThrows(IllegalArgumentException::class.java) {
            mergeEventPatch(
                current,
                ArrangeEventPatch(reminderMinutes = listOf(0, 5, 10, 15, 30, 60)),
            )
        }
    }

    @Test
    fun eventDefaultsEventIdToIdAndIsNotRecurring() {
        val event = ArrangeCalendarEvent(
            id = 7,
            calendarId = 1,
            title = "会议",
            startMillis = 0,
            endMillis = ARRANGE_HOUR_MILLIS,
            allDay = false,
        )
        assertEquals(7, event.eventId)
        assertFalse(event.recurring)
        assertNull(event.location)
        assertNull(event.description)
        assertNull(event.timezone)
    }

    @Test
    fun instanceEventsUseEventIdForMutationTargets() {
        val first = arrangeEventForInstance(
            eventId = 42,
            calendarId = 1,
            title = "每周会议",
            startMillis = 0,
            endMillis = ARRANGE_HOUR_MILLIS,
            allDay = false,
            location = null,
            description = null,
            timezone = "Asia/Shanghai",
        )
        val second = first.copy(
            startMillis = 7 * ARRANGE_DAY_MILLIS,
            endMillis = 7 * ARRANGE_DAY_MILLIS + ARRANGE_HOUR_MILLIS,
        )
        assertEquals(42, first.id)
        assertEquals(42, first.eventId)
        assertEquals(first.eventId, second.id)
        assertFalse(first.recurring)
    }

    private fun timedEvent(startMillis: Long, endMillis: Long): ArrangeCalendarEvent = ArrangeCalendarEvent(
        id = 1,
        calendarId = 1,
        title = "当前事件",
        startMillis = startMillis,
        endMillis = endMillis,
        allDay = false,
    )
}
