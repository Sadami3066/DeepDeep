package io.github.whywood.deepdeep.plugin.arrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeCalendarTest {
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
    fun allDayRangeSnapsToUtcMidnight() {
        val start = 2 * ARRANGE_DAY_MILLIS + 5 * ARRANGE_HOUR_MILLIS
        assertEquals(
            2 * ARRANGE_DAY_MILLIS to 3 * ARRANGE_DAY_MILLIS,
            resolveEventRange(start, null, allDay = true),
        )
    }

    @Test
    fun allDayRangeUsesDayAfterExplicitEndDate() {
        val start = 2 * ARRANGE_DAY_MILLIS + 5 * ARRANGE_HOUR_MILLIS
        val end = 4 * ARRANGE_DAY_MILLIS + 9 * ARRANGE_HOUR_MILLIS
        assertEquals(
            2 * ARRANGE_DAY_MILLIS to 5 * ARRANGE_DAY_MILLIS,
            resolveEventRange(start, end, allDay = true),
        )
    }

    @Test
    fun allDayRangeExplicitSameDayEndBecomesNextMidnight() {
        val start = 2 * ARRANGE_DAY_MILLIS + ARRANGE_HOUR_MILLIS
        val end = 2 * ARRANGE_DAY_MILLIS + 20 * ARRANGE_HOUR_MILLIS
        assertEquals(
            2 * ARRANGE_DAY_MILLIS to 3 * ARRANGE_DAY_MILLIS,
            resolveEventRange(start, end, allDay = true),
        )
    }

    @Test
    fun allDayRangeNeverEndsBeforeStart() {
        val start = 3 * ARRANGE_DAY_MILLIS
        val end = 2 * ARRANGE_DAY_MILLIS
        assertEquals(
            3 * ARRANGE_DAY_MILLIS to 4 * ARRANGE_DAY_MILLIS,
            resolveEventRange(start, end, allDay = true),
        )
    }

    @Test
    fun accessLevelWritableBoundary() {
        assertFalse(accessLevelIsWritable(0))
        assertFalse(accessLevelIsWritable(499))
        assertTrue(accessLevelIsWritable(500))
        assertTrue(accessLevelIsWritable(700))
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
    fun mergeAllDaySnapsRangeToUtcMidnight() {
        val current = timedEvent(
            startMillis = 2 * ARRANGE_DAY_MILLIS + 5 * ARRANGE_HOUR_MILLIS,
            endMillis = 2 * ARRANGE_DAY_MILLIS + 6 * ARRANGE_HOUR_MILLIS,
        )
        val update = mergeEventPatch(current, ArrangeEventPatch(allDay = true))
        assertTrue(update.allDay)
        assertTrue(update.timesChanged)
        assertEquals(2 * ARRANGE_DAY_MILLIS, update.startMillis)
        assertEquals(3 * ARRANGE_DAY_MILLIS, update.endMillis)
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
