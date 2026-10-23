package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val prompt: String,
    val trigger: ScheduleTrigger,
    val enabled: Boolean = true,
    val sessionId: String,
    val sessionFile: String,
    val cwd: String,
)

data class SchedulePatch(
    val name: String? = null,
    val prompt: String? = null,
    val trigger: ScheduleTrigger? = null,
    val enabled: Boolean? = null,
    val sessionId: String? = null,
    val sessionFile: String? = null,
    val cwd: String? = null,
)

class ScheduleStore(
    private val directory: File,
    internal val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()
    private val file = File(directory, FILE_NAME)
    private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = changesFlow.asSharedFlow()

    suspend fun list(): List<Schedule> = mutex.withLock { load() }

    suspend fun create(newSchedule: NewSchedule): Schedule = mutex.withLock {
        val current = load()
        val now = clock.millis()
        val nowZoned = ZonedDateTime.now(clock)
        newSchedule.trigger.validationError(nowZoned)?.let { error ->
            if (newSchedule.enabled || newSchedule.trigger !is ScheduleTrigger.Once) {
                throw IllegalArgumentException(error)
            }
        }
        val nextRunAt = if (newSchedule.enabled) computeNextRunAt(newSchedule.trigger) else null
        val created = Schedule(
            id = UUID.randomUUID().toString(),
            name = newSchedule.name,
            prompt = newSchedule.prompt,
            trigger = newSchedule.trigger,
            enabled = newSchedule.enabled,
            sessionId = newSchedule.sessionId,
            sessionFile = newSchedule.sessionFile,
            cwd = newSchedule.cwd,
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
        val bindingChanged = patch.sessionId != null || patch.sessionFile != null || patch.cwd != null
        val trigger = patch.trigger ?: existing.trigger
        val enabled = patch.enabled ?: existing.enabled
        val recompute = triggerChanged || enabledChanged || bindingChanged
        val nextRunAt = if (recompute) {
            if (enabled) computeNextRunAt(trigger) else null
        } else {
            existing.nextRunAt
        }
        if (recompute) {
            trigger.validationError(ZonedDateTime.now(clock))?.let { error ->
                if (enabled || trigger !is ScheduleTrigger.Once) {
                    throw IllegalArgumentException(error)
                }
            }
        }
        val updated = existing.copy(
            name = patch.name ?: existing.name,
            prompt = patch.prompt ?: existing.prompt,
            trigger = trigger,
            enabled = enabled,
            sessionId = patch.sessionId ?: existing.sessionId,
            sessionFile = patch.sessionFile ?: existing.sessionFile,
            cwd = patch.cwd ?: existing.cwd,
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

    suspend fun markDelivered(id: String, at: Long): Schedule? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val once = existing.trigger is ScheduleTrigger.Once
        val updated = existing.copy(
            enabled = if (once) false else existing.enabled,
            nextRunAt = if (once) null else nextRunAtAfter(existing.trigger, at),
            lastDeliveredAt = at,
            lastError = null,
            updatedAt = at,
        )
        persist(current.toMutableList().also { it[index] = updated })
        updated
    }

    suspend fun markDeliveryFailed(id: String, error: ScheduleError, at: Long): Schedule? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val once = existing.trigger is ScheduleTrigger.Once
        val updated = existing.copy(
            enabled = if (once) false else existing.enabled,
            nextRunAt = if (once) null else nextRunAtAfter(existing.trigger, at),
            lastError = error,
            updatedAt = at,
        )
        persist(current.toMutableList().also { it[index] = updated })
        updated
    }

    suspend fun markRunNow(id: String, at: Long, error: ScheduleError? = null): Schedule? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val updated = existing.copy(
            lastDeliveredAt = if (error == null) at else existing.lastDeliveredAt,
            lastError = error,
            updatedAt = at,
        )
        persist(current.toMutableList().also { it[index] = updated })
        updated
    }

    suspend fun deleteBySession(sessionId: String): Boolean = mutex.withLock {
        val current = load()
        val remaining = current.filterNot { it.sessionId == sessionId }
        if (remaining.size == current.size) return@withLock false
        persist(remaining)
        true
    }

    suspend fun disableBySession(sessionId: String): Boolean = mutex.withLock {
        val current = load()
        val now = clock.millis()
        val updated = current.map { schedule ->
            if (schedule.sessionId == sessionId && schedule.enabled) {
                schedule.copy(enabled = false, nextRunAt = null, updatedAt = now)
            } else {
                schedule
            }
        }
        if (updated == current) return@withLock false
        persist(updated)
        true
    }

    suspend fun recomputeTargets(): Unit = mutex.withLock {
        val current = load()
        val nowMillis = clock.millis()
        val now = ZonedDateTime.now(clock)
        val updated = current.map { schedule ->
            if (!schedule.enabled) {
                if (schedule.nextRunAt == null) schedule else schedule.copy(nextRunAt = null, updatedAt = nowMillis)
            } else {
                val next = schedule.trigger.nextRun(now)?.toInstant()?.toEpochMilli()
                val missedOnce = next == null && schedule.trigger is ScheduleTrigger.Once
                if (next == schedule.nextRunAt && !missedOnce) {
                    schedule
                } else {
                    schedule.copy(
                        enabled = if (missedOnce) false else schedule.enabled,
                        nextRunAt = next,
                        updatedAt = nowMillis,
                    )
                }
            }
        }
        if (updated != current) persist(updated)
    }

    private fun computeNextRunAt(trigger: ScheduleTrigger, now: ZonedDateTime = ZonedDateTime.now(clock)): Long? =
        trigger.nextRun(now)?.toInstant()?.toEpochMilli()

    private fun nextRunAtAfter(trigger: ScheduleTrigger, at: Long): Long? =
        trigger.nextRun(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), clock.zone))
            ?.toInstant()
            ?.toEpochMilli()

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
        if (version == LEGACY_SCHEMA_VERSION) {
            persist(emptyList())
            return emptyList()
        }
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
        changesFlow.tryEmit(Unit)
    }

    companion object {
        const val SCHEMA_VERSION = 2
        private const val LEGACY_SCHEMA_VERSION = 1
        private const val FILE_NAME = "schedules.json"
    }
}
