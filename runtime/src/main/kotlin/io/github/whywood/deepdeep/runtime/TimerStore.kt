package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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

data class NewTimer(
    val name: String,
    val prompt: String,
    val trigger: TimerTrigger,
    val enabled: Boolean = true,
    val sessionId: String,
    val sessionFile: String,
    val cwd: String,
)

data class TimerPatch(
    val name: String? = null,
    val prompt: String? = null,
    val trigger: TimerTrigger? = null,
    val enabled: Boolean? = null,
    val sessionId: String? = null,
    val sessionFile: String? = null,
    val cwd: String? = null,
)

class TimerStore(
    private val directory: File,
    internal val clock: Clock = Clock.systemDefaultZone(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    @Volatile
    private var writesBlocked = false

    private val mutex = Mutex()
    private val file = File(directory, FILE_NAME)
    private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = changesFlow.asSharedFlow()

    internal val loadCount = AtomicInteger()

    suspend fun list(): List<Timer> = mutex.withLock { load() }

    suspend fun create(newTimer: NewTimer): Timer = mutex.withLock {
        val current = load()
        val now = clock.millis()
        val nowZonedTime = nowZoned()
        newTimer.trigger.validationError(nowZonedTime)?.let { error ->
            if (newTimer.enabled || newTimer.trigger !is TimerTrigger.Once) {
                throw IllegalArgumentException(error)
            }
        }
        val nextRunAt = if (newTimer.enabled) computeNextRunAt(newTimer.trigger) else null
        val created = Timer(
            id = UUID.randomUUID().toString(),
            name = newTimer.name,
            prompt = newTimer.prompt,
            trigger = newTimer.trigger,
            enabled = newTimer.enabled,
            sessionId = newTimer.sessionId,
            sessionFile = newTimer.sessionFile,
            cwd = newTimer.cwd,
            nextRunAt = nextRunAt,
            createdAt = now,
            updatedAt = now,
        )
        persist(current + created)
        created
    }

    suspend fun update(id: String, patch: TimerPatch): Timer? = mutex.withLock {
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
            trigger.validationError(nowZoned())?.let { error ->
                if (enabled || trigger !is TimerTrigger.Once) {
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

    suspend fun markDelivered(id: String, at: Long): Timer? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val once = existing.trigger is TimerTrigger.Once
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

    suspend fun markDeliveryFailed(id: String, error: TimerError, at: Long): Timer? = mutex.withLock {
        val current = load()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val existing = current[index]
        val once = existing.trigger is TimerTrigger.Once
        val updated = existing.copy(
            enabled = if (once) false else existing.enabled,
            nextRunAt = if (once) null else nextRunAtAfter(existing.trigger, at),
            lastError = error,
            updatedAt = at,
        )
        persist(current.toMutableList().also { it[index] = updated })
        updated
    }

    suspend fun markRunNow(id: String, at: Long, error: TimerError? = null): Timer? = mutex.withLock {
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
        val updated = current.map { timer ->
            if (timer.sessionId == sessionId && timer.enabled) {
                timer.copy(enabled = false, nextRunAt = null, updatedAt = now)
            } else {
                timer
            }
        }
        if (updated == current) return@withLock false
        persist(updated)
        true
    }

    suspend fun recomputeTargets(): Unit = mutex.withLock {
        val current = load()
        val nowMillis = clock.millis()
        val zone = zoneProvider()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val updated = current.map { timer ->
            if (!timer.enabled) {
                if (timer.nextRunAt == null) timer else timer.copy(nextRunAt = null, updatedAt = nowMillis)
            } else {
                val next = timer.trigger.nextRun(now)?.toInstant()?.toEpochMilli()
                val missedOnce = next == null && timer.trigger is TimerTrigger.Once
                if (missedOnce) {
                    if (timer.lastDeliveredAt != null) {
                        timer
                    } else {
                        val original = onceTriggerAt(timer.trigger, zone)
                        if (timer.nextRunAt == original) {
                            timer
                        } else {
                            timer.copy(nextRunAt = original, updatedAt = nowMillis)
                        }
                    }
                } else if (next == timer.nextRunAt) {
                    timer
                } else {
                    timer.copy(nextRunAt = next, updatedAt = nowMillis)
                }
            }
        }
        if (updated != current) persist(updated)
    }

    private fun nowZoned(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), zoneProvider())

    private fun onceTriggerAt(trigger: TimerTrigger.Once, zone: ZoneId): Long =
        ZonedDateTime.of(trigger.date, trigger.time, zone).toInstant().toEpochMilli()

    private fun computeNextRunAt(trigger: TimerTrigger, now: ZonedDateTime = nowZoned()): Long? =
        trigger.nextRun(now)?.toInstant()?.toEpochMilli()

    private fun nextRunAtAfter(trigger: TimerTrigger, at: Long): Long? =
        trigger.nextRun(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zoneProvider()))
            ?.toInstant()
            ?.toEpochMilli()

    private fun load(): List<Timer> {
        loadCount.incrementAndGet()
        if (!file.exists()) return emptyList()
        val text: String = runCatching { file.readText() }.getOrElse { error ->
            return discardCorrupt("定时任务数据读取失败", error)
        }
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return discardCorrupt("定时任务数据不是有效 JSON 对象")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: return discardCorrupt("定时任务数据缺少 schemaVersion")
        if (version == LEGACY_SCHEMA_VERSION) {
            if (backup(LEGACY_BACKUP_FILE_NAME)) runCatching { persist(emptyList()) } else blockWrites()
            return emptyList()
        }
        if (version > SCHEMA_VERSION) {
            logWarn(TAG, "定时任务数据来自更高版本 schemaVersion=$version，只读并暂停写入")
            writesBlocked = true
            return emptyList()
        }
        if (version != SCHEMA_VERSION && version != PREVIOUS_SCHEMA_VERSION) {
            return discardCorrupt("不支持的 schemaVersion：$version")
        }
        val legacyFormat = version == PREVIOUS_SCHEMA_VERSION
        val entriesKey = if (legacyFormat) LEGACY_ENTRIES_KEY else ENTRIES_KEY
        val entries = root[entriesKey] as? JsonArray
            ?: return discardCorrupt("定时任务数据缺少 $entriesKey")
        val timers = mutableListOf<Timer>()
        var corruptEntries = 0
        entries.forEach { element ->
            runCatching {
                timerFromJsonObject(element as? JsonObject ?: throw CorruptTimerDataException("定时任务条目不是对象"))
            }.onSuccess { timers += it }.onFailure { corruptEntries += 1 }
        }
        if (corruptEntries > 0) {
            logWarn(TAG, "定时任务条目损坏 $corruptEntries 条，已跳过")
            if (backup(CORRUPT_BACKUP_FILE_NAME)) runCatching { persist(timers) } else blockWrites()
        } else if (legacyFormat) {
            runCatching { persist(timers) }
        }
        return timers
    }

    private fun discardCorrupt(message: String, error: Throwable? = null): List<Timer> {
        logWarn(TAG, message, error)
        if (backup(CORRUPT_BACKUP_FILE_NAME)) {
            runCatching { persist(emptyList()) }.onFailure { blockWrites() }
        } else {
            blockWrites()
        }
        return emptyList()
    }

    private fun blockWrites() {
        writesBlocked = true
        logError(TAG, "定时任务数据备份失败，已暂停写入以保留原文件")
    }

    private fun backup(name: String): Boolean {
        val current = runCatching { file.readBytes() }.getOrNull() ?: return false
        val base = File(directory, name)
        if (base.isFile && runCatching { base.readBytes().contentEquals(current) }.getOrDefault(false)) {
            return true
        }
        val target = if (!base.exists()) base else uniqueBackup(base)
        val temp = File(directory, "${target.name}.tmp")
        val copied = runCatching {
            temp.writeBytes(current)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = false)
                temp.delete()
            }
        }.isSuccess
        if (copied) return true
        runCatching { temp.delete() }
        return runCatching { file.renameTo(target) }.getOrDefault(false)
    }

    private fun uniqueBackup(base: File): File {
        var index = 1
        while (true) {
            val candidate = File(base.parentFile, "${base.name}-$index")
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private fun persist(timers: List<Timer>) {
        if (writesBlocked) {
            throw IllegalStateException("定时任务数据备份失败，已暂停写入")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建定时任务目录：${directory.absolutePath}")
        }
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            putJsonArray("timers") { timers.forEach { add(it.toJsonObject()) } }
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
        const val SCHEMA_VERSION = 3
        private const val PREVIOUS_SCHEMA_VERSION = 2
        private const val LEGACY_SCHEMA_VERSION = 1
        private const val ENTRIES_KEY = "timers"
        private const val LEGACY_ENTRIES_KEY = "schedules"
        private const val LEGACY_BACKUP_FILE_NAME = "timers.v1.bak"
        private const val CORRUPT_BACKUP_FILE_NAME = "timers.json.corrupt"
        private const val FILE_NAME = "timers.json"
        private const val INTENTS_FILE_NAME = "delivery-intents.json"
        private const val DIRECTORY_NAME = "timers"
        private const val LEGACY_DIRECTORY_NAME = "schedules"
        private const val TAG = "TimerStore"

        fun prepareDataDirectory(baseDirectory: File): File {
            val target = File(baseDirectory, DIRECTORY_NAME)
            val legacy = File(baseDirectory, LEGACY_DIRECTORY_NAME)
            if (legacy.isDirectory && (!target.exists() || target.listFiles().orEmpty().isEmpty())) {
                if (target.mkdirs() || target.isDirectory) {
                    legacy.listFiles().orEmpty().forEach { source ->
                        if (!source.isFile) return@forEach
                        val name = migrateFileName(source.name) ?: return@forEach
                        val destination = File(target, name)
                        if (destination.exists()) return@forEach
                        runCatching { source.copyTo(destination, overwrite = false) }
                            .onFailure { logWarn(TAG, "定时任务数据迁移失败：${source.name}", it) }
                    }
                } else {
                    logWarn(TAG, "无法创建定时任务目录，旧数据暂不迁移")
                }
            }
            return target
        }

        private fun migrateFileName(name: String): String? = when {
            name == INTENTS_FILE_NAME -> name
            name.startsWith("$LEGACY_ENTRIES_KEY.") -> ENTRIES_KEY + name.removePrefix(LEGACY_ENTRIES_KEY)
            else -> null
        }
    }
}
