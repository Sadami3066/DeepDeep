package io.github.whywood.deepdeep.plugin.arrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeCalendarWriterTest {
    private val store = FakeEventStore()
    private val writer = ArrangeCalendarWriter(store)

    @Test
    fun createDeletesEventWhenReminderWriteFails() {
        store.failReminderWrite = true

        val error = assertThrows(IllegalStateException::class.java) {
            writer.create(
                draft = ArrangeEventDraft(title = "会议", startMillis = 1_000L, endMillis = 2_000L),
                calendarId = 1L,
                range = 1_000L to 2_000L,
                reminders = listOf(15),
            )
        }

        assertSame(store.lastWriteError, error)
        assertTrue(store.events.isEmpty())
        assertTrue(store.reminders.isEmpty())
    }

    @Test
    fun updateKeepsPreviousRemindersWhenReplacementFails() {
        val id = seedEvent()
        store.reminders[id] = mutableListOf(
            ArrangeReminderSnapshot(id = 1L, minutes = 10, method = 1),
            ArrangeReminderSnapshot(id = 2L, minutes = 30, method = 1),
        )
        store.failReminderWrite = true
        val change = mergeEventPatch(
            store.loadEvent(id)!!,
            ArrangeEventPatch(title = "新标题", reminderMinutes = listOf(5)),
        )

        val error = assertThrows(IllegalStateException::class.java) { writer.update(id, change) }

        assertSame(store.lastWriteError, error)
        assertEquals(listOf(10, 30), store.reminders[id]?.map { it.minutes })
    }

    @Test
    fun updatePropagatesSnapshotFailureBeforeChangingEvent() {
        val id = seedEvent()
        store.reminders[id] = mutableListOf(ArrangeReminderSnapshot(id = 1L, minutes = 10, method = 1))
        store.failReminderSnapshot = true
        val change = mergeEventPatch(
            store.loadEvent(id)!!,
            ArrangeEventPatch(title = "新标题", reminderMinutes = listOf(5)),
        )

        val error = assertThrows(IllegalStateException::class.java) { writer.update(id, change) }

        assertEquals("读取事件提醒失败", error.message)
        assertEquals("旧标题", store.events[id]?.title)
        assertEquals(listOf(10), store.reminders[id]?.map { it.minutes })
    }

    private fun seedEvent(): Long = store.insertEvent(
        draft = ArrangeEventDraft(title = "旧标题", startMillis = 1_000L, endMillis = 2_000L),
        calendarId = 1L,
        range = 1_000L to 2_000L,
        hasAlarm = true,
    )

    private class FakeEventStore : ArrangeEventStore {
        val events = mutableMapOf<Long, ArrangeCalendarEvent>()
        val reminders = mutableMapOf<Long, MutableList<ArrangeReminderSnapshot>>()
        var failReminderWrite = false
        var failReminderSnapshot = false
        var lastWriteError: IllegalStateException? = null
        private var nextEventId = 1L
        private var nextReminderId = 100L

        override fun insertEvent(
            draft: ArrangeEventDraft,
            calendarId: Long,
            range: Pair<Long, Long>,
            hasAlarm: Boolean,
        ): Long {
            val id = nextEventId++
            events[id] = ArrangeCalendarEvent(
                id = id,
                calendarId = calendarId,
                title = draft.title,
                startMillis = range.first,
                endMillis = range.second,
                allDay = draft.allDay,
                location = draft.location,
                description = draft.description,
            )
            reminders[id] = mutableListOf()
            return id
        }

        override fun updateEvent(id: Long, update: ArrangeEventUpdate) {
            val current = events[id] ?: return
            events[id] = current.copy(
                title = update.title ?: current.title,
                startMillis = update.startMillis,
                endMillis = update.endMillis,
                allDay = update.allDay,
                location = update.location ?: current.location,
                description = update.description ?: current.description,
            )
        }

        override fun deleteEvent(id: Long) {
            events.remove(id)
            reminders.remove(id)
        }

        override fun loadEvent(id: Long): ArrangeCalendarEvent? = events[id]

        override fun loadReminders(id: Long): List<ArrangeReminderSnapshot> {
            if (failReminderSnapshot) throw IllegalStateException("读取事件提醒失败")
            return reminders[id].orEmpty()
        }

        override fun replaceReminders(id: Long, minutes: List<Int>) {
            reminders[id] = mutableListOf()
            if (failReminderWrite) {
                lastWriteError = IllegalStateException("写入事件提醒失败")
                throw lastWriteError!!
            }
            reminders[id] = minutes.map {
                ArrangeReminderSnapshot(id = nextReminderId++, minutes = it, method = 1)
            }.toMutableList()
        }

        override fun restoreReminders(id: Long, snapshot: List<ArrangeReminderSnapshot>) {
            reminders[id] = snapshot.toMutableList()
        }
    }
}
