package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.cancelRetry
import io.github.whywood.deepdeep.ui.chat.finishWithError
import io.github.whywood.deepdeep.ui.chat.markRetryScheduled
import io.github.whywood.deepdeep.ui.chat.markRetryingTail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class AutoRetryController(
    private val scope: CoroutineScope,
    private val writeTarget: ConversationWriteTarget,
    private val runtime: AgentRuntime,
    enabledFlow: Flow<Boolean>,
    private val canRetry: (String) -> Boolean,
    private val awaitSession: suspend (String) -> Unit,
    private val onBegin: (String) -> Unit = {},
    private val onActiveError: (ConversationState, String?) -> Unit = { _, _ -> },
    private val onFailure: (String, String?) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    private val delayMillis: Long = 2_000L,
    private val maxAttempts: Int = 5,
) {
    private data class RetryState(
        val attempts: Int,
        val token: Any?,
        val retiredRunId: String?,
        val job: Job?,
    )

    private val states = mutableMapOf<String, RetryState>()
    private var enabled = false

    init {
        scope.launch { enabledFlow.collect { enabled = it } }
    }

    fun attempts(sessionId: String): Int = states[sessionId]?.attempts ?: 0

    fun isRetrying(sessionId: String): Boolean = states[sessionId]?.job?.isActive == true

    fun onActivityChanged(runId: String): Boolean = states.values.any { it.retiredRunId == runId }

    fun onRunFailed(sessionId: String, runId: String, error: String): Boolean {
        if (!enabled || !canRetry(error)) return false
        val previous = states[sessionId]
        val attempt = (previous?.attempts ?: 0) + 1
        if (attempt > maxAttempts) {
            clear(sessionId)
            return false
        }
        previous?.job?.cancel()
        val token = Any()
        val job = scope.launch {
            writeTarget.updateIfPresent(sessionId) { it.markRetryScheduled(attempt, now()) }
            delay(delayMillis)
            if (states[sessionId]?.token !== token) return@launch
            if (!awaitIdle(sessionId, token)) {
                if (states[sessionId]?.token !== token) return@launch
                writeTarget.updateIfPresent(sessionId) { it.cancelRetry(now()) }
                states[sessionId] = RetryState(attempts = attempt, token = null, retiredRunId = null, job = null)
                onFailure(sessionId, null)
                return@launch
            }
            states[sessionId] = RetryState(attempts = attempt, token = null, retiredRunId = null, job = null)
            begin(sessionId)
        }
        states[sessionId] = RetryState(attempts = attempt, token = token, retiredRunId = runId, job = job)
        return true
    }

    fun onRunSucceeded(sessionId: String) = clear(sessionId)

    fun onStop(sessionId: String) = clear(sessionId)

    fun onRetryEvent(sessionId: String, event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.RunFailed -> onRunFailed(sessionId, event.runId, event.message)
            is RuntimeEvent.RunFinished -> if (event.status != io.github.whywood.deepdeep.runtime.RunStatus.FAILED) {
                onRunSucceeded(sessionId)
            }

            else -> Unit
        }
    }

    fun manualRetry(state: ChatUiState, messageId: Long) {
        if (!state.ready || !state.historyLoaded || state.sending || state.switchingConversation) return
        val sessionId = state.conversationId ?: return
        val index = state.messages.indexOfLast { it.id == messageId }
        if (index < 0 || index != state.messages.lastIndex) return
        val failed = state.messages[index]
        if (failed.role != AssistantRole.ASSISTANT || failed.status != MessageStatus.FAILED) return
        clear(sessionId)
        begin(sessionId)
    }

    fun finishRunError(sessionId: String, message: String, strings: ChatUiStrings) {
        val reduced = writeTarget.read(sessionId).finishWithError("", message, strings)
        if (!writeTarget.isActive(sessionId)) {
            writeTarget.updateIfPresent(sessionId) { reduced }
            return
        }
        val hasFailure = reduced.messages.any { it.status == MessageStatus.FAILED && it.error != null }
        onActiveError(reduced, if (hasFailure) null else strings.runError(message))
    }

    fun clear(sessionId: String) {
        states.remove(sessionId)?.job?.cancel()
    }

    private suspend fun awaitIdle(sessionId: String, token: Any): Boolean {
        var remaining = BUSY_WAIT_ATTEMPTS
        while (runtime.busySessionIds.value.contains(sessionId)) {
            if (states[sessionId]?.token !== token) return false
            if (remaining <= 0) return false
            delay(BUSY_POLL_INTERVAL_MS)
            remaining -= 1
        }
        return states[sessionId]?.token === token
    }

    private fun begin(sessionId: String) {
        val at = now()
        val applied = writeTarget.updateIfPresent(sessionId) { state -> state.markRetryingTail(at) }
        if (!applied) return
        onBegin(sessionId)
        scope.launch {
            try {
                awaitSession(sessionId)
                runtime.retryLastTurn(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(sessionId, error.message)
                writeTarget.updateIfPresent(sessionId) { state ->
                    if (state.sending || state.messages.any { it.retrying }) {
                        state.cancelRetry(now())
                    } else {
                        state
                    }
                }
            }
        }
    }

    private companion object {
        const val BUSY_POLL_INTERVAL_MS = 500L
        const val BUSY_WAIT_ATTEMPTS = 40
    }
}

internal fun ChatUiState.sessionModel(): SessionModelSnapshot? =
    providerId.takeIf { it.isNotBlank() }
        ?.let { provider -> SessionModelSnapshot(provider, modelId, thinkingLevel) }
