package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.conversation.adoptActiveRun
import io.github.whywood.deepdeep.conversation.beginTurn
import io.github.whywood.deepdeep.conversation.finishWithError
import io.github.whywood.deepdeep.conversation.finishWithStatus
import io.github.whywood.deepdeep.ui.chat.localizedRunError
import io.github.whywood.deepdeep.conversation.markRetryingTail
import io.github.whywood.deepdeep.conversation.reduce
import io.github.whywood.deepdeep.conversation.toggleStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val strings: () -> ConversationStrings = { ConversationStrings.fallback() },
) {
    private val mutableState = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = mutableState.asStateFlow()

    private var eventJob: Job? = null
    private var currentSessionId: String? = null
    private val preparations = mutableMapOf<String, Job>()

    suspend fun open(sessionId: String) {
        close()
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val usage = withContext(ioDispatcher) { runtime.usage(sessionId) }
        val localized = history.map { message -> message.localizedRunError { raw -> strings().runError(raw) } }
        var seeded = ConversationState(
            messages = localized,
            loaded = true,
            usage = usage,
            queuedInputs = runtime.queuedInputs(sessionId),
        )
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

    fun sendTurn(prompt: String, images: List<ImagePart> = emptyList()) = sendPreparedTurn(prompt) { images }

    fun sendPreparedTurn(prompt: String, prepareImages: suspend () -> List<ImagePart>) {
        val sessionId = currentSessionId ?: return
        val text = prompt.trim()
        if (text.isEmpty()) return
        mutableState.update { it.beginTurn(text, System.currentTimeMillis()) }
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val images = prepareImages()
                preparations.remove(sessionId, job)
                runtime.send(sessionId, text, images)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentSessionId == sessionId) fail(error.message)
            } finally {
                preparations.remove(sessionId, job)
            }
        }
        preparations[sessionId] = job
        job.start()
    }

    fun enqueue(prompt: String, followUp: Boolean) {
        val sessionId = currentSessionId ?: return
        val text = prompt.trim()
        if (text.isEmpty()) return
        scope.launch {
            try {
                if (followUp) runtime.followUp(sessionId, text) else runtime.steer(sessionId, text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error.message)
            }
        }
    }

    fun stop() {
        val sessionId = currentSessionId ?: return
        preparations.remove(sessionId)?.let { job ->
            job.cancel()
            mutableState.update { it.finishWithStatus("", "", RunStatus.STOPPED, System.currentTimeMillis()) }
        }
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
        mutableState.value = mutableState.value.finishWithError("", rawError, strings())
    }
}
