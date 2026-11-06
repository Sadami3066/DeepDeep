package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.BackgroundTurnStarted
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus

internal fun ConversationState.onRunStarted(event: RuntimeEvent.RunStarted): ConversationState {
    val known = event.runId.isNotBlank() && messages.any { it.runId == event.runId }
    val target = messages.lastOrNull { it.role == AssistantRole.ASSISTANT && it.streaming }
    return when {
        known -> this

        target != null -> copy(
            messages = messages.map {
                if (it.id == target.id) it.copy(runId = event.runId) else it
            },
            sending = true,
            activity = RuntimeActivity.Thinking,
            activityStartedAt = event.startedAt,
        )

        event.prompt.isNotEmpty() -> startExternalRun(event)

        retriableFailedTail() -> retryExternallyStartedRun(event.runId, event.startedAt)

        sending -> appendPlaceholder(event)

        else -> this
    }
}

internal fun ConversationState.finishWithStatus(
    runId: String,
    text: String,
    status: RunStatus,
    finishedAt: Long,
): ConversationState {
    val index = finishTargetIndex(runId)
    if (index == -1) return copy(sending = false, activity = null, activityStartedAt = null)
    val message = messages[index]
    val finalText = text.ifBlank { message.text }
    val finalized = message.copy(
        text = finalText,
        streaming = false,
        error = if (status == RunStatus.FAILED) message.error else null,
        rawError = if (status == RunStatus.FAILED) message.rawError else null,
        status = when (status) {
            RunStatus.DONE -> MessageStatus.DONE
            RunStatus.STOPPED -> MessageStatus.STOPPED
            RunStatus.FAILED -> MessageStatus.FAILED
        },
        finishedAt = finishedAt,
        retrying = false,
        steps = consumeFinalText(failRunningTools(message.steps), finalText),
    )
    return copy(
        messages = replaceAndSettle(index, finalized),
        sending = false,
        activity = null,
        activityStartedAt = null,
    )
}

internal fun ConversationState.finishWithError(
    runId: String,
    rawError: String?,
    strings: ConversationStrings,
    now: Long = System.currentTimeMillis(),
): ConversationState {
    val index = finishTargetIndex(runId)
    if (index == -1) return copy(sending = false, activity = null, activityStartedAt = null)
    val current = messages[index]
    val providerError = rawError?.trim()?.takeIf { it.isNotEmpty() }
    val failed = current.copy(
        text = current.text,
        streaming = false,
        retrying = false,
        status = MessageStatus.FAILED,
        finishedAt = now,
        error = strings.runError(providerError),
        rawError = providerError,
        steps = failRunningTools(current.steps),
    )
    return copy(
        messages = replaceAndSettle(index, failed),
        sending = false,
        activity = null,
        activityStartedAt = null,
    )
}

internal fun ConversationState.adoptActiveRun(
    runId: String,
    activity: RuntimeActivity,
    now: Long,
): ConversationState {
    val index = messages.indexOfLast { it.role == AssistantRole.ASSISTANT }
    val updated = if (index >= 0 && index == messages.lastIndex) {
        messages.toMutableList().also { list ->
            list[index] = list[index].copy(
                streaming = true,
                status = MessageStatus.RUNNING,
                finishedAt = null,
                runId = runId,
            )
        }
    } else {
        messages + AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.ASSISTANT,
            text = "",
            streaming = true,
            status = MessageStatus.RUNNING,
            startedAt = now,
            runId = runId,
        )
    }
    return copy(
        messages = updated,
        sending = true,
        activity = activity,
        activityStartedAt = activityStartedAt ?: now,
    )
}

internal fun ConversationState.startBackgroundTurn(
    event: BackgroundTurnStarted,
    now: Long,
): ConversationState {
    val added = event.notice?.let { notice ->
        AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.ASSISTANT,
            text = "",
            status = MessageStatus.DONE,
            startedAt = now,
            finishedAt = now,
            notice = notice,
        )
    }
    val assistantId = nextMessageId(messages + listOfNotNull(added))
    val assistant = AssistantMessage(
        id = assistantId,
        role = AssistantRole.ASSISTANT,
        text = "",
        streaming = true,
        status = MessageStatus.RUNNING,
        startedAt = now,
        runId = event.runId,
    )
    return copy(
        messages = messages + listOfNotNull(added, assistant),
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = activityStartedAt ?: now,
    )
}

private fun ConversationState.finishTargetIndex(runId: String): Int {
    if (runId.isNotBlank()) {
        val matched = messages.indexOfLast { it.runId == runId }
        if (matched >= 0) return matched
    }
    if (!sending) {
        return if (runId.isBlank()) {
            messages.indexOfLast { it.role == AssistantRole.ASSISTANT && it.streaming }
        } else {
            -1
        }
    }
    return messages.indexOfLast {
        it.role == AssistantRole.ASSISTANT && (it.streaming || it.status == MessageStatus.RUNNING)
    }
}

private fun ConversationState.replaceAndSettle(
    index: Int,
    finalized: AssistantMessage,
): List<AssistantMessage> = messages.toMutableList().also { list ->
    for (position in list.indices) {
        list[position] = when {
            position == index -> finalized
            list[position].streaming || list[position].retrying ->
                list[position].copy(streaming = false, retrying = false)

            else -> list[position]
        }
    }
}

private fun ConversationState.retriableFailedTail(): Boolean =
    messages.lastOrNull()?.let {
        it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.FAILED
    } == true

private fun ConversationState.startExternalRun(event: RuntimeEvent.RunStarted): ConversationState {
    val tail = messages.lastOrNull()
    val withUser = if (tail != null && tail.role == AssistantRole.USER && tail.text == event.prompt) {
        messages
    } else {
        messages + AssistantMessage(
            id = nextMessageId(messages),
            role = AssistantRole.USER,
            text = event.prompt,
        )
    }
    return copy(
        messages = withUser + AssistantMessage(
            id = nextMessageId(withUser),
            role = AssistantRole.ASSISTANT,
            text = "",
            streaming = true,
            status = MessageStatus.RUNNING,
            startedAt = event.startedAt,
            runId = event.runId,
        ),
        sending = true,
        activity = RuntimeActivity.Thinking,
        activityStartedAt = event.startedAt,
    )
}

private fun ConversationState.appendPlaceholder(event: RuntimeEvent.RunStarted): ConversationState = copy(
    messages = messages + AssistantMessage(
        id = nextMessageId(messages),
        role = AssistantRole.ASSISTANT,
        text = "",
        streaming = true,
        status = MessageStatus.RUNNING,
        startedAt = event.startedAt,
        runId = event.runId,
    ),
    sending = true,
    activity = RuntimeActivity.Thinking,
    activityStartedAt = event.startedAt,
)
