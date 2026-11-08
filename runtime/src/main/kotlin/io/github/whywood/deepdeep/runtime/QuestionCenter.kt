package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionAnswer(
    val questionId: String,
    val selected: List<String> = emptyList(),
    val custom: String? = null,
    val skipped: Boolean = false,
)

data class PendingQuestion(
    val requestId: String,
    val sessionId: String,
    val toolCallId: String,
    val questions: List<UserQuestion>,
)

sealed interface QuestionOutcome {
    data class Answered(val answers: List<QuestionAnswer>) : QuestionOutcome

    data object Cancelled : QuestionOutcome
}

class QuestionCenter(
    private val persistence: QuestionPersistence? = null,
    private val persistScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
) {
    private val lock = Any()
    private val pendingState = MutableStateFlow<List<PendingQuestion>>(emptyList())
    private val interruptedState = MutableStateFlow<List<InterruptedQuestion>>(emptyList())
    private val waiters = mutableMapOf<String, CompletableDeferred<QuestionOutcome>>()

    val pending: StateFlow<List<PendingQuestion>> = pendingState.asStateFlow()

    val interrupted: StateFlow<List<InterruptedQuestion>> = interruptedState.asStateFlow()

    suspend fun loadPersisted() {
        val store = persistence ?: return
        val leftover = runCatching { store.load() }.getOrDefault(emptyList())
        runCatching { store.clear() }
        if (leftover.isNotEmpty()) {
            interruptedState.value = leftover.sortedBy { it.createdAt }
        }
    }

    suspend fun dismissInterrupted(sessionId: String) {
        interruptedState.value = interruptedState.value.filterNot { it.sessionId == sessionId }
    }

    suspend fun ask(
        sessionId: String,
        toolCallId: String,
        questions: List<UserQuestion>,
        signal: RunSignal? = null,
    ): QuestionOutcome {
        val requestId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val deferred = CompletableDeferred<QuestionOutcome>()
        synchronized(lock) {
            waiters[requestId] = deferred
            pendingState.value = pendingState.value + PendingQuestion(
                requestId = requestId,
                sessionId = sessionId,
                toolCallId = toolCallId,
                questions = questions,
            )
        }
        persist {
            persistence?.append(
                InterruptedQuestion(
                    requestId = requestId,
                    sessionId = sessionId,
                    toolCallId = toolCallId,
                    questions = questions,
                    createdAt = createdAt,
                ),
            )
        }
        val unsubscribe = signal?.onAbort { cancel(requestId) }
        return try {
            deferred.await()
        } finally {
            unsubscribe?.invoke()
            removePending(requestId)
        }
    }

    fun answer(requestId: String, answers: List<QuestionAnswer>) {
        val deferred = synchronized(lock) { waiters.remove(requestId) } ?: return
        removePending(requestId)
        deferred.complete(QuestionOutcome.Answered(answers))
    }

    fun cancel(requestId: String) {
        val deferred = synchronized(lock) { waiters.remove(requestId) } ?: return
        removePending(requestId)
        deferred.complete(QuestionOutcome.Cancelled)
    }

    private fun removePending(requestId: String) {
        synchronized(lock) {
            waiters.remove(requestId)
            pendingState.value = pendingState.value.filterNot { it.requestId == requestId }
        }
        persist { persistence?.remove(requestId) }
    }

    private fun persist(block: suspend () -> Unit) {
        persistScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }
}
