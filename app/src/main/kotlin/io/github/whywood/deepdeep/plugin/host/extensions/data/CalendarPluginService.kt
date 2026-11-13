package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import io.github.whywood.deepdeep.arrange.ARRANGE_EVENT_LIMIT_MAX
import io.github.whywood.deepdeep.arrange.ArrangeCalendarEvent
import io.github.whywood.deepdeep.arrange.ArrangeCalendarGateway
import io.github.whywood.deepdeep.arrange.ArrangeCalendarInfo
import io.github.whywood.deepdeep.arrange.ArrangeEventDraft
import io.github.whywood.deepdeep.arrange.ArrangeEventPatch
import io.github.whywood.deepdeep.plugin.api.CalendarService
import io.github.whywood.deepdeep.plugin.api.PluginCalendar
import io.github.whywood.deepdeep.plugin.api.PluginCalendarEvent
import io.github.whywood.deepdeep.plugin.api.PluginCalendarEventDraft
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun pluginCalendar(info: ArrangeCalendarInfo): PluginCalendar = PluginCalendar(
    id = info.id.toString(),
    name = info.name,
    accountName = info.accountName.takeIf { it.isNotBlank() },
    displayName = info.name,
    color = null,
    visible = true,
    primary = info.primary,
)

internal fun pluginCalendarEvent(event: ArrangeCalendarEvent, reminders: List<Int>): PluginCalendarEvent =
    PluginCalendarEvent(
        id = event.eventId.toString(),
        calendarId = event.calendarId.toString(),
        title = event.title,
        startMs = event.startMillis,
        endMs = event.endMillis,
        allDay = event.allDay,
        description = event.description?.takeIf { it.isNotBlank() },
        location = event.location?.takeIf { it.isNotBlank() },
        timeZone = event.timezone?.takeIf { it.isNotBlank() },
        reminders = reminders.sorted(),
    )

internal fun arrangeEventDraft(draft: PluginCalendarEventDraft): ArrangeEventDraft = ArrangeEventDraft(
    title = draft.title,
    startMillis = draft.startMs,
    endMillis = draft.endMs,
    allDay = draft.allDay,
    location = draft.location,
    description = draft.description,
    calendarId = draft.calendarId?.toLongOrNull(),
    reminderMinutes = draft.reminders,
)

internal fun arrangeEventPatch(draft: PluginCalendarEventDraft): ArrangeEventPatch = ArrangeEventPatch(
    title = draft.title,
    startMillis = draft.startMs,
    endMillis = draft.endMs,
    allDay = draft.allDay,
    location = draft.location,
    description = draft.description,
    calendarId = draft.calendarId?.toLongOrNull(),
    reminderMinutes = draft.reminders,
)

internal class CalendarPluginServiceImpl(
    private val gateway: ArrangeCalendarGateway,
    private val interaction: PluginInteraction,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CalendarService {
    private val resolver: ContentResolver = context.contentResolver

    override val permissionGranted: Boolean
        get() = gateway.hasReadPermission()

    override suspend fun calendars(): List<PluginCalendar> {
        ensurePermissions(DataPermissions.calendarRead, "read")
        return withContext(ioDispatcher) { gateway.calendars().map(::pluginCalendar) }
    }

    override suspend fun events(
        fromMs: Long,
        toMs: Long,
        calendarIds: List<String>,
        limit: Int,
    ): List<PluginCalendarEvent> {
        ensurePermissions(DataPermissions.calendarRead, "read")
        require(toMs > fromMs) { "Calendar range end must be after range start" }
        return withContext(ioDispatcher) {
            val ids = calendarIds.mapNotNull(String::toLongOrNull).toSet()
            val events = gateway.list(fromMs, toMs, null, limit.coerceIn(1, ARRANGE_EVENT_LIMIT_MAX))
                .filter { ids.isEmpty() || it.calendarId in ids }
            val reminders = remindersFor(events.map { it.eventId }.distinct())
            events.map { event -> pluginCalendarEvent(event, reminders[event.eventId].orEmpty()) }
        }
    }

    override suspend fun get(eventId: String): PluginCalendarEvent? {
        ensurePermissions(DataPermissions.calendarRead, "read")
        val id = eventId.toLongOrNull() ?: return null
        return withContext(ioDispatcher) {
            val event = gateway.get(id) ?: return@withContext null
            pluginCalendarEvent(event, remindersFor(listOf(event.eventId))[event.eventId].orEmpty())
        }
    }

    override suspend fun create(draft: PluginCalendarEventDraft): PluginCalendarEvent? {
        ensurePermissions(DataPermissions.calendarWrite, "write")
        return withContext(ioDispatcher) {
            val created = gateway.create(arrangeEventDraft(draft))
            pluginCalendarEvent(created, remindersFor(listOf(created.eventId))[created.eventId].orEmpty())
        }
    }

    override suspend fun update(eventId: String, draft: PluginCalendarEventDraft): PluginCalendarEvent? {
        ensurePermissions(DataPermissions.calendarWrite, "write")
        val id = eventId.toLongOrNull() ?: return null
        return withContext(ioDispatcher) {
            val updated = gateway.update(id, arrangeEventPatch(draft)) ?: return@withContext null
            pluginCalendarEvent(updated, remindersFor(listOf(updated.eventId))[updated.eventId].orEmpty())
        }
    }

    override suspend fun delete(eventId: String): Boolean {
        ensurePermissions(DataPermissions.calendarWrite, "write")
        val id = eventId.toLongOrNull() ?: return false
        return withContext(ioDispatcher) { gateway.delete(id) }
    }

    private suspend fun ensurePermissions(permissions: Array<String>, action: String) {
        if (DataPermissions.allGranted(context, permissions)) return
        val result = interaction.requestPermissions(permissions)
        val granted = permissions.all { result[it] == true }
        if (!granted) {
            throw SecurityException(
                "Calendar $action permission was not granted; grant calendar access for DeepDeep and retry",
            )
        }
    }

    private fun remindersFor(eventIds: List<Long>): Map<Long, List<Int>> {
        if (eventIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<Int>>()
        val selection = "${CalendarContract.Reminders.EVENT_ID} IN (${eventIds.joinToString(",") { "?" }})"
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
            selection,
            eventIds.map { it.toString() }.toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.getOrPut(cursor.getLong(0)) { mutableListOf() } += cursor.getInt(1)
            }
        }
        return result
    }

    private companion object {
        @Suppress("unused")
        val REQUIRED: String = Manifest.permission.READ_CALENDAR
    }
}
