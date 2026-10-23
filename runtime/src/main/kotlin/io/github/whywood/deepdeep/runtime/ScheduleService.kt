package io.github.whywood.deepdeep.runtime

import java.io.File
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.withTimeoutOrNull

class ScheduleDeliveryException(val error: ScheduleError) : Exception(error.name)

class ScheduleService(
    private val runtime: AgentRuntime,
    private val store: ScheduleStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val schedulesState = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = schedulesState.asStateFlow()

    private val deliveryMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            refresh()
            deliverDue()
            launch {
                store.changes.collect {
                    refresh()
                    wake.trySend(Unit)
                }
            }
            launch {
                runtime.events.collect { event ->
                    handleRuntimeEvent(event)
                    wake.trySend(Unit)
                }
            }
            launch { timerLoop() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun list(): List<Schedule> {
        val schedules = store.list()
        schedulesState.value = schedules
        return schedules
    }

    suspend fun recomputeTargets() {
        store.recomputeTargets()
        refresh()
    }

    suspend fun create(newSchedule: NewSchedule): Schedule {
        val created = store.create(newSchedule)
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
        val updated = store.update(id, patch)
        refresh()
        return updated
    }

    suspend fun delete(id: String): Boolean {
        val deleted = store.delete(id)
        refresh()
        return deleted
    }

    suspend fun runNow(id: String): Result<Unit> = deliveryMutex.withLock {
        val schedule = store.list().firstOrNull { it.id == id }
            ?: return@withLock Result.failure(IllegalArgumentException("schedule not found"))
        val error = deliverSchedules(listOf(schedule))
        val now = clock.millis()
        if (error == null) {
            store.markRunNow(id, now)
            refresh()
            Result.success(Unit)
        } else {
            store.markRunNow(id, now, error)
            refresh()
            Result.failure(ScheduleDeliveryException(error))
        }
    }

    internal suspend fun deliverDue() = deliveryMutex.withLock {
        val now = clock.millis()
        val due = store.list().filter { it.enabled && it.nextRunAt?.let { time -> time <= now } == true }
        if (due.isNotEmpty()) {
            due.groupBy { it.sessionId }.forEach { (_, group) ->
                val ordered = group.sortedWith(compareBy({ it.nextRunAt ?: 0L }, { it.createdAt }, { it.id }))
                val error = deliverSchedules(ordered)
                val at = clock.millis()
                if (error == null) {
                    ordered.forEach { store.markDelivered(it.id, at) }
                } else {
                    ordered.forEach { store.markDeliveryFailed(it.id, error, at) }
                }
            }
        }
        refresh()
    }

    internal suspend fun handleRuntimeEvent(event: SessionRuntimeEvent) {
        when (val inner = event.event) {
            is RuntimeEvent.SessionRemoved -> store.deleteBySession(event.sessionId)
            is RuntimeEvent.SessionArchived -> if (inner.archived) store.disableBySession(event.sessionId)
            else -> Unit
        }
        refresh()
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
            val now = clock.millis()
            val current = store.list()
            val hasDue = current.any { it.enabled && it.nextRunAt?.let { time -> time <= now } == true }
            if (hasDue) {
                deliverDue()
                continue
            }
            val next = current.asSequence().filter { it.enabled }.mapNotNull { it.nextRunAt }.minOrNull()
            val wait = next?.minus(now)?.coerceIn(1L, MAX_TIMER_WAIT_MS) ?: MAX_TIMER_WAIT_MS
            withTimeoutOrNull(wait) { wake.receive() }
        }
    }

    private suspend fun refresh() {
        list()
    }

    private companion object {
        const val MAX_TIMER_WAIT_MS = 15 * 60 * 1000L
    }
}

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
