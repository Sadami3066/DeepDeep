package io.github.whywood.deepdeep.plugin.arrange

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

internal const val ARRANGE_EVENT_LIMIT_MAX = 200
internal const val ARRANGE_HOUR_MILLIS = 3_600_000L
internal const val ARRANGE_DAY_MILLIS = 86_400_000L
internal const val ARRANGE_WRITABLE_ACCESS_LEVEL = 500
internal const val ARRANGE_MAX_REMINDERS = 5
private const val UTC_TIMEZONE = "UTC"

data class ArrangeCalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val writable: Boolean,
    val primary: Boolean,
)

data class ArrangeCalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String? = null,
    val description: String? = null,
    val timezone: String? = null,
    val recurring: Boolean = false,
    val eventId: Long = id,
)

data class ArrangeEventDraft(
    val title: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val calendarId: Long? = null,
    val reminderMinutes: List<Int>? = null,
)

data class ArrangeEventPatch(
    val title: String? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val allDay: Boolean? = null,
    val location: String? = null,
    val description: String? = null,
    val calendarId: Long? = null,
    val reminderMinutes: List<Int>? = null,
)

class ArrangeCalendarPermissionException(
    message: String,
) : SecurityException(message)

class ArrangeNoWritableCalendarException(
    message: String = "No writable system calendar; create or sync one in a calendar app first",
) : IllegalStateException(message)

interface ArrangeCalendarGateway {
    fun hasReadPermission(): Boolean

    fun hasWritePermission(): Boolean

    fun calendars(): List<ArrangeCalendarInfo>

    fun list(startMillis: Long, endMillis: Long, query: String?, limit: Int): List<ArrangeCalendarEvent>

    fun get(id: Long): ArrangeCalendarEvent?

    fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent

    fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent?

    fun delete(id: Long): Boolean
}

internal fun accessLevelIsWritable(accessLevel: Int): Boolean = accessLevel >= ARRANGE_WRITABLE_ACCESS_LEVEL

internal fun calendarDisplayName(raw: String): String = when (raw.trim()) {
    "calendar_displayname_xiaomi" -> "Xiaomi Calendar"
    else -> raw.trim()
}

internal fun limitedSortOrder(sortOrder: String, limit: Int): String {
    require(limit in 1..ARRANGE_EVENT_LIMIT_MAX) { "limit must be between 1 and $ARRANGE_EVENT_LIMIT_MAX" }
    return "$sortOrder LIMIT $limit"
}

internal fun resolveEventRange(
    startMillis: Long,
    endMillis: Long?,
    allDay: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    if (allDay) {
        val startDate = localDateOf(startMillis, zone)
        val endDate = endMillis?.let { localDateOf(it, zone) } ?: startDate
        val lastDate = if (endDate.isBefore(startDate)) startDate else endDate
        return utcMidnight(startDate) to utcMidnight(lastDate.plusDays(1))
    }
    val end = endMillis ?: (startMillis + ARRANGE_HOUR_MILLIS)
    require(end > startMillis) { "End time must be after start time" }
    return startMillis to end
}

internal fun validatedReminderMinutes(minutes: List<Int>): List<Int> {
    require(minutes.size <= ARRANGE_MAX_REMINDERS) { "At most $ARRANGE_MAX_REMINDERS reminders are allowed" }
    require(minutes.all { it >= 0 }) { "Reminder minutes must not be negative" }
    return minutes
}

internal data class ArrangeEventUpdate(
    val title: String?,
    val timesChanged: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val calendarId: Long?,
    val reminderMinutes: List<Int>?,
)

internal fun mergeEventPatch(
    current: ArrangeCalendarEvent,
    patch: ArrangeEventPatch,
    zone: ZoneId = ZoneId.systemDefault(),
): ArrangeEventUpdate {
    val timesChanged = patch.startMillis != null || patch.endMillis != null || patch.allDay != null
    val allDay = patch.allDay ?: current.allDay
    val range = when {
        !timesChanged -> current.startMillis to current.endMillis
        allDay -> mergeAllDayRange(current, patch, zone)
        else -> resolveEventRange(patch.startMillis ?: current.startMillis, patch.endMillis, false, zone)
    }
    return ArrangeEventUpdate(
        title = patch.title,
        timesChanged = timesChanged,
        startMillis = range.first,
        endMillis = range.second,
        allDay = allDay,
        location = patch.location,
        description = patch.description,
        calendarId = patch.calendarId,
        reminderMinutes = patch.reminderMinutes?.let { validatedReminderMinutes(it) },
    )
}

private fun mergeAllDayRange(
    current: ArrangeCalendarEvent,
    patch: ArrangeEventPatch,
    zone: ZoneId,
): Pair<Long, Long> {
    val startDate = patch.startMillis?.let { localDateOf(it, zone) }
        ?: if (current.allDay) {
            localDateOf(current.startMillis, ZoneOffset.UTC)
        } else {
            localDateOf(current.startMillis, zone)
        }
    val endDate = patch.endMillis?.let { localDateOf(it, zone) }
        ?: if (current.allDay && patch.startMillis == null) {
            localDateOf(current.endMillis, ZoneOffset.UTC).minusDays(1)
        } else {
            startDate
        }
    val lastDate = if (endDate.isBefore(startDate)) startDate else endDate
    return utcMidnight(startDate) to utcMidnight(lastDate.plusDays(1))
}

private fun localDateOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun utcMidnight(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun arrangeEventForInstance(
    eventId: Long,
    calendarId: Long,
    title: String,
    startMillis: Long,
    endMillis: Long,
    allDay: Boolean,
    location: String?,
    description: String?,
    timezone: String?,
): ArrangeCalendarEvent = ArrangeCalendarEvent(
    id = eventId,
    calendarId = calendarId,
    title = title,
    startMillis = startMillis,
    endMillis = endMillis,
    allDay = allDay,
    location = location,
    description = description,
    timezone = timezone,
    recurring = false,
    eventId = eventId,
)

class AndroidArrangeCalendarGateway(context: Context) : ArrangeCalendarGateway {
    private val context: Context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver
    private val writer = ArrangeCalendarWriter(ResolverEventStore())

    override fun hasReadPermission(): Boolean = hasPermission(Manifest.permission.READ_CALENDAR)

    override fun hasWritePermission(): Boolean = hasPermission(Manifest.permission.WRITE_CALENDAR)

    override fun calendars(): List<ArrangeCalendarInfo> {
        requireReadPermission()
        val sortOrder =
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        val calendars = mutableListOf<ArrangeCalendarInfo>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += ArrangeCalendarInfo(
                    id = cursor.long(CalendarContract.Calendars._ID),
                    name = calendarDisplayName(
                        cursor.text(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME).orEmpty(),
                    ),
                    accountName = cursor.text(CalendarContract.Calendars.ACCOUNT_NAME).orEmpty(),
                    accountType = cursor.text(CalendarContract.Calendars.ACCOUNT_TYPE).orEmpty(),
                    writable = accessLevelIsWritable(
                        cursor.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                    ),
                    primary = cursor.int(CalendarContract.Calendars.IS_PRIMARY) != 0,
                )
            }
        }
        return calendars
    }

    override fun list(
        startMillis: Long,
        endMillis: Long,
        query: String?,
        limit: Int,
    ): List<ArrangeCalendarEvent> {
        requireReadPermission()
        require(endMillis > startMillis) { "Range end must be after range start" }
        val safeLimit = limit.also {
            require(it in 1..ARRANGE_EVENT_LIMIT_MAX) { "limit must be between 1 and $ARRANGE_EVENT_LIMIT_MAX" }
        }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()
        var selection = "${CalendarContract.Instances.BEGIN} < ? AND ${CalendarContract.Instances.END} > ?"
        val selectionArgs = mutableListOf(endMillis.toString(), startMillis.toString())
        val filter = query?.trim().orEmpty()
        if (filter.isNotEmpty()) {
            selection += " AND ${CalendarContract.Instances.TITLE} LIKE ?"
            selectionArgs += "%$filter%"
        }
        val sortOrder = limitedSortOrder("${CalendarContract.Instances.BEGIN} ASC", safeLimit)
        val events = mutableListOf<ArrangeCalendarEvent>()
        resolver.query(
            uri,
            INSTANCE_PROJECTION,
            selection,
            selectionArgs.toTypedArray(),
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                events += instanceFromCursor(cursor)
            }
        }
        val recurringEventIds = recurringEventIds(events.map { it.eventId }.distinct())
        if (recurringEventIds.isEmpty()) return events
        return events.map { event -> event.copy(recurring = event.eventId in recurringEventIds) }
    }

    override fun get(id: Long): ArrangeCalendarEvent? {
        requireReadPermission()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        resolver.query(uri, EVENT_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return eventFromCursor(cursor)
        }
        return null
    }

    override fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent {
        requireWritePermission()
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "Event title must not be empty" }
        val reminders = draft.reminderMinutes?.let { validatedReminderMinutes(it) }.orEmpty()
        val calendarId = draft.calendarId ?: defaultWritableCalendarId()
        requireWritableCalendar(calendarId)
        val range = resolveEventRange(draft.startMillis, draft.endMillis, draft.allDay)
        return writer.create(draft.copy(title = title), calendarId, range, reminders)
    }

    override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? {
        requireWritePermission()
        val current = get(id) ?: return null
        val update = mergeEventPatch(current, patch)
        update.calendarId?.let { requireWritableCalendar(it) }
        return writer.update(id, update)
    }

    override fun delete(id: Long): Boolean {
        requireWritePermission()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return resolver.delete(uri, null, null) > 0
    }

    private inner class ResolverEventStore : ArrangeEventStore {
        override fun insertEvent(
            draft: ArrangeEventDraft,
            calendarId: Long,
            range: Pair<Long, Long>,
            hasAlarm: Boolean,
        ): Long {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, draft.title)
                put(CalendarContract.Events.DTSTART, range.first)
                put(CalendarContract.Events.DTEND, range.second)
                put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, eventTimezone(draft.allDay))
                if (draft.allDay) put(CalendarContract.Events.EVENT_END_TIMEZONE, UTC_TIMEZONE)
                put(CalendarContract.Events.HAS_ALARM, if (hasAlarm) 1 else 0)
                draft.location?.takeIf { it.isNotEmpty() }?.let {
                    put(CalendarContract.Events.EVENT_LOCATION, it)
                }
                draft.description?.takeIf { it.isNotEmpty() }?.let {
                    put(CalendarContract.Events.DESCRIPTION, it)
                }
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create calendar event: system calendar returned no event URI")
            return ContentUris.parseId(uri)
        }

        override fun updateEvent(id: Long, update: ArrangeEventUpdate) {
            val values = ContentValues()
            update.title?.let { values.put(CalendarContract.Events.TITLE, it.ifEmpty { null }) }
            update.location?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it.ifEmpty { null }) }
            update.description?.let { values.put(CalendarContract.Events.DESCRIPTION, it.ifEmpty { null }) }
            if (update.timesChanged) {
                values.put(CalendarContract.Events.DTSTART, update.startMillis)
                values.put(CalendarContract.Events.DTEND, update.endMillis)
                values.put(CalendarContract.Events.ALL_DAY, if (update.allDay) 1 else 0)
                values.put(CalendarContract.Events.EVENT_TIMEZONE, eventTimezone(update.allDay))
                if (update.allDay) values.put(CalendarContract.Events.EVENT_END_TIMEZONE, UTC_TIMEZONE)
            }
            update.calendarId?.let { values.put(CalendarContract.Events.CALENDAR_ID, it) }
            val reminders = update.reminderMinutes
            if (reminders != null) {
                values.put(CalendarContract.Events.HAS_ALARM, if (reminders.isEmpty()) 0 else 1)
            }
            if (values.size() > 0) {
                resolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    values,
                    null,
                    null,
                )
            }
        }

        override fun deleteEvent(id: Long) {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
        }

        override fun loadEvent(id: Long): ArrangeCalendarEvent? = get(id)

        override fun loadReminders(id: Long): List<ArrangeReminderSnapshot> {
            val snapshot = mutableListOf<ArrangeReminderSnapshot>()
            resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                REMINDER_PROJECTION,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    snapshot += ArrangeReminderSnapshot(
                        id = cursor.long(CalendarContract.Reminders._ID),
                        minutes = cursor.int(CalendarContract.Reminders.MINUTES),
                        method = cursor.int(CalendarContract.Reminders.METHOD),
                    )
                }
            }
            return snapshot
        }

        override fun replaceReminders(id: Long, minutes: List<Int>) {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(id.toString()),
            )
            minutes.forEach { minute ->
                val values = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, minute)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to write event reminders")
            }
        }

        override fun restoreReminders(id: Long, snapshot: List<ArrangeReminderSnapshot>) {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(id.toString()),
            )
            snapshot.sortedBy { it.id }.forEach { reminder ->
                val values = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, reminder.minutes)
                    put(CalendarContract.Reminders.METHOD, reminder.method)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            }
        }
    }

    fun defaultWritableCalendarId(): Long {
        val writable = calendars().filter { it.writable }
        return (writable.firstOrNull { it.primary } ?: writable.firstOrNull())?.id
            ?: throw ArrangeNoWritableCalendarException()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun requireReadPermission() {
        if (!hasReadPermission()) {
            throw ArrangeCalendarPermissionException("Missing READ_CALENDAR permission; grant calendar read access first")
        }
    }

    private fun requireWritePermission() {
        if (!hasWritePermission()) {
            throw ArrangeCalendarPermissionException("Missing WRITE_CALENDAR permission; grant calendar write access first")
        }
    }

    private fun requireWritableCalendar(calendarId: Long) {
        val calendar = calendars().firstOrNull { it.id == calendarId }
            ?: throw IllegalArgumentException("Calendar $calendarId not found")
        require(calendar.writable) { "Calendar is not writable: ${calendar.name}" }
    }

    private fun eventTimezone(allDay: Boolean): String =
        if (allDay) UTC_TIMEZONE else TimeZone.getDefault().id

    private fun instanceFromCursor(cursor: Cursor): ArrangeCalendarEvent {
        val allDay = cursor.int(CalendarContract.Instances.ALL_DAY) != 0
        val start = cursor.long(CalendarContract.Instances.BEGIN)
        val end = cursor.longOrNull(CalendarContract.Instances.END)
            ?: (start + if (allDay) ARRANGE_DAY_MILLIS else ARRANGE_HOUR_MILLIS)
        return arrangeEventForInstance(
            eventId = cursor.long(CalendarContract.Instances.EVENT_ID),
            calendarId = cursor.long(CalendarContract.Instances.CALENDAR_ID),
            title = cursor.text(CalendarContract.Instances.TITLE).orEmpty(),
            startMillis = start,
            endMillis = end,
            allDay = allDay,
            location = cursor.text(CalendarContract.Instances.EVENT_LOCATION),
            description = cursor.text(CalendarContract.Instances.DESCRIPTION),
            timezone = cursor.text(CalendarContract.Instances.EVENT_TIMEZONE),
        )
    }

    private fun eventFromCursor(cursor: Cursor): ArrangeCalendarEvent {
        val id = cursor.long(CalendarContract.Events._ID)
        val allDay = cursor.int(CalendarContract.Events.ALL_DAY) != 0
        val start = cursor.longOrNull(CalendarContract.Events.DTSTART)
            ?: throw IllegalStateException("Calendar event $id has no start time")
        val end = cursor.longOrNull(CalendarContract.Events.DTEND)
            ?: (start + if (allDay) ARRANGE_DAY_MILLIS else ARRANGE_HOUR_MILLIS)
        return ArrangeCalendarEvent(
            id = id,
            calendarId = cursor.long(CalendarContract.Events.CALENDAR_ID),
            title = cursor.text(CalendarContract.Events.TITLE).orEmpty(),
            startMillis = start,
            endMillis = end,
            allDay = allDay,
            location = cursor.text(CalendarContract.Events.EVENT_LOCATION),
            description = cursor.text(CalendarContract.Events.DESCRIPTION),
            timezone = cursor.text(CalendarContract.Events.EVENT_TIMEZONE),
            recurring = !cursor.text(CalendarContract.Events.RRULE).isNullOrBlank(),
            eventId = id,
        )
    }

    private fun recurringEventIds(eventIds: List<Long>): Set<Long> {
        if (eventIds.isEmpty()) return emptySet()
        val selection = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val args = eventIds.map { it.toString() }.toTypedArray()
        val recurring = mutableSetOf<Long>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.RRULE),
            selection,
            args,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.text(CalendarContract.Events.RRULE).isNullOrBlank()) {
                    recurring += cursor.long(CalendarContract.Events._ID)
                }
            }
        }
        return recurring
    }

    private companion object {
        val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )

        val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_TIMEZONE,
        )

        val EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
        )

        val REMINDER_PROJECTION = arrayOf(
            CalendarContract.Reminders._ID,
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD,
        )
    }
}

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}

private fun Cursor.text(column: String): String? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}
