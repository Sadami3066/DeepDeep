package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.BackgroundTurnStarted
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
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
    val queuedInputs: List<QueuedInput> = emptyList(),
)

internal fun reduce(
    state: ConversationState,
    event: RuntimeEvent,
    strings: ConversationStrings,
    now: Long = System.currentTimeMillis(),
): ConversationState = when (event) {
    is RuntimeEvent.RunStarted -> state.onRunStarted(event)
    is RuntimeEvent.ActivityChanged -> state.onActivityChanged(event, now)
    is RuntimeEvent.TextDelta -> state.onTextDelta(event, now)
    is RuntimeEvent.ReasoningDelta -> state.onReasoningDelta(event, now)
    is RuntimeEvent.ToolCallStarted -> state.onToolCallStarted(event, now)
    is RuntimeEvent.ToolStarted -> state.onToolStarted(event, now)
    is RuntimeEvent.ToolFinished -> state.onToolFinished(event)
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

    is RuntimeEvent.PluginCustomMessage -> state.appendCustomMessage(event)
    is RuntimeEvent.TurnInjected -> state.injectTurn(event, now)
    is RuntimeEvent.UserInputAdmitted -> state.admitUserInput(event.text, event.runId, now)
    is RuntimeEvent.QueueChanged -> state.onQueueChanged(event)
    is RuntimeEvent.QueuedInputsDropped -> state.onQueuedInputsDropped()
    is NoticeEvent -> state.onNotice(event.notice, now)
    is BackgroundTurnStarted -> state.startBackgroundTurn(event, now)
    is UsageChanged -> state.copy(usage = event.usage)
    is RuntimeEvent.SessionListChanged -> state
    is RuntimeEvent.SessionRemoved -> state
    is RuntimeEvent.SessionArchived -> state
}

internal fun ConversationState.onNotice(
    notice: TurnNotice,
    now: Long = System.currentTimeMillis(),
): ConversationState {
    if (notice !is TurnNotice.JobFinished) return appendNotice(notice, now)
    val index = messages.indexOfLast {
        it.role == AssistantRole.ASSISTANT && it.streaming
    }
    if (index < 0) return appendNotice(notice, now)
    val message = messages[index]
    val step = AssistantStep.Notice(nextStepId(message.steps), notice)
    return copy(
        messages = messages.toMutableList().also {
            it[index] = message.copy(steps = message.steps + step)
        },
    )
}

internal fun nextMessageId(messages: List<AssistantMessage>): Long =
    (messages.maxOfOrNull { it.id } ?: -1L) + 1

internal fun nextStepId(steps: List<AssistantStep>): Long =
    (steps.maxOfOrNull { it.id } ?: -1L) + 1

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

internal fun failRunningTools(steps: List<AssistantStep>): List<AssistantStep> = steps.map { step ->
    if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
        step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
    } else {
        step
    }
}

private const val REMINDER_HEADER = "[定时提醒]"
