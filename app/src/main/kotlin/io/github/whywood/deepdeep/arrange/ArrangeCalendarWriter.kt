package io.github.whywood.deepdeep.arrange

internal data class ArrangeReminderSnapshot(
    val id: Long,
    val minutes: Int,
    val method: Int,
)

internal interface ArrangeEventStore {
    fun insertEvent(
        draft: ArrangeEventDraft,
        calendarId: Long,
        range: Pair<Long, Long>,
        hasAlarm: Boolean,
    ): Long

    fun updateEvent(id: Long, update: ArrangeEventUpdate)

    fun deleteEvent(id: Long)

    fun loadEvent(id: Long): ArrangeCalendarEvent?

    fun loadReminders(id: Long): List<ArrangeReminderSnapshot>

    fun replaceReminders(id: Long, minutes: List<Int>)

    fun restoreReminders(id: Long, snapshot: List<ArrangeReminderSnapshot>)
}

internal class ArrangeCalendarWriter(
    private val store: ArrangeEventStore,
) {
    fun create(
        draft: ArrangeEventDraft,
        calendarId: Long,
        range: Pair<Long, Long>,
        reminders: List<Int>,
    ): ArrangeCalendarEvent {
        val eventId = store.insertEvent(draft, calendarId, range, reminders.isNotEmpty())
        try {
            store.replaceReminders(eventId, reminders)
        } catch (error: Exception) {
            try {
                store.deleteEvent(eventId)
            } catch (_: Exception) {
            }
            throw error
        }
        return store.loadEvent(eventId)
            ?: throw IllegalStateException("Event was written but could not be read back; try again later")
    }

    fun update(id: Long, change: ArrangeEventUpdate): ArrangeCalendarEvent? {
        val reminders = change.reminderMinutes
        val snapshot: List<ArrangeReminderSnapshot> = if (reminders == null) {
            emptyList()
        } else {
            store.loadReminders(id)
        }
        store.updateEvent(id, change)
        if (reminders != null) {
            try {
                store.replaceReminders(id, reminders)
            } catch (error: Exception) {
                try {
                    store.restoreReminders(id, snapshot)
                } catch (_: Exception) {
                }
                throw error
            }
        }
        return store.loadEvent(id)
    }
}
