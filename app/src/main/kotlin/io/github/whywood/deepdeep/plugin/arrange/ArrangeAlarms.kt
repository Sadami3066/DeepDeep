package io.github.whywood.deepdeep.plugin.arrange

import android.app.AlarmManager
import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

data class ArrangeAlarm(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val days: List<Int>,
    val enabled: Boolean,
    val timer: Boolean,
    val triggerAt: Long,
    val createdAt: Long,
)

data class ArrangeAlarmDraft(
    val hour: Int,
    val minute: Int,
    val label: String? = null,
    val days: List<Int> = emptyList(),
    val enabled: Boolean = true,
)

data class ArrangeSystemAlarm(
    val triggerAt: Long,
    val label: String,
)

class ArrangeExactAlarmException(message: String) : IllegalStateException(message)

class ArrangeAlarmNotFoundException(message: String) : NoSuchElementException(message)

interface ArrangeAlarmEngine {
    fun list(): List<ArrangeAlarm>

    fun createAlarm(draft: ArrangeAlarmDraft): ArrangeAlarm

    fun updateAlarm(id: String, draft: ArrangeAlarmDraft): ArrangeAlarm?

    fun deleteAlarm(id: String): Boolean

    fun createTimer(durationSeconds: Int, label: String?): ArrangeAlarm

    fun nextSystemAlarm(): ArrangeSystemAlarm?

    fun canScheduleExactAlarms(): Boolean

    fun rescheduleAll()
}

internal const val ARRANGE_ALARMS_DIRECTORY = "arrange"
internal const val ARRANGE_ALARMS_FILE = "alarms.json"

internal fun nextAlarmTrigger(
    hour: Int,
    minute: Int,
    days: List<Int>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val repeatDays = days.filter { it in 1..7 }.toSet()
    val time = LocalTime.of(hour, minute)
    var date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    repeat(8) {
        if (repeatDays.isEmpty() || date.dayOfWeek.value in repeatDays) {
            val trigger = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
            if (trigger > nowMillis) return trigger
        }
        date = date.plusDays(1)
    }
    throw IllegalStateException("无法计算下一次触发时间")
}

internal fun nextTimerTrigger(nowMillis: Long, durationSeconds: Int): Long =
    nowMillis + durationSeconds.coerceAtLeast(0) * 1_000L

internal fun firedArrangeAlarm(
    alarm: ArrangeAlarm,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): ArrangeAlarm = if (alarm.timer || alarm.days.isEmpty()) {
    alarm.copy(enabled = false)
} else {
    alarm.copy(triggerAt = nextAlarmTrigger(alarm.hour, alarm.minute, alarm.days, nowMillis, zone))
}

internal fun encodeArrangeAlarms(alarms: List<ArrangeAlarm>): String = JsonObject(
    mapOf(
        "version" to JsonPrimitive(1),
        "alarms" to JsonArray(alarms.map(::arrangeAlarmJson)),
    ),
).toString()

internal fun decodeArrangeAlarms(raw: String): List<ArrangeAlarm>? = runCatching {
    val root = Json.parseToJsonElement(raw).jsonObject
    val alarms = root["alarms"]?.jsonArray ?: return@runCatching emptyList<ArrangeAlarm>()
    alarms.map { element ->
        val item = element.jsonObject
        ArrangeAlarm(
            id = item.getValue("id").jsonPrimitive.content,
            label = item["label"]?.jsonPrimitive?.content.orEmpty(),
            hour = item.getValue("hour").jsonPrimitive.int,
            minute = item.getValue("minute").jsonPrimitive.int,
            days = item["days"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty(),
            enabled = item["enabled"]?.jsonPrimitive?.boolean ?: true,
            timer = item["timer"]?.jsonPrimitive?.boolean ?: false,
            triggerAt = item["triggerAt"]?.jsonPrimitive?.long ?: 0L,
            createdAt = item["createdAt"]?.jsonPrimitive?.long ?: 0L,
        )
    }
}.getOrNull()

private fun arrangeAlarmJson(alarm: ArrangeAlarm): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(alarm.id),
        "label" to JsonPrimitive(alarm.label),
        "hour" to JsonPrimitive(alarm.hour),
        "minute" to JsonPrimitive(alarm.minute),
        "days" to JsonArray(alarm.days.map { JsonPrimitive(it) }),
        "enabled" to JsonPrimitive(alarm.enabled),
        "timer" to JsonPrimitive(alarm.timer),
        "triggerAt" to JsonPrimitive(alarm.triggerAt),
        "createdAt" to JsonPrimitive(alarm.createdAt),
    ),
)

internal class ArrangeAlarmStore(private val directory: File) {
    private val file = File(directory, ARRANGE_ALARMS_FILE)

    fun list(): List<ArrangeAlarm> = synchronized(LOCK) { read() }

    fun find(id: String): ArrangeAlarm? = synchronized(LOCK) {
        read().firstOrNull { it.id == id }
    }

    fun nextOwnAlarm(): ArrangeSystemAlarm? = synchronized(LOCK) {
        read()
            .asSequence()
            .filter { it.enabled }
            .minByOrNull { it.triggerAt }
            ?.let { ArrangeSystemAlarm(triggerAt = it.triggerAt, label = it.label) }
    }

    fun add(alarm: ArrangeAlarm) {
        synchronized(LOCK) {
            val alarms = read().toMutableList()
            alarms += alarm
            write(alarms)
        }
    }

    fun update(alarm: ArrangeAlarm): ArrangeAlarm? = synchronized(LOCK) {
        val alarms = read().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarm
            write(alarms)
            alarm
        } else {
            null
        }
    }

    fun remove(id: String): Boolean = synchronized(LOCK) {
        val alarms = read().toMutableList()
        val removed = alarms.removeAll { it.id == id }
        if (removed) write(alarms)
        removed
    }

    fun fire(
        id: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ArrangeAlarm? = synchronized(LOCK) {
        val alarms = read().toMutableList()
        val index = alarms.indexOfFirst { it.id == id }
        if (index < 0) return null
        val fired = firedArrangeAlarm(alarms[index], nowMillis, zone)
        alarms[index] = fired
        write(alarms)
        fired
    }

    private fun read(): List<ArrangeAlarm> {
        if (!file.isFile) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return decodeArrangeAlarms(raw) ?: emptyList()
    }

    private fun write(alarms: List<ArrangeAlarm>) {
        directory.mkdirs()
        val payload = encodeArrangeAlarms(alarms)
        val temp = File(directory, "$ARRANGE_ALARMS_FILE.tmp")
        temp.writeText(payload)
        if (!temp.renameTo(file)) {
            file.writeText(payload)
            temp.delete()
        }
    }

    private companion object {
        val LOCK = Any()
    }
}

internal fun arrangeAlarmStore(context: Context): ArrangeAlarmStore =
    ArrangeAlarmStore(File(context.filesDir, ARRANGE_ALARMS_DIRECTORY))

class AndroidArrangeAlarmEngine(context: Context) : ArrangeAlarmEngine {
    private val appContext = context.applicationContext
    private val store = arrangeAlarmStore(appContext)
    private val scheduler = ArrangeAlarmScheduler(appContext, store)

    init {
        runCatching { scheduler.rescheduleAll() }
    }

    override fun list(): List<ArrangeAlarm> = store.list()

    override fun createAlarm(draft: ArrangeAlarmDraft): ArrangeAlarm {
        val time = clockTime(draft.hour, draft.minute)
        val days = normalizeIsoDays(draft.days)
        val label = boundedOptionalText(draft.label, MAX_LABEL_LENGTH, "label").orEmpty()
        val now = System.currentTimeMillis()
        val alarm = ArrangeAlarm(
            id = UUID.randomUUID().toString(),
            label = label,
            hour = time.hour,
            minute = time.minute,
            days = days,
            enabled = draft.enabled,
            timer = false,
            triggerAt = nextAlarmTrigger(time.hour, time.minute, days, now),
            createdAt = now,
        )
        if (alarm.enabled) scheduler.schedule(alarm)
        store.add(alarm)
        return alarm
    }

    override fun updateAlarm(id: String, draft: ArrangeAlarmDraft): ArrangeAlarm? {
        val existing = store.find(id) ?: return null
        val label = boundedOptionalText(draft.label, MAX_LABEL_LENGTH, "label").orEmpty()
        val updated = if (existing.timer) {
            existing.copy(label = label, enabled = existing.enabled && draft.enabled)
        } else {
            val time = clockTime(draft.hour, draft.minute)
            val days = normalizeIsoDays(draft.days)
            existing.copy(
                label = label,
                hour = time.hour,
                minute = time.minute,
                days = days,
                enabled = draft.enabled,
                triggerAt = nextAlarmTrigger(time.hour, time.minute, days, System.currentTimeMillis()),
            )
        }
        if (updated.enabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(id)
        }
        store.update(updated)
        return updated
    }

    override fun deleteAlarm(id: String): Boolean {
        scheduler.cancel(id)
        return store.remove(id)
    }

    override fun createTimer(durationSeconds: Int, label: String?): ArrangeAlarm {
        if (durationSeconds !in MIN_TIMER_SECONDS..MAX_TIMER_SECONDS) {
            throw IllegalArgumentException("durationSeconds 需要在 $MIN_TIMER_SECONDS..$MAX_TIMER_SECONDS 之间")
        }
        val text = boundedOptionalText(label, MAX_LABEL_LENGTH, "label").orEmpty()
        val now = System.currentTimeMillis()
        val triggerAt = nextTimerTrigger(now, durationSeconds)
        val local = Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault())
        val alarm = ArrangeAlarm(
            id = UUID.randomUUID().toString(),
            label = text,
            hour = local.hour,
            minute = local.minute,
            days = emptyList(),
            enabled = true,
            timer = true,
            triggerAt = triggerAt,
            createdAt = now,
        )
        scheduler.schedule(alarm)
        store.add(alarm)
        return alarm
    }

    override fun nextSystemAlarm(): ArrangeSystemAlarm? {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return null
        val next = manager.nextAlarmClock ?: return null
        return ArrangeSystemAlarm(triggerAt = next.triggerTime, label = "")
    }

    override fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    override fun rescheduleAll() = scheduler.rescheduleAll()
}
