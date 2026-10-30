package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.finishWithError
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
            writeTarget.updateIfPresent(sessionId) {
                it.copy(
                    sending = true,
                    activity = RuntimeActivity.Retrying(attempt),
                    activityStartedAt = now(),
                )
            }
            delay(delayMillis)
            if (states[sessionId]?.token !== token) return@launch
            if (runtime.busySessionIds.value.contains(sessionId)) return@launch
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

    private fun begin(sessionId: String) {
        val at = now()
        val applied = writeTarget.updateIfPresent(sessionId) { state ->
            state.copy(
                messages = markRetryingTail(state.messages, at),
                sending = true,
                activity = RuntimeActivity.Thinking,
                activityStartedAt = at,
            )
        }
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
            }
        }
    }

    private fun markRetryingTail(messages: List<AssistantMessage>, at: Long): List<AssistantMessage> {
        val index = messages.indexOfLast {
            it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.FAILED
        }
        if (index < 0) {
            return messages + AssistantMessage(
                id = (messages.maxOfOrNull { it.id } ?: -1L) + 1,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = at,
            )
        }
        val retrying = messages[index].copy(
            streaming = true,
            retrying = true,
            runId = "",
            finishedAt = null,
        )
        return messages.take(index) + retrying
    }
}

internal fun ChatUiState.sessionModel(): SessionModelSnapshot? =
    providerId.takeIf { it.isNotBlank() }
        ?.let { provider -> SessionModelSnapshot(provider, modelId, thinkingLevel) }
