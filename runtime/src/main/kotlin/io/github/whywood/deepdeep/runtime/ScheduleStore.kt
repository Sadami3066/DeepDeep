package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class NewSchedule(
    val name: String,
    val workspaceId: String,
    val prompt: String,
    val trigger: ScheduleTrigger,
    val enabled: Boolean = true,
    val notify: Boolean = true,
)

data class SchedulePatch(
    val name: String? = null,
    val workspaceId: String? = null,
    val prompt: String? = null,
    val trigger: ScheduleTrigger? = null,
    val enabled: Boolean? = null,
    val notify: Boolean? = null,
)

class ScheduleStore(
    private val directory: File,
    internal val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()
    private val file = File(directory, FILE_NAME)

    suspend fun list(): List<Schedule> = mutex.withLock { load() }

    suspend fun create(schedule: NewSchedule): Schedule = mutex.withLock {
        val current = load()
        val nextRunAt = computeNextRunAt(schedule.trigger)
        if (schedule.trigger is ScheduleTrigger.Once && nextRunAt == null) {
            throw IllegalArgumentException("once 的时间必须在未来")
        }
        val now = clock.millis()
        val created = Schedule(
            id = UUID.randomUUID().toString(),
            name = schedule.name,
            workspaceId = schedule.workspaceId,
            prompt = schedule.prompt,
            trigger = schedule.trigger,
            enabled = schedule.enabled,
            notify = schedule.notify,
            nextRunAt = nextRunAt,
            createdAt = now,
            updatedAt = now,
        )
        persist(current + created)
        created
    }

    suspend fun update(id: String, patch: SchedulePatch): Schedule? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val triggerChanged = patch.trigger != null
        val enabledChanged = patch.enabled != null
        val trigger = patch.trigger ?: existing.trigger
        val nextRunAt = if (triggerChanged || enabledChanged) {
            computeNextRunAt(trigger)
        } else {
            existing.nextRunAt
        }
        if (triggerChanged && trigger is ScheduleTrigger.Once && nextRunAt == null) {
            throw IllegalArgumentException("once 的时间必须在未来")
        }
        val updated = existing.copy(
            name = patch.name ?: existing.name,
            workspaceId = patch.workspaceId ?: existing.workspaceId,
            prompt = patch.prompt ?: existing.prompt,
            trigger = trigger,
            enabled = patch.enabled ?: existing.enabled,
            notify = patch.notify ?: existing.notify,
            nextRunAt = nextRunAt,
            updatedAt = clock.millis(),
        )
        persist(current.toMutableList().also { it[index] = updated })
        updated
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val current = load()
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) return@withLock false
        persist(remaining)
        true
    }

    private fun computeNextRunAt(trigger: ScheduleTrigger): Long? =
        trigger.nextRun(ZonedDateTime.now(clock))?.toInstant()?.toEpochMilli()

    private fun load(): List<Schedule> {
        if (!file.exists()) return emptyList()
        val text = try {
            file.readText()
        } catch (error: Exception) {
            throw CorruptScheduleDataException("无法读取定时任务数据", error)
        }
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw CorruptScheduleDataException("定时任务数据不是有效 JSON 对象")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: throw CorruptScheduleDataException("定时任务数据缺少 schemaVersion")
        if (version != SCHEMA_VERSION) {
            throw CorruptScheduleDataException("不支持的 schemaVersion：$version")
        }
        val entries = root["schedules"] as? JsonArray
            ?: throw CorruptScheduleDataException("定时任务数据缺少 schedules")
        return entries.map { element ->
            scheduleFromJsonObject(element as? JsonObject ?: throw CorruptScheduleDataException("定时任务条目不是对象"))
        }
    }

    private fun persist(schedules: List<Schedule>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建定时任务目录：${directory.absolutePath}")
        }
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            putJsonArray("schedules") { schedules.forEach { add(it.toJsonObject()) } }
        }
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val FILE_NAME = "schedules.json"
    }
}
