package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.ui.common.chat.PresentedItem

const val FLOATING_SLEEPY_AFTER_MS = 10 * 60_000L
const val FLOATING_COMPLETED_LIMIT = 20
const val FLOATING_DISMISSED_LIMIT = 50

enum class FloatingWhaleMood {
    IDLE,
    THINKING,
    SLEEPY,
    WAITING,
    DONE,
    ALERT,
}

data class FloatingRun(
    val sessionId: String,
    val title: String,
    val activity: RuntimeActivity?,
    val startedAt: Long,
)

data class FloatingSubscription(
    val sessionId: String,
    val title: String,
    val startedAt: Long,
    val activity: RuntimeActivity?,
    val text: String,
    val updatedAt: Long,
    val tool: String? = null,
    val reasoning: String = "",
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
    val subscription: FloatingSubscription? = null,
    val manualSubscription: Boolean = false,
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

    data class Tracking(val subscription: FloatingSubscription) : FloatingEventCard {
        override val key: String = "tracking:${subscription.sessionId}"
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
        val whileForeground: Boolean = false,
    ) : FloatingFeedEvent

    data class DismissCard(val key: String) : FloatingFeedEvent

    data class RemoveCompleted(val key: String) : FloatingFeedEvent

    data class Subscribe(val sessionId: String) : FloatingFeedEvent

    data class TrackingActivity(
        val sessionId: String,
        val activity: RuntimeActivity?,
    ) : FloatingFeedEvent

    data class TrackingText(
        val sessionId: String,
        val text: String,
    ) : FloatingFeedEvent

    data class TrackingReasoning(
        val sessionId: String,
        val text: String,
    ) : FloatingFeedEvent

    data class TrackingTool(
        val sessionId: String,
        val tool: String,
    ) : FloatingFeedEvent

    data class TrackingReset(val sessionId: String) : FloatingFeedEvent
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
        val latest = runs.lastOrNull()
        var manual = state.manualSubscription
        var subscription = state.subscription?.let { current ->
            runs.firstOrNull { it.sessionId == current.sessionId }?.let { run ->
                current.copy(
                    title = run.title.ifBlank { current.title },
                    startedAt = run.startedAt,
                    activity = run.activity,
                )
            }
        }
        val ended = state.subscription != null && subscription == null
        when {
            latest == null -> {
                subscription = null
                manual = false
            }

            ended -> {
                manual = false
                subscription = latest.toSubscription(now)
            }

            subscription == null -> {
                if (!manual) subscription = latest.toSubscription(now)
            }

            !manual && latest.startedAt > subscription.startedAt -> {
                subscription = latest.toSubscription(now)
            }
        }
        val next = state.copy(
            runs = runs,
            sessionTitles = titles,
            subscription = subscription,
            manualSubscription = manual,
        )
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
        val dismissed = (liveDismissedKeys(state.dismissedCardKeys, completed).toList() +
            listOfNotNull(completion.key.takeIf { event.whileForeground }))
            .takeLast(FLOATING_DISMISSED_LIMIT)
            .toCollection(LinkedHashSet())
        state.copy(
            completed = completed,
            dismissedCardKeys = dismissed,
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

    is FloatingFeedEvent.Subscribe -> {
        val current = state.subscription
        if (current?.sessionId == event.sessionId) {
            state.copy(subscription = null, manualSubscription = true)
        } else {
            val run = state.runs.firstOrNull { it.sessionId == event.sessionId }
            state.copy(
                subscription = FloatingSubscription(
                    sessionId = event.sessionId,
                    title = run?.title ?: state.sessionTitles[event.sessionId].orEmpty(),
                    startedAt = run?.startedAt ?: 0L,
                    activity = run?.activity,
                    text = "",
                    updatedAt = now,
                ),
                manualSubscription = true,
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.TrackingActivity -> {
        val current = state.subscription
        if (current == null || current.sessionId != event.sessionId || current.activity == event.activity) {
            state
        } else {
            state.copy(
                subscription = current.copy(activity = event.activity, updatedAt = now),
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.TrackingText -> {
        val current = state.subscription
        if (current == null || current.sessionId != event.sessionId || current.text == event.text) {
            state
        } else {
            state.copy(
                subscription = current.copy(text = event.text, updatedAt = now),
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.TrackingReasoning -> {
        val current = state.subscription
        if (current == null || current.sessionId != event.sessionId || current.reasoning == event.text) {
            state
        } else {
            state.copy(
                subscription = current.copy(reasoning = event.text, updatedAt = now),
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.TrackingTool -> {
        val current = state.subscription
        if (current == null || current.sessionId != event.sessionId || current.tool == event.tool) {
            state
        } else {
            state.copy(
                subscription = current.copy(tool = event.tool, updatedAt = now),
                lastEventAt = now,
            )
        }
    }

    is FloatingFeedEvent.TrackingReset -> {
        val current = state.subscription
        if (current == null || current.sessionId != event.sessionId) {
            state
        } else if (current.text.isEmpty() && current.reasoning.isEmpty() && current.tool == null) {
            state
        } else {
            state.copy(
                subscription = current.copy(
                    text = "",
                    reasoning = "",
                    tool = null,
                    updatedAt = now,
                ),
                lastEventAt = now,
            )
        }
    }
}

private fun FloatingRun.toSubscription(now: Long): FloatingSubscription = FloatingSubscription(
    sessionId = sessionId,
    title = title,
    startedAt = startedAt,
    activity = activity,
    text = "",
    updatedAt = now,
)

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
    state.questions.isNotEmpty() -> FloatingWhaleMood.WAITING
    floatingVisibleCompletions(state).isNotEmpty() ->
        if (floatingVisibleCompletions(state).any { it.status == RunStatus.FAILED || it.error != null }) {
            FloatingWhaleMood.ALERT
        } else {
            FloatingWhaleMood.DONE
        }
    now - state.lastEventAt >= FLOATING_SLEEPY_AFTER_MS -> FloatingWhaleMood.SLEEPY
    else -> FloatingWhaleMood.IDLE
}

fun floatingEventQueue(state: FloatingFeedState): List<FloatingEventCard> {
    val candidates = buildList {
        state.questions.forEach { add(FloatingEventCard.Question(it)) }
        floatingVisibleCompletions(state).forEach { add(FloatingEventCard.Completed(it)) }
        state.subscription?.let { add(FloatingEventCard.Tracking(it)) }
    }
    return candidates.filterNot { it.key in state.dismissedCardKeys }
}

fun floatingEventCard(state: FloatingFeedState): FloatingEventCard? = floatingEventQueue(state).firstOrNull()

fun floatingInlineOptions(pending: PendingQuestion): List<QuestionOption>? {
    val question = pending.questions.singleOrNull() ?: return null
    if (question.multiSelect || question.options.isEmpty()) return null
    return question.options
}

const val FLOATING_TRACKED_PREVIEW_LENGTH = 64

fun floatingTrackedPreview(text: String): String = text
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .lastOrNull()
    .orEmpty()
    .takeLast(FLOATING_TRACKED_PREVIEW_LENGTH)
    .trim()

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
    id?.let { "miniapp:$it" } ?: "${type.name}:${path ?: name}"
