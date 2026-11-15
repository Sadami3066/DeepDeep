package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.invocationKey

internal const val TodoWriteCustomType = "todo_write"

private val HiddenCustomCardTypes = setOf(TodoWriteCustomType)

internal sealed interface AnswerCardSource {
    data class Custom(val step: AssistantStep.CustomCard) : AnswerCardSource

    data class Tool(val trace: ToolTrace) : AnswerCardSource

    data class Host(val card: HostAnswerCard) : AnswerCardSource
}

internal sealed interface HostAnswerCard {
    data class Present(val traceId: Long, val items: List<PresentedItem>) : HostAnswerCard

    data class Miniapp(val traceId: Long, val app: MiniappCardApp) : HostAnswerCard

    data class AnsweredQuestion(val toolCallId: String, val record: QuestionRecord) : HostAnswerCard
}

internal sealed interface TurnBlock {
    val key: String

    data class Activity(val steps: List<AssistantStep>) : TurnBlock {
        override val key: String get() = "activity-${steps.firstOrNull()?.id ?: LIVE_KEY}"
    }

    data class Text(val step: AssistantStep.Text) : TurnBlock {
        override val key: String get() = "text-${step.id}"
    }

    data class Card(val source: AnswerCardSource) : TurnBlock {
        override val key: String
            get() = when (val source = source) {
                is AnswerCardSource.Custom -> "card-${source.step.id}"
                is AnswerCardSource.Tool -> "card-${source.trace.invocationKey}"
                is AnswerCardSource.Host -> when (val card = source.card) {
                    is HostAnswerCard.Present -> "present-${card.traceId}"
                    is HostAnswerCard.Miniapp -> "miniapp-${card.traceId}"
                    is HostAnswerCard.AnsweredQuestion -> "answered-${card.toolCallId}"
                }
            }
    }

    data class LiveQuestion(val question: PendingQuestion) : TurnBlock {
        override val key: String get() = "live-${question.requestId}"
    }
}

internal fun turnBlocks(
    message: AssistantMessage,
    pendingQuestions: List<PendingQuestion> = emptyList(),
): List<TurnBlock> {
    val blocks = mutableListOf<TurnBlock>()
    val steps = message.steps.filterNot { step ->
        step is AssistantStep.CustomCard && step.customType in HiddenCustomCardTypes
    }
    val trailingText = steps.takeLastWhile { it is AssistantStep.Text }
    val body = steps.dropLast(trailingText.size)
    val activity = mutableListOf<AssistantStep>()

    fun flushActivity() {
        if (activity.isEmpty()) return
        blocks += TurnBlock.Activity(activity.toList())
        activity.clear()
    }

    fun addContent(step: AssistantStep, block: TurnBlock) {
        activity += step
        flushActivity()
        blocks += block
    }

    body.forEach { step ->
        when {
            step is AssistantStep.CustomCard -> {
                flushActivity()
                blocks += TurnBlock.Card(AnswerCardSource.Custom(step))
            }

            step is AssistantStep.Tool && step.trace.answerCard -> {
                addContent(step, TurnBlock.Card(AnswerCardSource.Tool(step.trace)))
            }

            else -> {
                val artifact = toolBlock(step)
                if (artifact == null) {
                    activity += step
                } else {
                    addContent(step, artifact)
                }
            }
        }
    }
    flushActivity()
    trailingText.forEach { step -> blocks += TurnBlock.Text(step as AssistantStep.Text) }
    if (blocks.isEmpty() && message.streaming) {
        blocks += TurnBlock.Activity(emptyList())
    }
    liveQuestion(message, pendingQuestions)?.let { question ->
        val owner = blocks.indexOfLast { block ->
            block is TurnBlock.Activity && block.steps.any { step ->
                step is AssistantStep.Tool && step.trace.toolCallId == question.toolCallId
            }
        }
        blocks.add(if (owner >= 0) owner + 1 else blocks.size, TurnBlock.LiveQuestion(question))
    }
    return blocks
}

internal fun answerText(message: AssistantMessage, blocks: List<TurnBlock>): String =
    message.text.trim().ifBlank {
        blocks.filterIsInstance<TurnBlock.Text>().joinToString("") { it.step.text }.trim()
    }

internal fun latestTodoPayload(messages: List<AssistantMessage>): String? {
    messages.asReversed().forEach { message ->
        message.steps.asReversed().forEach { step ->
            if (step is AssistantStep.CustomCard && step.customType == TodoWriteCustomType) {
                return step.payload
            }
        }
        if (message.customType == TodoWriteCustomType) return message.customPayload
    }
    return null
}

private fun toolBlock(step: AssistantStep): TurnBlock? {
    val trace = (step as? AssistantStep.Tool)?.trace ?: return null
    if (trace.name == "present") {
        val items = parsePresentedItems(trace) ?: return null
        return TurnBlock.Card(AnswerCardSource.Host(HostAnswerCard.Present(trace.id, items)))
    }
    if (trace.name == "miniapp_load" || trace.name == "miniapp_register") {
        val app = parseRegisteredMiniapp(trace) ?: return null
        return TurnBlock.Card(AnswerCardSource.Host(HostAnswerCard.Miniapp(trace.id, app)))
    }
    if (trace.name == "ask_user_question" && trace.status == ToolActivityStatus.DONE) {
        val record = parseQuestionRecord(trace) ?: return null
        return TurnBlock.Card(AnswerCardSource.Host(HostAnswerCard.AnsweredQuestion(trace.toolCallId, record)))
    }
    return null
}

private fun liveQuestion(
    message: AssistantMessage,
    pendingQuestions: List<PendingQuestion>,
): PendingQuestion? = pendingQuestions.firstOrNull { pending ->
    message.steps.any { step ->
        step is AssistantStep.Tool &&
            step.trace.name == "ask_user_question" &&
            step.trace.toolCallId == pending.toolCallId
    }
}

private const val LIVE_KEY = "live"
