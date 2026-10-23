package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuestionOption(
    val label: String,
    val description: String? = null,
)

data class UserQuestion(
    val id: String,
    val header: String? = null,
    val question: String,
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
)

data class QuestionAnswer(
    val questionId: String,
    val selected: List<String> = emptyList(),
    val custom: String? = null,
    val skipped: Boolean = false,
)

data class PendingQuestion(
    val requestId: String,
    val toolCallId: String,
    val questions: List<UserQuestion>,
)

sealed interface QuestionOutcome {
    data class Answered(val answers: List<QuestionAnswer>) : QuestionOutcome

    data object Cancelled : QuestionOutcome

    data object Unavailable : QuestionOutcome
}

class QuestionCenter(private val isForeground: () -> Boolean) {
    private val lock = Any()
    private val pendingState = MutableStateFlow<List<PendingQuestion>>(emptyList())
    private val waiters = mutableMapOf<String, CompletableDeferred<QuestionOutcome>>()

    val pending: StateFlow<List<PendingQuestion>> = pendingState.asStateFlow()

    suspend fun ask(
        toolCallId: String,
        questions: List<UserQuestion>,
        signal: RunSignal? = null,
    ): QuestionOutcome {
        if (!isForeground()) return QuestionOutcome.Unavailable
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<QuestionOutcome>()
        synchronized(lock) {
            if (!isForeground()) return QuestionOutcome.Unavailable
            waiters[requestId] = deferred
            pendingState.value = pendingState.value + PendingQuestion(
                requestId = requestId,
                toolCallId = toolCallId,
                questions = questions,
            )
        }
        val unsubscribe = signal?.onAbort { cancel(requestId) }
        return try {
            deferred.await()
        } finally {
            unsubscribe?.invoke()
            synchronized(lock) {
                waiters.remove(requestId)
                pendingState.value = pendingState.value.filterNot { it.requestId == requestId }
            }
        }
    }

    fun answer(requestId: String, answers: List<QuestionAnswer>) {
        val deferred = synchronized(lock) { waiters.remove(requestId) } ?: return
        pendingState.value = pendingState.value.filterNot { it.requestId == requestId }
        deferred.complete(QuestionOutcome.Answered(answers))
    }

    fun cancel(requestId: String) {
        val deferred = synchronized(lock) { waiters.remove(requestId) } ?: return
        pendingState.value = pendingState.value.filterNot { it.requestId == requestId }
        deferred.complete(QuestionOutcome.Cancelled)
    }
}
