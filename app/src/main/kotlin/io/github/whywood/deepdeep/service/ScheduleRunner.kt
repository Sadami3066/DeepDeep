package io.github.whywood.deepdeep.service

import android.util.Log
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleRunStatus
import io.github.whywood.deepdeep.runtime.ScheduleStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ScheduleRunner(
    private val runtime: AgentRuntime,
    private val store: ScheduleStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val running = AtomicBoolean(false)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            runtime.ensureStarted()
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun tick() {
        if (!running.compareAndSet(false, true)) return
        try {
            val now = System.currentTimeMillis()
            val due = store.list().firstOrNull { schedule ->
                schedule.enabled && schedule.nextRunAt?.let { it <= now } == true
            } ?: return
            execute(due)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "schedule tick failed", error)
        } finally {
            running.set(false)
        }
    }

    private suspend fun execute(schedule: Schedule) {
        val started = store.markRunning(schedule.id, System.currentTimeMillis()) ?: return
        val workspace = runCatching {
            runtime.workspaces().firstOrNull { it.id == started.workspaceId && !it.hidden }
        }.getOrNull()
        if (workspace == null) {
            fail(started.id, "workspace not found")
            return
        }
        val opened = runCatching { runtime.newSession(workspace.id) }.getOrNull()
        if (opened == null) {
            fail(started.id, "session creation failed")
            return
        }
        val outcome = runAndWait(opened.sessionId, started.prompt)
        store.markFinished(
            id = started.id,
            status = outcome.status,
            sessionId = opened.sessionId,
            error = outcome.error,
            at = System.currentTimeMillis(),
        )
    }

    private suspend fun fail(id: String, message: String) {
        store.markFinished(
            id = id,
            status = ScheduleRunStatus.FAILED,
            sessionId = null,
            error = message,
            at = System.currentTimeMillis(),
        )
    }

    private suspend fun runAndWait(sessionId: String, prompt: String): RunOutcome = coroutineScope {
        val result = CompletableDeferred<RunOutcome>()
        val collector = launch {
            runtime.events.collect { event ->
                if (event.sessionId != sessionId) return@collect
                when (val value = event.event) {
                    is RuntimeEvent.RunFinished -> result.complete(
                        RunOutcome(value.status.toScheduleStatus(), null),
                    )

                    is RuntimeEvent.RunFailed -> result.complete(
                        RunOutcome(ScheduleRunStatus.FAILED, value.message.takeIf { it.isNotBlank() }),
                    )

                    else -> Unit
                }
            }
        }
        val sender = launch {
            runCatching { runtime.send(sessionId, prompt) }
                .onFailure { error ->
                    if (!result.isCompleted) {
                        result.complete(RunOutcome(ScheduleRunStatus.FAILED, error.message))
                    }
                }
        }
        val outcome = withTimeoutOrNull(RUN_TIMEOUT_MS) { result.await() }
        collector.cancel()
        if (outcome == null) {
            runtime.stop(sessionId)
            sender.cancel()
            RunOutcome(ScheduleRunStatus.FAILED, "run timed out")
        } else {
            withTimeoutOrNull(FINISH_GRACE_MS) { sender.join() }
            outcome
        }
    }

    private fun RunStatus.toScheduleStatus(): ScheduleRunStatus = when (this) {
        RunStatus.DONE -> ScheduleRunStatus.DONE
        RunStatus.STOPPED -> ScheduleRunStatus.STOPPED
        RunStatus.FAILED -> ScheduleRunStatus.FAILED
    }

    private data class RunOutcome(
        val status: ScheduleRunStatus,
        val error: String?,
    )

    companion object {
        private const val TAG = "DeepDeepSchedule"
        private const val TICK_MS = 30_000L
        private const val RUN_TIMEOUT_MS = 30 * 60_000L
        private const val FINISH_GRACE_MS = 10_000L
    }
}
