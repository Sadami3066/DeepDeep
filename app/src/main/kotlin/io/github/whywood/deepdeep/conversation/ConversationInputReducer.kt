package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.TurnNotice

internal fun ConversationState.admitUserInput(
    text: String,
    runId: String,
    now: Long,
): ConversationState {
    if (runId.isNotBlank() && messages.none { it.runId == runId }) return this
    val settled = messages.toMutableList()
    val streamingIndex = settled.indexOfLast {
        it.role == AssistantRole.ASSISTANT && it.streaming && (runId.isBlank() || it.runId == runId)
    }.takeIf { it >= 0 } ?: settled.indexOfLast { it.role == AssistantRole.ASSISTANT && it.streaming }
    if (streamingIndex >= 0) {
        val streaming = settled[streamingIndex]
        settled[streamingIndex] = streaming.copy(
            streaming = false,
            retrying = false,
            status = if (streaming.status == MessageStatus.RUNNING) MessageStatus.DONE else streaming.status,
            finishedAt = now,
            steps = failRunningTools(streaming.steps),
        )
    }
    val userId = nextMessageId(settled)
    settled += AssistantMessage(
        id = userId,
        role = AssistantRole.USER,
        text = text,
    )
    settled += AssistantMessage(
        id = userId + 1,
        role = AssistantRole.ASSISTANT,
        text = "",
        streaming = true,
        status = MessageStatus.RUNNING,
        startedAt = now,
        runId = runId,
    )
    return copy(
        messages = settled,
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = now,
    )
}

internal fun ConversationState.onQueueChanged(event: RuntimeEvent.QueueChanged): ConversationState = copy(
    queuedInputs = event.steering.map { QueuedInput(InputQueueKind.STEER, it) } +
        event.followUp.map { QueuedInput(InputQueueKind.FOLLOW_UP, it) },
)

internal fun ConversationState.onQueuedInputsDropped(): ConversationState = copy(queuedInputs = emptyList())

internal fun ConversationState.injectTurn(
    event: RuntimeEvent.TurnInjected,
    now: Long,
): ConversationState {
    val display = reminderDisplayText(event.text)
    val userId = nextMessageId(messages)
    val user = AssistantMessage(
        id = userId,
        role = AssistantRole.USER,
        text = display,
        reminder = event.reminder,
        injectedSource = event.source,
    )
    val streaming = messages.lastOrNull { it.role == AssistantRole.ASSISTANT && it.streaming }
    if (streaming != null) {
        return copy(messages = messages + user)
    }
    val assistant = AssistantMessage(
        id = userId + 1,
        role = AssistantRole.ASSISTANT,
        text = "",
        streaming = true,
        status = MessageStatus.RUNNING,
        startedAt = now,
        runId = event.runId,
    )
    return copy(
        messages = messages + user + assistant,
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = activityStartedAt ?: now,
    )
}

internal fun ConversationState.appendNotice(
    notice: TurnNotice,
    now: Long = System.currentTimeMillis(),
): ConversationState {
    val message = AssistantMessage(
        id = nextMessageId(messages),
        role = AssistantRole.ASSISTANT,
        text = "",
        status = MessageStatus.DONE,
        startedAt = now,
        finishedAt = now,
        notice = notice,
    )
    return copy(messages = messages + message)
}
