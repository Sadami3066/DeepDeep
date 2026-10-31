package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.ui.chat.PresentedItem

const val FLOATING_SLEEPY_AFTER_MS = 10 * 60_000L
const val FLOATING_COMPLETED_LIMIT = 20
const val FLOATING_DISMISSED_LIMIT = 50

enum class FloatingWhaleMood {
    IDLE,
    WAVE,
    THINKING,
    SLEEPY,
}

data class FloatingRun(
    val sessionId: String,
    val title: String,
    val activity: RuntimeActivity?,
    val startedAt: Long,
)

data class FloatingCompletion(
    val sessionId: String,
    val title: String,
    val status: RunStatus,
    val text: String,
    val error: String?,
    val finishedAt: Long,
    val items: List<PresentedItem> = emptyList(),
) {
    val key: String = "completion:$sessionId:$finishedAt:$status"

    val hasDelivery: Boolean = items.isNotEmpty()

    val deliveryLabel: String
        get() = items.firstOrNull()?.let(::deliveryItemLabel).orEmpty()
}

data class FloatingFeedState(
    val runs: List<FloatingRun> = emptyList(),
    val questions: List<PendingQuestion> = emptyList(),
    val completed: List<FloatingCompletion> = emptyList(),
    val stagedDeliveries: Map<String, List<PresentedItem>> = emptyMap(),
    val sessionTitles: Map<String, String> = emptyMap(),
    val lastEventAt: Long = 0L,
    val dismissedCardKeys: Set<String> = emptySet(),
)

sealed interface FloatingEventCard {
    val key: String

    data class Question(val pending: PendingQuestion) : FloatingEventCard {
        override val key: String = "question:${pending.requestId}"
    }

    data class Completed(val completion: FloatingCompletion) : FloatingEventCard {
        override val key: String = completion.key
    }
}

sealed interface FloatingFeedEvent {
    data class RunsChanged(val runs: List<FloatingRun>) : FloatingFeedEvent

    data class QuestionsChanged(val questions: List<PendingQuestion>) : FloatingFeedEvent

    data class Delivered(
        val sessionId: String,
        val items: List<PresentedItem>,
    ) : FloatingFeedEvent

    data class Completed(
        val sessionId: String,
        val title: String,
        val status: RunStatus,
        val text: String,
        val error: String?,
    ) : FloatingFeedEvent

    data class DismissCard(val key: String) : FloatingFeedEvent

    data class RemoveCompleted(val key: String) : FloatingFeedEvent
}

fun reduceFloatingFeed(
    state: FloatingFeedState,
    event: FloatingFeedEvent,
    now: Long,
): FloatingFeedState = when (event) {
    is FloatingFeedEvent.RunsChanged -> {
        val runs = event.runs.sortedBy { it.startedAt }
        val titles = state.sessionTitles + runs
            .filter { it.title.isNotBlank() }
            .associate { it.sessionId to it.title }
        val next = state.copy(runs = runs, sessionTitles = titles)
        if (next.copy(lastEventAt = state.lastEventAt) == state) state else next.copy(lastEventAt = now)
    }

    is FloatingFeedEvent.QuestionsChanged -> {
        val next = state.copy(questions = event.questions)
        if (next.questions.map { it.requestId } == state.questions.map { it.requestId }) {
            state
        } else {
            next.copy(lastEventAt = now)
        }
    }

    is FloatingFeedEvent.Delivered -> {
        if (event.items.isEmpty()) {
            state
        } else {
            val merged = state.stagedDeliveries[event.sessionId].orEmpty() + event.items
            state.copy(
                stagedDeliveries = state.stagedDeliveries + (event.sessionId to merged.distinctBy { it.deliveryKey() }),
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.Completed -> {
        val completion = FloatingCompletion(
            sessionId = event.sessionId,
            title = event.title,
            status = event.status,
            text = event.text,
            error = event.error,
            finishedAt = now,
            items = state.stagedDeliveries[event.sessionId].orEmpty(),
        )
        val completed = (listOf(completion) + state.completed.filterNot { it.sessionId == event.sessionId })
            .take(FLOATING_COMPLETED_LIMIT)
        state.copy(
            completed = completed,
            dismissedCardKeys = liveDismissedKeys(state.dismissedCardKeys, completed),
            stagedDeliveries = state.stagedDeliveries - event.sessionId,
            lastEventAt = now,
        )
    }

    is FloatingFeedEvent.DismissCard -> state.copy(
        dismissedCardKeys = (state.dismissedCardKeys + event.key)
            .toList()
            .takeLast(FLOATING_DISMISSED_LIMIT)
            .toCollection(LinkedHashSet()),
    )

    is FloatingFeedEvent.RemoveCompleted -> {
        val completed = state.completed.filterNot { it.key == event.key }
        state.copy(
            completed = completed,
            dismissedCardKeys = liveDismissedKeys(state.dismissedCardKeys, completed),
        )
    }
}

private fun liveDismissedKeys(
    keys: Set<String>,
    completed: List<FloatingCompletion>,
): Set<String> {
    if (keys.isEmpty()) return keys
    val live = completed.mapTo(HashSet()) { it.key }
    return keys.filterTo(LinkedHashSet()) { it in live }
}

fun floatingVisibleCompletions(state: FloatingFeedState): List<FloatingCompletion> {
    val running = state.runs.mapTo(HashSet()) { it.sessionId }
    return state.completed.filterNot { it.sessionId in running }
}

fun floatingPendingCount(state: FloatingFeedState): Int =
    state.questions.size + floatingVisibleCompletions(state).size

fun floatingWhaleMood(state: FloatingFeedState, now: Long): FloatingWhaleMood = when {
    state.runs.isNotEmpty() -> FloatingWhaleMood.THINKING
    state.questions.isNotEmpty() || floatingVisibleCompletions(state).isNotEmpty() -> FloatingWhaleMood.WAVE
    now - state.lastEventAt >= FLOATING_SLEEPY_AFTER_MS -> FloatingWhaleMood.SLEEPY
    else -> FloatingWhaleMood.IDLE
}

fun floatingEventCard(state: FloatingFeedState): FloatingEventCard? {
    val candidates = buildList {
        state.questions.firstOrNull()?.let { add(FloatingEventCard.Question(it)) }
        floatingVisibleCompletions(state).forEach { add(FloatingEventCard.Completed(it)) }
    }
    return candidates.firstOrNull { it.key !in state.dismissedCardKeys }
}

fun floatingInlineOptions(pending: PendingQuestion): List<QuestionOption>? {
    val question = pending.questions.singleOrNull() ?: return null
    if (question.multiSelect || question.options.isEmpty()) return null
    return question.options
}

data class FloatingElapsed(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

fun floatingElapsed(elapsedMs: Long): FloatingElapsed {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    return FloatingElapsed(
        hours = totalSeconds / 3600L,
        minutes = (totalSeconds % 3600L) / 60L,
        seconds = totalSeconds % 60L,
    )
}

fun deliveryItemLabel(item: PresentedItem): String =
    item.title?.takeIf { it.isNotBlank() } ?: item.name

private fun PresentedItem.deliveryKey(): String =
    id?.let { "creative:$it" } ?: "${type.name}:${path ?: name}"
