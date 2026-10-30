package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.adoptActiveRun
import io.github.whywood.deepdeep.ui.chat.beginTurn
import io.github.whywood.deepdeep.ui.chat.finishWithError
import io.github.whywood.deepdeep.ui.chat.markRetryingTail
import io.github.whywood.deepdeep.ui.chat.reduce
import io.github.whywood.deepdeep.ui.chat.toggleStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SessionProjection(
    private val runtime: AgentRuntime,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val strings: () -> ChatUiStrings = { ChatUiStrings.fallback() },
) {
    private val mutableState = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = mutableState.asStateFlow()

    private var eventJob: Job? = null
    private var currentSessionId: String? = null

    suspend fun open(sessionId: String) {
        close()
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val usage = withContext(ioDispatcher) { runtime.usage(sessionId) }
        val localized = history.map { message ->
            message.error?.let { raw -> message.copy(error = strings().runError(raw)) } ?: message
        }
        var seeded = ConversationState(messages = localized, loaded = true, usage = usage)
        runtime.activeRuns.value[sessionId]?.let { active ->
            seeded = seeded.adoptActiveRun(active.runId, RuntimeActivity.Thinking, active.startedAt)
        }
        currentSessionId = sessionId
        mutableState.value = seeded
        eventJob = scope.launch {
            runtime.events.filter { it.sessionId == sessionId }.collect { scoped ->
                mutableState.value = reduce(mutableState.value, scoped.event, strings())
            }
        }
    }

    fun ask(prompt: String) {
        val sessionId = currentSessionId ?: return
        val text = prompt.trim()
        if (text.isEmpty()) return
        mutableState.update { it.beginTurn(text, System.currentTimeMillis()) }
        scope.launch {
            try {
                runtime.send(sessionId, text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error.message)
            }
        }
    }

    fun stop() {
        val sessionId = currentSessionId ?: return
        runtime.stop(sessionId)
    }

    fun retryLast() {
        val sessionId = currentSessionId ?: return
        mutableState.update { it.markRetryingTail(System.currentTimeMillis()) }
        scope.launch {
            try {
                runtime.retryLastTurn(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error.message)
            }
        }
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        mutableState.update { it.toggleStep(messageId, stepId) }
    }

    fun close() {
        eventJob?.cancel()
        eventJob = null
        currentSessionId = null
        mutableState.value = ConversationState()
    }

    private fun fail(rawError: String?) {
        mutableState.value = mutableState.value.finishWithError("", rawError ?: strings().requestFailed, strings())
    }
}
