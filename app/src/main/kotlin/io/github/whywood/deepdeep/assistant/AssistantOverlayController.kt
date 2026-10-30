package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class AssistantOverlayPhase { Idle, Thinking, Streaming, Done, Error }

enum class AssistantOverlayError { Busy, SendFailed }

data class AssistantOverlayState(
    val phase: AssistantOverlayPhase = AssistantOverlayPhase.Idle,
    val sessionId: String? = null,
    val answer: String = "",
    val error: AssistantOverlayError? = null,
)

class AssistantOverlayController(
    private val runtime: AgentRuntime,
    private val activeSessionId: () -> String?,
    private val homeWorkspaceId: String,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AssistantOverlayState())
    val state: StateFlow<AssistantOverlayState> = mutableState.asStateFlow()

    private var sendJob: Job? = null
    private var eventJob: Job? = null
    private var currentRunId: String? = null

    fun ask(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return
        if (mutableState.value.phase == AssistantOverlayPhase.Thinking) return
        if (mutableState.value.phase == AssistantOverlayPhase.Streaming) return
        cancelJobs()
        currentRunId = null
        mutableState.value = AssistantOverlayState(phase = AssistantOverlayPhase.Thinking)
        sendJob = scope.launch {
            runCatching { runtime.ensureStarted() }
            val sessionId = resolveSession(create = true) ?: run {
                mutableState.value = AssistantOverlayState(phase = AssistantOverlayPhase.Error, error = AssistantOverlayError.SendFailed)
                return@launch
            }
            mutableState.value = mutableState.value.copy(sessionId = sessionId)
            val running = runtime.activeRuns.value[sessionId]
            if (running != null) {
                attach(sessionId, running.runId)
                yield()
                try {
                    runtime.followUp(sessionId, prompt)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(
                        phase = AssistantOverlayPhase.Error,
                        error = AssistantOverlayError.Busy,
                    )
                }
                return@launch
            }
            eventJob = listen(sessionId)
            yield()
            try {
                runtime.send(sessionId, prompt)
                finishFromHistory(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (mutableState.value.phase == AssistantOverlayPhase.Thinking ||
                    mutableState.value.phase == AssistantOverlayPhase.Streaming
                ) {
                    mutableState.value = mutableState.value.copy(
                        phase = AssistantOverlayPhase.Error,
                        error = AssistantOverlayError.SendFailed,
                    )
                }
            }
        }
    }

    fun attachRunning() {
        scope.launch {
            runCatching { runtime.ensureStarted() }
            val sessionId = resolveSession(create = false) ?: return@launch
            val running = runtime.activeRuns.value[sessionId] ?: return@launch
            cancelJobs()
            mutableState.value = AssistantOverlayState(phase = AssistantOverlayPhase.Thinking, sessionId = sessionId)
            attach(sessionId, running.runId)
            yield()
            if (runtime.activeRuns.value[sessionId] == null) finishFromHistory(sessionId)
        }
    }

    fun stop() {
        val sessionId = mutableState.value.sessionId ?: return
        runtime.stop(sessionId)
        cancelJobs()
        mutableState.value = mutableState.value.copy(phase = AssistantOverlayPhase.Done, error = null)
    }

    fun reset() {
        cancelJobs()
        currentRunId = null
        mutableState.value = AssistantOverlayState()
    }

    private fun attach(sessionId: String, runId: String) {
        currentRunId = runId
        eventJob = listen(sessionId)
    }

    private fun listen(sessionId: String): Job = scope.launch {
        runtime.events.filter { it.sessionId == sessionId }.collect { sessionEvent ->
            when (val event = sessionEvent.event) {
                is RuntimeEvent.RunStarted -> if (currentRunId == null) currentRunId = event.runId
                is RuntimeEvent.TextDelta -> if (matches(event.runId)) {
                    mutableState.value = mutableState.value.copy(
                        phase = AssistantOverlayPhase.Streaming,
                        answer = mutableState.value.answer + event.text,
                        error = null,
                    )
                }

                is RuntimeEvent.RunFinished -> if (matches(event.runId)) {
                    val text = event.text.ifBlank { mutableState.value.answer }
                    mutableState.value = mutableState.value.copy(
                        phase = AssistantOverlayPhase.Done,
                        answer = text,
                        error = null,
                    )
                }

                is RuntimeEvent.RunFailed -> if (matches(event.runId)) {
                    mutableState.value = mutableState.value.copy(
                        phase = AssistantOverlayPhase.Error,
                        error = AssistantOverlayError.SendFailed,
                    )
                }

                else -> Unit
            }
        }
    }

    private fun matches(runId: String): Boolean = currentRunId == null || currentRunId == runId

    private suspend fun finishFromHistory(sessionId: String) {
        if (mutableState.value.phase != AssistantOverlayPhase.Thinking) return
        val text = runCatching {
            runtime.history(sessionId).lastOrNull { it.role == AssistantRole.ASSISTANT }?.text
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(phase = AssistantOverlayPhase.Done, answer = text)
        } else {
            mutableState.value = mutableState.value.copy(
                phase = AssistantOverlayPhase.Error,
                error = AssistantOverlayError.SendFailed,
            )
        }
    }

    private suspend fun resolveSession(create: Boolean): String? {
        activeSessionId()?.takeIf { it.isNotBlank() }?.let { return it }
        val sessions = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
        sessions.maxByOrNull { it.updatedAt }?.let { return it.id }
        if (!create) return null
        return runCatching { runtime.newSession(homeWorkspaceId).sessionId }.getOrNull()
    }

    private fun cancelJobs() {
        eventJob?.cancel()
        eventJob = null
        sendJob?.cancel()
        sendJob = null
    }
}
