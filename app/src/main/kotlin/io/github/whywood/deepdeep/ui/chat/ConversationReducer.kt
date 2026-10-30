package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.BackgroundTurnStarted
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.runtime.UsageChanged

data class ConversationState(
    val messages: List<AssistantMessage> = emptyList(),
    val title: String = "",
    val sending: Boolean = false,
    val activity: RuntimeActivity? = null,
    val activityStartedAt: Long? = null,
    val loaded: Boolean = false,
    val usage: SessionUsage? = null,
    val sessionModel: SessionModelSnapshot? = null,
)

internal fun reduce(
    state: ConversationState,
    event: RuntimeEvent,
    strings: ChatUiStrings,
    now: Long = System.currentTimeMillis(),
): ConversationState = when (event) {
    is RuntimeEvent.RunStarted -> {
        val target = state.messages.lastOrNull { it.role == AssistantRole.ASSISTANT && it.streaming }
        if (target == null) {
            if (state.sending) {
                state.copy(
                    messages = state.messages + AssistantMessage(
                        id = nextMessageId(state.messages),
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
            } else {
                state
            }
        } else {
            state.copy(
                messages = state.messages.map {
                    if (it.id == target.id) it.copy(runId = event.runId) else it
                },
                sending = true,
                activity = RuntimeActivity.Thinking,
                activityStartedAt = event.startedAt,
            )
        }
    }

    is RuntimeEvent.ActivityChanged -> state.onRun(event.runId) {
        copy(
            activity = event.activity,
            activityStartedAt = if (event.activity == null) null else activityStartedAt ?: now,
        )
    }

    is RuntimeEvent.TextDelta -> state.onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            val steps = message.steps.toMutableList()
            val last = steps.lastOrNull()
            if (last is AssistantStep.Text) {
                steps[steps.lastIndex] = last.copy(text = last.text + event.text)
            } else {
                steps += AssistantStep.Text(nextStepId(steps), event.text)
            }
            message.copy(steps = steps).clearFailureOnProgress()
        }
    }

    is RuntimeEvent.ReasoningDelta -> state.onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            val steps = message.steps.toMutableList()
            val last = steps.lastOrNull()
            if (last is AssistantStep.Reasoning) {
                steps[steps.lastIndex] = last.copy(text = last.text + event.text)
            } else {
                steps += AssistantStep.Reasoning(nextStepId(steps), event.text)
            }
            message.copy(steps = steps).clearFailureOnProgress()
        }
    }

    is RuntimeEvent.ToolStarted -> state.onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            message.copy(
                steps = message.steps + AssistantStep.Tool(
                    ToolTrace(
                        id = nextStepId(message.steps),
                        name = event.name,
                        arguments = event.arguments,
                        toolCallId = event.callId,
                        startedAt = event.startedAt,
                    ),
                ),
            ).clearFailureOnProgress()
        }
    }

    is RuntimeEvent.ToolFinished -> state.onRun(event.runId) {
        updateAssistant(event.runId) { message -> message.withToolResult(event) }
    }

    is RuntimeEvent.RunFinished -> state.finishWithStatus(
        runId = event.runId,
        text = event.text,
        status = event.status,
        finishedAt = event.finishedAt,
    )

    is RuntimeEvent.RunFailed -> state.finishWithError(
        runId = event.runId,
        rawError = event.message,
        strings = strings,
        now = now,
    )

    is RuntimeEvent.TurnInjected -> state.injectTurn(event, now)

    is RuntimeEvent.UserInputAdmitted -> state.admitUserInput(event.text, event.runId, now)

    is RuntimeEvent.QueueChanged -> state
    is RuntimeEvent.QueuedInputsDropped -> state

    is NoticeEvent -> state.appendNotice(event.notice, now)
    is BackgroundTurnStarted -> state.startBackgroundTurn(event, now)
    is UsageChanged -> state.copy(usage = event.usage)

    is RuntimeEvent.SessionListChanged -> state
    is RuntimeEvent.SessionRemoved -> state
    is RuntimeEvent.SessionArchived -> state
}

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
    strings: ChatUiStrings,
    now: Long = System.currentTimeMillis(),
): ConversationState {
    val index = finishTargetIndex(runId)
    if (index == -1) return copy(sending = false, activity = null, activityStartedAt = null)
    val current = messages[index]
    val failed = current.copy(
        text = current.text,
        streaming = false,
        retrying = false,
        status = MessageStatus.FAILED,
        finishedAt = now,
        error = strings.runError(rawError),
        steps = failRunningTools(current.steps),
    )
    return copy(
        messages = replaceAndSettle(index, failed),
        sending = false,
        activity = null,
        activityStartedAt = null,
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

internal fun ConversationState.updateAssistant(
    runId: String,
    transform: (AssistantMessage) -> AssistantMessage,
): ConversationState {
    val index = messages.indexOfLast { it.runId == runId }
    if (index == -1) return this
    val target = messages[index]
    return copy(messages = messages.toMutableList().also { it[index] = transform(target) })
}

internal fun ConversationState.matchesRun(runId: String): Boolean =
    messages.any { it.runId == runId }

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

internal fun nextMessageId(messages: List<AssistantMessage>): Long =
    (messages.maxOfOrNull { it.id } ?: -1L) + 1

internal fun nextStepId(steps: List<AssistantStep>): Long =
    (steps.maxOfOrNull { it.id } ?: -1L) + 1

internal fun consumeFinalText(steps: List<AssistantStep>, finalText: String): List<AssistantStep> {
    val final = finalText.trim()
    if (final.isEmpty()) return steps
    val last = steps.lastOrNull() as? AssistantStep.Text ?: return steps
    val candidate = last.text.trim()
    return if (candidate.isNotEmpty() && (final == candidate || final.endsWith(candidate))) {
        steps.dropLast(1)
    } else {
        steps
    }
}

internal fun reminderDisplayText(text: String): String {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith(REMINDER_HEADER)) return text
    val blankLine = trimmed.indexOf("\n\n")
    return if (blankLine >= 0) {
        trimmed.substring(blankLine + 2).trimStart('\n')
    } else {
        trimmed.substringAfter('\n', "").trimStart('\n')
    }
}

private fun AssistantMessage.clearFailureOnProgress(): AssistantMessage = if (retrying) {
    this
} else {
    copy(
        error = null,
        status = if (status == MessageStatus.FAILED) MessageStatus.RUNNING else status,
    )
}

private fun ConversationState.onRun(
    runId: String,
    transform: ConversationState.() -> ConversationState,
): ConversationState = if (matchesRun(runId)) transform() else this

private fun AssistantMessage.withToolResult(event: RuntimeEvent.ToolFinished): AssistantMessage {
    val index = steps.indexOfLast { step ->
        step is AssistantStep.Tool && step.trace.toolCallId == event.callId
    }
    val status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE
    val output = event.result.takeIf { !event.isError && it.isNotBlank() }
    val error = event.result.takeIf { event.isError && it.isNotBlank() }
    if (index == -1) {
        return copy(
            steps = steps + AssistantStep.Tool(
                ToolTrace(
                    id = nextStepId(steps),
                    name = event.name,
                    arguments = emptyMap(),
                    status = status,
                    output = output,
                    error = error,
                    toolCallId = event.callId,
                    durationMs = event.durationMs,
                ),
            ),
        )
    }
    val trace = (steps[index] as AssistantStep.Tool).trace.copy(
        status = status,
        output = output,
        error = error,
        durationMs = event.durationMs,
    )
    return copy(steps = steps.toMutableList().also { it[index] = AssistantStep.Tool(trace) })
}

private fun failRunningTools(steps: List<AssistantStep>): List<AssistantStep> = steps.map { step ->
    if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
        step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
    } else {
        step
    }
}

private fun ConversationState.injectTurn(
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

private fun ConversationState.startBackgroundTurn(
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

private const val REMINDER_HEADER = "[定时提醒]"
