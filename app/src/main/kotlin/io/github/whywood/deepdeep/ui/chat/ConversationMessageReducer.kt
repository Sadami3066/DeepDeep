package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace

internal fun ConversationState.onActivityChanged(
    event: RuntimeEvent.ActivityChanged,
    now: Long,
): ConversationState = onRun(event.runId) {
    copy(
        activity = event.activity,
        activityStartedAt = if (event.activity == null) null else activityStartedAt ?: now,
    )
}

internal fun ConversationState.onTextDelta(event: RuntimeEvent.TextDelta, now: Long): ConversationState =
    onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            val steps = message.steps.toMutableList()
            val last = steps.lastOrNull()
            if (last is AssistantStep.Text) {
                steps[steps.lastIndex] = last.copy(text = last.text + event.text)
            } else {
                steps += AssistantStep.Text(nextStepId(steps), event.text)
            }
            message.copy(steps = steps).clearFailureOnProgress()
        }.clearRetryActivity(now)
    }

internal fun ConversationState.onReasoningDelta(event: RuntimeEvent.ReasoningDelta, now: Long): ConversationState =
    onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            val steps = message.steps.toMutableList()
            val last = steps.lastOrNull()
            if (last is AssistantStep.Reasoning) {
                steps[steps.lastIndex] = last.copy(text = last.text + event.text)
            } else {
                steps += AssistantStep.Reasoning(nextStepId(steps), event.text)
            }
            message.copy(steps = steps).clearFailureOnProgress()
        }.clearRetryActivity(now)
    }

internal fun ConversationState.onToolStarted(event: RuntimeEvent.ToolStarted, now: Long): ConversationState =
    onRun(event.runId) {
        updateAssistant(event.runId) { message ->
            message.copy(
                steps = message.steps + AssistantStep.Tool(
                    ToolTrace(
                        id = nextStepId(message.steps),
                        name = event.name,
                        arguments = event.arguments,
                        rawArguments = event.rawArguments,
                        toolCallId = event.callId,
                        startedAt = event.startedAt,
                    ),
                ),
            ).clearFailureOnProgress()
        }.clearRetryActivity(now)
    }

internal fun ConversationState.onToolFinished(event: RuntimeEvent.ToolFinished): ConversationState =
    onRun(event.runId) {
        updateAssistant(event.runId) { message -> message.withToolResult(event) }
    }

internal fun ConversationState.appendCustomMessage(event: RuntimeEvent.PluginCustomMessage): ConversationState {
    if (!event.display) return this
    val nextId = (messages.maxOfOrNull { it.id } ?: 0L) + 1
    return copy(
        messages = messages + AssistantMessage(
            id = nextId,
            role = AssistantRole.ASSISTANT,
            text = event.text,
            status = MessageStatus.DONE,
            customType = event.customType,
            customPayload = event.payload,
        ),
    )
}

private fun ConversationState.onRun(
    runId: String,
    transform: ConversationState.() -> ConversationState,
): ConversationState = if (matchesRun(runId)) transform() else this

private fun ConversationState.matchesRun(runId: String): Boolean =
    messages.any { it.runId == runId }

private fun AssistantMessage.clearFailureOnProgress(): AssistantMessage = if (retrying) {
    this
} else {
    copy(
        error = null,
        rawError = null,
        status = if (status == MessageStatus.FAILED) MessageStatus.RUNNING else status,
    )
}

private fun ConversationState.updateAssistant(
    runId: String,
    transform: (AssistantMessage) -> AssistantMessage,
): ConversationState {
    val index = messages.indexOfLast { it.runId == runId }
    if (index == -1) return this
    val target = messages[index]
    return copy(messages = messages.toMutableList().also { it[index] = transform(target) })
}

private fun ConversationState.clearRetryActivity(now: Long): ConversationState =
    if (activity is RuntimeActivity.Retrying) {
        copy(activity = RuntimeActivity.Thinking, activityStartedAt = activityStartedAt ?: now)
    } else {
        this
    }

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
                    resultPayload = event.payload,
                ),
            ),
        )
    }
    val trace = (steps[index] as AssistantStep.Tool).trace.copy(
        status = status,
        output = output,
        error = error,
        durationMs = event.durationMs,
        resultPayload = event.payload,
    )
    return copy(steps = steps.toMutableList().also { it[index] = AssistantStep.Tool(trace) })
}
