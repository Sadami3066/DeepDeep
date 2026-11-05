package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

class TimerDeliveryException(val error: TimerError) : Exception(error.name)

class TimerService(
    private val runtime: AgentRuntime,
    private val store: TimerStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val intentsFile: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val timersState = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = timersState.asStateFlow()

    private val deliveryMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            guard("recover pending timers failed") { recoverPending() }
            guard("refresh timers failed") { refresh() }
            guard("deliver due timers failed") { deliverDue() }
            launch {
                store.changes.collect {
                    guard("refresh timers failed") { refresh() }
                    wake.trySend(Unit)
                }
            }
            launch {
                runtime.events.collect { event ->
                    guard("handle timer event failed") { handleRuntimeEvent(event) }
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

    suspend fun list(): List<Timer> {
        val timers = readTimers()
        timersState.value = timers
        return timers
    }

    suspend fun recomputeTargets() {
        withContext(ioDispatcher) { store.recomputeTargets() }
        refresh()
    }

    suspend fun create(newTimer: NewTimer): Timer {
        val created = withContext(ioDispatcher) { store.create(newTimer) }
        refresh()
        return created
    }

    suspend fun create(
        name: String,
        prompt: String,
        trigger: TimerTrigger,
        enabled: Boolean = true,
        sessionId: String,
        sessionFile: String,
        cwd: String,
    ): Timer = create(
        NewTimer(
            name = name,
            prompt = prompt,
            trigger = trigger,
            enabled = enabled,
            sessionId = sessionId,
            sessionFile = sessionFile,
            cwd = cwd,
        ),
    )

    suspend fun update(id: String, patch: TimerPatch): Timer? {
        val updated = withContext(ioDispatcher) { store.update(id, patch) }
        refresh()
        return updated
    }

    suspend fun delete(id: String): Boolean {
        val deleted = withContext(ioDispatcher) { store.delete(id) }
        refresh()
        return deleted
    }

    suspend fun runNow(id: String): Result<Unit> = deliveryMutex.withLock {
        val timer = readTimers().firstOrNull { it.id == id }
            ?: return@withLock Result.failure(IllegalArgumentException("timer not found"))
        val intent = intentFor(timer, clock.millis())
        addIntent(intent)
        val error = deliverTimers(listOf(timer))
        val now = clock.millis()
        if (error == null) {
            withContext(ioDispatcher) { store.markRunNow(id, now) }
            removeIntent(intent.dedupeKey)
            refresh()
            Result.success(Unit)
        } else {
            withContext(ioDispatcher) { store.markRunNow(id, now, error) }
            removeIntent(intent.dedupeKey)
            refresh()
            Result.failure(TimerDeliveryException(error))
        }
    }

    internal suspend fun deliverDue() = deliveryMutex.withLock {
        val now = clock.millis()
        val due = readTimers()
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
        val timers = readTimers().associateBy { it.id }
        val deliverable = mutableListOf<DeliveryIntent>()
        intents.forEach { intent ->
            val timer = timers[intent.timerId]
            val covered = timer?.lastDeliveredAt?.let { it >= intent.createdAt } == true
            if (timer == null || !timer.enabled || covered) {
                removeIntent(intent.dedupeKey)
            } else {
                deliverable += intent
            }
        }
        deliverable.groupBy { it.sessionId }.forEach { (_, group) ->
            val groupTimers = group.mapNotNull { timers[it.timerId] }
            val error = deliverTimers(groupTimers)
            val at = clock.millis()
            group.forEach { intent ->
                val timer = timers[intent.timerId]
                if (timer != null) {
                    if (error == null) {
                        withContext(ioDispatcher) { store.markDelivered(timer.id, at) }
                    } else {
                        withContext(ioDispatcher) { store.markDeliveryFailed(timer.id, error, at) }
                    }
                }
                removeIntent(intent.dedupeKey)
            }
        }
        refresh()
    }

    internal suspend fun handleRuntimeEvent(event: SessionRuntimeEvent) {
        when (val inner = event.event) {
            is RuntimeEvent.SessionRemoved -> try {
                withContext(ioDispatcher) { store.deleteBySession(event.sessionId) }
            } finally {
                wake.trySend(Unit)
            }

            is RuntimeEvent.SessionArchived -> if (inner.archived) {
                try {
                    withContext(ioDispatcher) { store.disableBySession(event.sessionId) }
                } finally {
                    wake.trySend(Unit)
                }
            }

            else -> Unit
        }
    }

    private suspend fun deliverGroup(group: List<Timer>, now: Long) {
        val intents = group.map { intentFor(it, now) }
        intents.forEach { addIntent(it) }
        val error = deliverTimers(group)
        val at = clock.millis()
        if (error == null) {
            withContext(ioDispatcher) { group.forEach { store.markDelivered(it.id, at) } }
        } else {
            withContext(ioDispatcher) { group.forEach { store.markDeliveryFailed(it.id, error, at) } }
        }
        intents.forEach { removeIntent(it.dedupeKey) }
    }

    private fun intentFor(timer: Timer, now: Long): DeliveryIntent {
        val runAt = timer.nextRunAt
        return DeliveryIntent(
            dedupeKey = "${timer.id}@${runAt ?: "run-now-$now"}",
            timerId = timer.id,
            sessionId = timer.sessionId,
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
        if ((version != INTENT_SCHEMA_VERSION && version != LEGACY_INTENT_SCHEMA_VERSION) || entries == null) {
            return rebuildIntents()
        }
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
                        put("timerId", intent.timerId)
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

    private suspend fun deliverTimers(group: List<Timer>): TimerError? {
        val anchor = group.firstOrNull() ?: return null
        if (anchor.sessionFile.isBlank() || !File(anchor.sessionFile).isFile) {
            return TimerError.SESSION_MISSING
        }
        val opened = try {
            runtime.openSession(anchor.sessionFile, anchor.cwd)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TimerError.OPEN_FAILED
        }
        val (text, reminder) = reminderText(group)
        return try {
            runtime.deliverReminder(opened.sessionId, text, reminder)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TimerError.DELIVERY_FAILED
        }
    }

    private suspend fun timerLoop() {
        while (currentCoroutineContext().isActive) {
            val delivered = try {
                val now = clock.millis()
                val current = readTimers()
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
                logError(TAG, "timer timer iteration failed", error)
                withTimeoutOrNull(MAX_TIMER_WAIT_MS) { wake.receive() }
                false
            }
            if (delivered) continue
        }
    }

    private suspend fun refresh() {
        list()
    }

    private suspend fun readTimers(): List<Timer> = withContext(ioDispatcher) {
        store.list()
    }

    private companion object {
        const val MAX_TIMER_WAIT_MS = 15 * 60 * 1000L
        const val INTENT_SCHEMA_VERSION = 2
        const val LEGACY_INTENT_SCHEMA_VERSION = 1
        const val FILE_NAME = "delivery-intents.json"
        const val TAG = "TimerService"
    }
}

private data class DeliveryIntent(
    val dedupeKey: String,
    val timerId: String,
    val sessionId: String,
    val createdAt: Long,
)

private const val LEGACY_INTENT_TIMER_ID_KEY = "scheduleId"

private fun intentFromJson(json: JsonObject): DeliveryIntent = DeliveryIntent(
    dedupeKey = json.requiredIntentString("dedupeKey"),
    timerId = (json["timerId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: json.requiredIntentString(LEGACY_INTENT_TIMER_ID_KEY),
    sessionId = json.requiredIntentString("sessionId"),
    createdAt = json.requiredIntentLong("createdAt"),
)

private fun JsonObject.requiredIntentString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing intent field: $name")

private fun JsonObject.requiredIntentLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw IllegalArgumentException("invalid intent field: $name")

private fun reminderText(group: List<Timer>): Pair<String, String> {
    val first = group.first()
    if (group.size == 1) {
        val text = "[定时提醒] ${first.name}\n这是你设置的定时提醒，现在到时间了。\n\n${first.prompt}"
        return text to first.name
    }
    val header = "[定时提醒] ${group.size} 个提醒\n这是你设置的定时提醒，现在到时间了。"
    val body = group.mapIndexed { index, timer ->
        "\n\n${index + 1}. ${timer.name}\n${timer.prompt}"
    }.joinToString("")
    return header + body to "${group.size} 个提醒"
}
