package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.ui.chat.PresentedItem

const val FLOATING_COMPLETION_LIFETIME_MS = 6_000L
const val FLOATING_SLEEPY_AFTER_MS = 10 * 60_000L

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

data class FloatingDelivery(
    val sessionId: String,
    val sessionTitle: String,
    val items: List<PresentedItem>,
    val receivedAt: Long,
    val read: Boolean = false,
) {
    val key: String = deliveryKey(sessionId, items)

    val label: String
        get() = items.firstOrNull()?.let(::deliveryItemLabel).orEmpty()
}

data class FloatingCompletion(
    val sessionId: String,
    val title: String,
    val status: RunStatus,
    val text: String,
    val error: String?,
    val finishedAt: Long,
) {
    val key: String = "completion:$sessionId:$finishedAt:$status"
}

data class FloatingFeedState(
    val runs: List<FloatingRun> = emptyList(),
    val questions: List<PendingQuestion> = emptyList(),
    val deliveries: List<FloatingDelivery> = emptyList(),
    val completions: List<FloatingCompletion> = emptyList(),
    val sessionTitles: Map<String, String> = emptyMap(),
    val lastEventAt: Long = 0L,
    val dismissedCardKey: String? = null,
)

sealed interface FloatingEventCard {
    val key: String

    data class Question(val pending: PendingQuestion) : FloatingEventCard {
        override val key: String = "question:${pending.requestId}"
    }

    data class Delivery(val delivery: FloatingDelivery) : FloatingEventCard {
        override val key: String = delivery.key
    }

    data class Completion(val completion: FloatingCompletion) : FloatingEventCard {
        override val key: String = completion.key
    }
}

sealed interface FloatingFeedEvent {
    data class RunsChanged(val runs: List<FloatingRun>) : FloatingFeedEvent

    data class QuestionsChanged(val questions: List<PendingQuestion>) : FloatingFeedEvent

    data class Delivered(
        val sessionId: String,
        val sessionTitle: String,
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

    data class MarkDeliveryRead(val key: String) : FloatingFeedEvent

    data object MarkDeliveriesRead : FloatingFeedEvent

    data class Prune(val now: Long) : FloatingFeedEvent
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
            val delivery = FloatingDelivery(
                sessionId = event.sessionId,
                sessionTitle = event.sessionTitle,
                items = event.items,
                receivedAt = now,
            )
            val exists = state.deliveries.any { it.key == delivery.key }
            if (exists) {
                state
            } else {
                state.copy(deliveries = state.deliveries + delivery, lastEventAt = now)
            }
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
        )
        val next = state.copy(
            completions = (state.completions + completion).filter {
                now - it.finishedAt < FLOATING_COMPLETION_LIFETIME_MS
            },
            lastEventAt = now,
        )
        next
    }

    is FloatingFeedEvent.DismissCard -> state.copy(dismissedCardKey = event.key)

    is FloatingFeedEvent.MarkDeliveryRead -> state.copy(
        deliveries = state.deliveries.map { delivery ->
            if (delivery.key == event.key) delivery.copy(read = true) else delivery
        },
    )

    FloatingFeedEvent.MarkDeliveriesRead -> {
        if (state.deliveries.none { !it.read }) {
            state
        } else {
            state.copy(deliveries = state.deliveries.map { it.copy(read = true) })
        }
    }

    is FloatingFeedEvent.Prune -> {
        val kept = state.completions.filter { event.now - it.finishedAt < FLOATING_COMPLETION_LIFETIME_MS }
        if (kept.size == state.completions.size) state else state.copy(completions = kept)
    }
}

fun floatingUnreadCount(state: FloatingFeedState): Int =
    state.questions.size + state.deliveries.count { !it.read }

fun floatingWhaleMood(state: FloatingFeedState, now: Long): FloatingWhaleMood = when {
    state.runs.isNotEmpty() -> FloatingWhaleMood.THINKING
    state.questions.isNotEmpty() || state.deliveries.any { !it.read } -> FloatingWhaleMood.WAVE
    now - state.lastEventAt >= FLOATING_SLEEPY_AFTER_MS -> FloatingWhaleMood.SLEEPY
    else -> FloatingWhaleMood.IDLE
}

fun floatingEventCard(state: FloatingFeedState, now: Long): FloatingEventCard? {
    val candidates = buildList {
        state.questions.firstOrNull()?.let { add(FloatingEventCard.Question(it)) }
        state.deliveries.firstOrNull { !it.read }?.let { add(FloatingEventCard.Delivery(it)) }
        state.completions
            .filter { now - it.finishedAt < FLOATING_COMPLETION_LIFETIME_MS }
            .maxByOrNull { it.finishedAt }
            ?.let { add(FloatingEventCard.Completion(it)) }
    }
    return candidates.firstOrNull { it.key != state.dismissedCardKey }
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

private fun deliveryKey(sessionId: String, items: List<PresentedItem>): String =
    "delivery:$sessionId:" + items.joinToString(",") { item ->
        if (item.id != null) "creative:${item.id}" else "${item.type.name}:${item.path.orEmpty()}"
    }
