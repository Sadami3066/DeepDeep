package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ScheduleDeliveryException(val error: ScheduleError) : Exception(error.name)

class ScheduleService(
    private val runtime: AgentRuntime,
    private val store: ScheduleStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val intentsFile: File? = null,
) {
    private val schedulesState = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = schedulesState.asStateFlow()

    private val deliveryMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            guard("recover pending schedules failed") { recoverPending() }
            guard("refresh schedules failed") { refresh() }
            guard("deliver due schedules failed") { deliverDue() }
            launch {
                store.changes.collect {
                    guard("refresh schedules failed") { refresh() }
                    wake.trySend(Unit)
                }
            }
            launch {
                runtime.events.collect { event ->
                    guard("handle schedule event failed") { handleRuntimeEvent(event) }
                    wake.trySend(Unit)
                }
            }
            launch { timerLoop() }
        }
    }

    private suspend fun guard(message: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logError(TAG, message, error)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun list(): List<Schedule> {
        val schedules = withContext(Dispatchers.IO) { store.list() }
        schedulesState.value = schedules
        return schedules
    }

    suspend fun recomputeTargets() {
        withContext(Dispatchers.IO) { store.recomputeTargets() }
        refresh()
    }

    suspend fun create(newSchedule: NewSchedule): Schedule {
        val created = withContext(Dispatchers.IO) { store.create(newSchedule) }
        refresh()
        return created
    }

    suspend fun create(
        name: String,
        prompt: String,
        trigger: ScheduleTrigger,
        enabled: Boolean = true,
        sessionId: String,
        sessionFile: String,
        cwd: String,
    ): Schedule = create(
        NewSchedule(
            name = name,
            prompt = prompt,
            trigger = trigger,
            enabled = enabled,
            sessionId = sessionId,
            sessionFile = sessionFile,
            cwd = cwd,
        ),
    )

    suspend fun update(id: String, patch: SchedulePatch): Schedule? {
        val updated = withContext(Dispatchers.IO) { store.update(id, patch) }
        refresh()
        return updated
    }

    suspend fun delete(id: String): Boolean {
        val deleted = withContext(Dispatchers.IO) { store.delete(id) }
        refresh()
        return deleted
    }

    suspend fun runNow(id: String): Result<Unit> = deliveryMutex.withLock {
        val schedule = withContext(Dispatchers.IO) { store.list() }.firstOrNull { it.id == id }
            ?: return@withLock Result.failure(IllegalArgumentException("schedule not found"))
        val intent = intentFor(schedule, clock.millis())
        addIntent(intent)
        val error = deliverSchedules(listOf(schedule))
        val now = clock.millis()
        if (error == null) {
            withContext(Dispatchers.IO) { store.markRunNow(id, now) }
            removeIntent(intent.dedupeKey)
            refresh()
            Result.success(Unit)
        } else {
            withContext(Dispatchers.IO) { store.markRunNow(id, now, error) }
            removeIntent(intent.dedupeKey)
            refresh()
            Result.failure(ScheduleDeliveryException(error))
        }
    }

    internal suspend fun deliverDue() = deliveryMutex.withLock {
        val now = clock.millis()
        val due = withContext(Dispatchers.IO) { store.list() }
            .filter { it.enabled && it.nextRunAt?.let { time -> time <= now } == true }
        if (due.isNotEmpty()) {
            due.groupBy { it.sessionId }.forEach { (_, group) ->
                val ordered = group.sortedWith(compareBy({ it.nextRunAt ?: 0L }, { it.createdAt }, { it.id }))
                deliverGroup(ordered, now)
            }
        }
        refresh()
    }

    internal suspend fun recoverPending() = deliveryMutex.withLock {
        val intents = loadIntents()
        if (intents.isEmpty()) {
            refresh()
            return@withLock
        }
        val schedules = withContext(Dispatchers.IO) { store.list() }.associateBy { it.id }
        val deliverable = mutableListOf<DeliveryIntent>()
        intents.forEach { intent ->
            val schedule = schedules[intent.scheduleId]
            val covered = schedule?.lastDeliveredAt?.let { it >= intent.createdAt } == true
            if (schedule == null || !schedule.enabled || covered) {
                removeIntent(intent.dedupeKey)
            } else {
                deliverable += intent
            }
        }
        deliverable.groupBy { it.sessionId }.forEach { (_, group) ->
            val groupSchedules = group.mapNotNull { schedules[it.scheduleId] }
            val error = deliverSchedules(groupSchedules)
            val at = clock.millis()
            group.forEach { intent ->
                val schedule = schedules[intent.scheduleId]
                if (schedule != null) {
                    if (error == null) {
                        withContext(Dispatchers.IO) { store.markDelivered(schedule.id, at) }
                    } else {
                        withContext(Dispatchers.IO) { store.markDeliveryFailed(schedule.id, error, at) }
                    }
                }
                removeIntent(intent.dedupeKey)
            }
        }
        refresh()
    }

    internal suspend fun handleRuntimeEvent(event: SessionRuntimeEvent) {
        when (val inner = event.event) {
            is RuntimeEvent.SessionRemoved ->
                withContext(Dispatchers.IO) { store.deleteBySession(event.sessionId) }

            is RuntimeEvent.SessionArchived ->
                if (inner.archived) withContext(Dispatchers.IO) { store.disableBySession(event.sessionId) }
            else -> Unit
        }
        refresh()
    }

    private suspend fun deliverGroup(group: List<Schedule>, now: Long) {
        val intents = group.map { intentFor(it, now) }
        intents.forEach { addIntent(it) }
        val error = deliverSchedules(group)
        val at = clock.millis()
        if (error == null) {
            withContext(Dispatchers.IO) { group.forEach { store.markDelivered(it.id, at) } }
        } else {
            withContext(Dispatchers.IO) { group.forEach { store.markDeliveryFailed(it.id, error, at) } }
        }
        intents.forEach { removeIntent(it.dedupeKey) }
    }

    private fun intentFor(schedule: Schedule, now: Long): DeliveryIntent {
        val runAt = schedule.nextRunAt
        return DeliveryIntent(
            dedupeKey = "${schedule.id}@${runAt ?: "run-now-$now"}",
            scheduleId = schedule.id,
            sessionId = schedule.sessionId,
            createdAt = now,
        )
    }

    private fun loadIntents(): List<DeliveryIntent> {
        val file = intentsFile ?: return emptyList()
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return rebuildIntents()
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return rebuildIntents()
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val entries = root["intents"] as? JsonArray
        if (version != INTENT_SCHEMA_VERSION || entries == null) return rebuildIntents()
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            runCatching { intentFromJson(item) }.getOrNull()
        }
    }

    private fun rebuildIntents(): List<DeliveryIntent> {
        persistIntents(emptyList())
        return emptyList()
    }

    private fun addIntent(intent: DeliveryIntent) {
        val current = loadIntents()
        if (current.any { it.dedupeKey == intent.dedupeKey }) return
        persistIntents(current + intent)
    }

    private fun removeIntent(dedupeKey: String) {
        val current = loadIntents()
        if (current.none { it.dedupeKey == dedupeKey }) return
        persistIntents(current.filterNot { it.dedupeKey == dedupeKey })
    }

    private fun persistIntents(intents: List<DeliveryIntent>) {
        val file = intentsFile ?: return
        val directory = file.parentFile ?: return
        if (!directory.exists() && !directory.mkdirs()) return
        val root = buildJsonObject {
            put("schemaVersion", INTENT_SCHEMA_VERSION)
            putJsonArray("intents") {
                intents.forEach { intent ->
                    add(buildJsonObject {
                        put("dedupeKey", intent.dedupeKey)
                        put("scheduleId", intent.scheduleId)
                        put("sessionId", intent.sessionId)
                        put("createdAt", intent.createdAt)
                    })
                }
            }
        }
        val temp = File(directory, "$FILE_NAME.tmp")
        runCatching {
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
    }

    private suspend fun deliverSchedules(group: List<Schedule>): ScheduleError? {
        val anchor = group.firstOrNull() ?: return null
        if (anchor.sessionFile.isBlank() || !File(anchor.sessionFile).isFile) {
            return ScheduleError.SESSION_MISSING
        }
        val opened = try {
            runtime.openSession(anchor.sessionFile, anchor.cwd)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ScheduleError.OPEN_FAILED
        }
        val (text, reminder) = reminderText(group)
        return try {
            runtime.deliverReminder(opened.sessionId, text, reminder)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScheduleError.DELIVERY_FAILED
        }
    }

    private suspend fun timerLoop() {
        while (currentCoroutineContext().isActive) {
            val delivered = try {
                val now = clock.millis()
                val current = store.list()
                val hasDue = current.any { it.enabled && it.nextRunAt?.let { time -> time <= now } == true }
                if (hasDue) {
                    deliverDue()
                    true
                } else {
                    val next = current.asSequence().filter { it.enabled }.mapNotNull { it.nextRunAt }.minOrNull()
                    val wait = next?.minus(now)?.coerceIn(1L, MAX_TIMER_WAIT_MS) ?: MAX_TIMER_WAIT_MS
                    withTimeoutOrNull(wait) { wake.receive() }
                    false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logError(TAG, "schedule timer iteration failed", error)
                withTimeoutOrNull(MAX_TIMER_WAIT_MS) { wake.receive() }
                false
            }
            if (delivered) continue
        }
    }

    private suspend fun refresh() {
        list()
    }

    private companion object {
        const val MAX_TIMER_WAIT_MS = 15 * 60 * 1000L
        const val INTENT_SCHEMA_VERSION = 1
        const val FILE_NAME = "delivery-intents.json"
        const val TAG = "ScheduleService"
    }
}

private data class DeliveryIntent(
    val dedupeKey: String,
    val scheduleId: String,
    val sessionId: String,
    val createdAt: Long,
)

private fun intentFromJson(json: JsonObject): DeliveryIntent = DeliveryIntent(
    dedupeKey = json.requiredIntentString("dedupeKey"),
    scheduleId = json.requiredIntentString("scheduleId"),
    sessionId = json.requiredIntentString("sessionId"),
    createdAt = json.requiredIntentLong("createdAt"),
)

private fun JsonObject.requiredIntentString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing intent field: $name")

private fun JsonObject.requiredIntentLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw IllegalArgumentException("invalid intent field: $name")

private fun reminderText(group: List<Schedule>): Pair<String, String> {
    val first = group.first()
    if (group.size == 1) {
        val text = "[定时提醒] ${first.name}\n这是你设置的定时提醒，现在到时间了。\n\n${first.prompt}"
        return text to first.name
    }
    val header = "[定时提醒] ${group.size} 个提醒\n这是你设置的定时提醒，现在到时间了。"
    val body = group.mapIndexed { index, schedule ->
        "\n\n${index + 1}. ${schedule.name}\n${schedule.prompt}"
    }.joinToString("")
    return header + body to "${group.size} 个提醒"
}
