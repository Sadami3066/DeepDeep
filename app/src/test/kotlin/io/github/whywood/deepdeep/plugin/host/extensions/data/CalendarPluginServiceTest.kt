package io.github.whywood.deepdeep.plugin.host.extensions.data

import io.github.whywood.deepdeep.arrange.ArrangeCalendarEvent
import io.github.whywood.deepdeep.arrange.ArrangeCalendarInfo
import io.github.whywood.deepdeep.plugin.api.pluginCalendarEventDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPluginServiceTest {
    private val arrangeEvent = ArrangeCalendarEvent(
        id = 11,
        calendarId = 7,
        title = "Standup",
        startMillis = 1_700_000_000_000,
        endMillis = 1_700_000_600_000,
        allDay = false,
        location = "Room 1",
        description = "Daily sync",
        timezone = "Asia/Shanghai",
        recurring = true,
        eventId = 11,
    )

    @Test
    fun mapsCalendarInfo() {
        val calendar = pluginCalendar(
            ArrangeCalendarInfo(
                id = 7,
                name = "Primary",
                accountName = "alice@example.com",
                accountType = "com.google",
                writable = true,
                primary = true,
            ),
        )
        assertEquals("7", calendar.id)
        assertEquals("Primary", calendar.name)
        assertEquals("alice@example.com", calendar.accountName)
        assertEquals("Primary", calendar.displayName)
        assertNull(calendar.color)
        assertTrue(calendar.visible)
        assertTrue(calendar.primary)
    }

    @Test
    fun mapsEventAndReminders() {
        val event = pluginCalendarEvent(arrangeEvent, listOf(15, 5))
        assertEquals("11", event.id)
        assertEquals("7", event.calendarId)
        assertEquals("Standup", event.title)
        assertEquals(1_700_000_000_000L, event.startMs)
        assertEquals(1_700_000_600_000L, event.endMs)
        assertEquals("Room 1", event.location)
        assertEquals("Asia/Shanghai", event.timeZone)
        assertEquals(listOf(5, 15), event.reminders)
    }

    @Test
    fun blankOptionalFieldsBecomeNull() {
        val event = pluginCalendarEvent(arrangeEvent.copy(location = " ", description = ""), emptyList())
        assertNull(event.location)
        assertNull(event.description)
        assertTrue(event.reminders.isEmpty())
    }

    @Test
    fun mapsDraftToGatewayTypes() {
        val draft = pluginCalendarEventDraft(
            title = "Review",
            startMs = 100L,
            endMs = 200L,
            allDay = true,
            description = "desc",
            location = "loc",
            calendarId = "7",
            timeZone = "UTC",
            reminders = listOf(10),
        )
        val arrangeDraft = arrangeEventDraft(draft)
        assertEquals("Review", arrangeDraft.title)
        assertEquals(100L, arrangeDraft.startMillis)
        assertEquals(200L, arrangeDraft.endMillis)
        assertTrue(arrangeDraft.allDay)
        assertEquals(7L, arrangeDraft.calendarId)
        assertEquals(listOf(10), arrangeDraft.reminderMinutes)

        val patch = arrangeEventPatch(draft)
        assertEquals("Review", patch.title)
        assertEquals(100L, patch.startMillis)
        assertEquals(7L, patch.calendarId)
        assertEquals(listOf(10), patch.reminderMinutes)
    }

    @Test
    fun invalidCalendarIdBecomesNull() {
        val draft = pluginCalendarEventDraft(
            title = "Review",
            startMs = 100L,
            endMs = 200L,
            calendarId = "not-a-number",
        )
        assertNull(arrangeEventDraft(draft).calendarId)
        assertNull(arrangeEventPatch(draft).calendarId)
        assertTrue(arrangeEventDraft(draft).reminderMinutes.isNullOrEmpty())
    }
}
