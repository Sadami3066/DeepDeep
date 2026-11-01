package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity

internal fun ConversationState.beginTurn(prompt: String, now: Long): ConversationState {
    val nextId = nextMessageId(messages)
    return copy(
        messages = messages + AssistantMessage(nextId, AssistantRole.USER, prompt) + AssistantMessage(
            id = nextId + 1,
            role = AssistantRole.ASSISTANT,
            text = "",
            streaming = true,
            status = MessageStatus.RUNNING,
            startedAt = now,
        ),
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = now,
    )
}

internal fun ConversationState.toggleStep(messageId: Long, stepId: Long): ConversationState = copy(
    messages = messages.map { message ->
        if (message.id != messageId) return@map message
        message.copy(
            steps = message.steps.map { step ->
                when {
                    step is AssistantStep.Reasoning && step.id == stepId ->
                        step.copy(expanded = !step.expanded)

                    step is AssistantStep.Tool && step.trace.id == stepId ->
                        step.copy(trace = step.trace.copy(expanded = !step.trace.expanded))

                    else -> step
                }
            },
        )
    },
)

internal fun ConversationState.retryExternallyStartedRun(runId: String, startedAt: Long): ConversationState {
    val retried = markRetryingTail(startedAt)
    val index = retried.messages.indexOfLast { it.role == AssistantRole.ASSISTANT && it.streaming }
    if (index < 0) return retried
    val updated = retried.messages.toMutableList().also { list -> list[index] = list[index].copy(runId = runId) }
    return retried.copy(messages = updated)
}

internal fun ConversationState.markRetryScheduled(attempt: Int, now: Long): ConversationState {
    val index = messages.indexOfLast {
        it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.FAILED
    }
    val updated = if (index < 0) {
        messages
    } else {
        messages.toMutableList().also { list -> list[index] = list[index].copy(retrying = true) }
    }
    return copy(
        messages = updated,
        sending = true,
        activity = RuntimeActivity.Retrying(attempt),
        activityStartedAt = now,
    )
}

internal fun ConversationState.markRetryingTail(now: Long): ConversationState {
    val index = messages.indexOfLast {
        it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.FAILED
    }
    val updated = if (index < 0) {
        messages + AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.ASSISTANT,
            text = "",
            streaming = true,
            status = MessageStatus.RUNNING,
            startedAt = now,
        )
    } else {
        messages.take(index) + messages[index].copy(
            streaming = true,
            status = MessageStatus.RUNNING,
            retrying = true,
            runId = "",
            finishedAt = null,
        )
    }
    return copy(
        messages = updated,
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = now,
    )
}

internal fun ConversationState.cancelRetry(now: Long): ConversationState {
    val index = messages.indexOfLast { it.retrying }
    val updated = if (index < 0) {
        messages
    } else {
        messages.toMutableList().also { list -> list[index] = list[index].copy(retrying = false) }
    }
    return copy(
        messages = updated,
        sending = false,
        activity = null,
        activityStartedAt = null,
    )
}
