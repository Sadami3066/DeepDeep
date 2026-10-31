package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveRunView(
    val sessionId: String,
    val title: String,
    val activity: RuntimeActivity?,
    val startedAt: Long,
)

sealed interface RunActivityChange {
    data object Updated : RunActivityChange

    data class Finished(
        val sessionId: String,
        val title: String,
        val status: RunStatus,
        val text: String,
        val error: String?,
    ) : RunActivityChange
}

class RunActivityTracker(
    private val runtime: ConversationQueries,
    private val fallbackTitle: () -> String,
) {
    private val mutableActiveRuns = MutableStateFlow<Map<String, ActiveRunView>>(emptyMap())
    val activeRuns: StateFlow<Map<String, ActiveRunView>> = mutableActiveRuns.asStateFlow()

    private val mutableChanges = MutableSharedFlow<RunActivityChange>(
        extraBufferCapacity = CHANGE_BUFFER,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val changes: SharedFlow<RunActivityChange> = mutableChanges.asSharedFlow()

    private var scope = newScope()
    private var collector: Job? = null
    private var seedCollector: Job? = null
    private val active = LinkedHashMap<String, ActiveRunView>()
    private val finishedSessions = HashSet<String>()
    private val seededSessions = HashSet<String>()

    fun start() {
        if (collector != null) return
        collector = scope.launch {
            runtime.events.collect(::onEvent)
        }
        seedCollector = scope.launch {
            runtime.activeRuns.collect(::seed)
        }
    }

    fun stop() {
        collector?.cancel()
        collector = null
        seedCollector?.cancel()
        seedCollector = null
        scope.cancel()
        scope = newScope()
        active.clear()
        finishedSessions.clear()
        seededSessions.clear()
        mutableActiveRuns.value = emptyMap()
    }

    fun titleOf(sessionId: String): String? = mutableActiveRuns.value[sessionId]?.title

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private suspend fun seed(runs: Map<String, ActiveRunSnapshot>) {
        var changed = false
        val stale = seededSessions.filter { it !in runs.keys && it in active.keys }
        stale.forEach { sessionId ->
            active.remove(sessionId)
            seededSessions.remove(sessionId)
            changed = true
        }
        runs.forEach { (sessionId, snapshot) ->
            if (active.containsKey(sessionId) || sessionId in finishedSessions) return@forEach
            active[sessionId] = ActiveRunView(
                sessionId = sessionId,
                title = runtime.sessionTitle(sessionId) ?: fallbackTitle(),
                activity = RuntimeActivity.Thinking,
                startedAt = snapshot.startedAt,
            )
            seededSessions += sessionId
            changed = true
        }
        if (changed) publish()
    }

    private suspend fun onEvent(event: SessionRuntimeEvent) {
        val runtimeEvent = event.event
        if (runtimeEvent is RuntimeEvent.SessionListChanged) {
            refreshTitles()
            publish()
            return
        }
        if (event.sessionId.isBlank()) return
        when (runtimeEvent) {
            is RuntimeEvent.RunStarted -> {
                finishedSessions.remove(event.sessionId)
                seededSessions.remove(event.sessionId)
                active[event.sessionId] = ActiveRunView(
                    sessionId = event.sessionId,
                    title = runtime.sessionTitle(event.sessionId) ?: fallbackTitle(),
                    activity = RuntimeActivity.Thinking,
                    startedAt = runtimeEvent.startedAt,
                )
                publish()
            }

            is RuntimeEvent.ActivityChanged -> {
                val current = active[event.sessionId] ?: return
                active[event.sessionId] = current.copy(activity = runtimeEvent.activity)
                publish()
            }

            is RuntimeEvent.RunFinished -> {
                finishedSessions.add(event.sessionId)
                seededSessions.remove(event.sessionId)
                val current = active.remove(event.sessionId)
                publish()
                mutableChanges.emit(
                    RunActivityChange.Finished(
                        sessionId = event.sessionId,
                        title = current?.title ?: fallbackTitle(),
                        status = runtimeEvent.status,
                        text = runtimeEvent.text,
                        error = null,
                    ),
                )
            }

            is RuntimeEvent.RunFailed -> {
                finishedSessions.add(event.sessionId)
                seededSessions.remove(event.sessionId)
                val current = active.remove(event.sessionId)
                publish()
                mutableChanges.emit(
                    RunActivityChange.Finished(
                        sessionId = event.sessionId,
                        title = current?.title ?: fallbackTitle(),
                        status = RunStatus.FAILED,
                        text = "",
                        error = runtimeEvent.message,
                    ),
                )
            }

            else -> Unit
        }
    }

    private suspend fun refreshTitles() {
        active.entries.toList().forEach { (sessionId, current) ->
            val title = runtime.sessionTitle(sessionId) ?: return@forEach
            if (title != current.title) {
                active[sessionId] = current.copy(title = title)
            }
        }
    }

    private suspend fun publish() {
        mutableActiveRuns.value = LinkedHashMap(active)
        mutableChanges.emit(RunActivityChange.Updated)
    }

    private companion object {
        const val CHANGE_BUFFER = 128
    }
}
