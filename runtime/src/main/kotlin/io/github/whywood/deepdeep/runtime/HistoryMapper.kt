package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.AssistantMessage as PiAssistantMessage
import io.github.whywood.deepdeep.sdk.ai.BlockContent
import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ThinkingContent
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.ToolResultMessage
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.coding.session.BranchSummaryEntry
import io.github.whywood.deepdeep.sdk.coding.session.CompactionEntry
import io.github.whywood.deepdeep.sdk.coding.session.CustomMessageEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionMessageEntry
import kotlinx.serialization.json.JsonObject

private const val REMINDER_PREFIX = "[定时提醒]"

object HistoryMapper {
    fun mapEntries(entries: List<SessionEntry>): List<AssistantMessage> {
        val result = mutableListOf<AssistantMessage>()
        val traces = mutableMapOf<String, ToolTrace>()
        val toolOwners = mutableMapOf<String, Int>()
        var current: AssistantMessage? = null
        var messageId = 0L
        var stepId = 0L

        fun flush() {
            val message = current ?: return
            if (message.text.isNotBlank() || message.steps.isNotEmpty() || message.status == MessageStatus.FAILED) {
                val index = result.size
                result += message
                message.steps.forEach { step ->
                    if (step is AssistantStep.Tool) toolOwners[step.trace.toolCallId] = index
                }
            }
            current = null
        }

        for (entry in entries) {
            when (entry) {
                is CustomMessageEntry -> {
                    if (!entry.display) continue
                    flush()
                    val text = when (val content = entry.content) {
                        is PlainTextContent -> content.text
                        is BlockContent -> content.asText()
                    }
                    result += AssistantMessage(
                        id = messageId++,
                        role = AssistantRole.ASSISTANT,
                        text = text,
                        status = MessageStatus.DONE,
                        customType = entry.customType,
                        customPayload = entry.details?.toString(),
                    )
                }

                is SessionMessageEntry -> when (val message = entry.message) {
                    is UserMessage -> {
                        flush()
                        val text = message.content.asText()
                        if (text.isNotBlank()) {
                            val reminder = parseReminder(text)
                            if (reminder == null) {
                                result += AssistantMessage(messageId++, AssistantRole.USER, text)
                            } else {
                                result += AssistantMessage(
                                    id = messageId++,
                                    role = AssistantRole.USER,
                                    text = reminder.body,
                                    reminder = reminder.name,
                                )
                            }
                        }
                    }

                    is PiAssistantMessage -> {
                        val base = current ?: AssistantMessage(
                            id = messageId++,
                            role = AssistantRole.ASSISTANT,
                            text = "",
                            status = MessageStatus.DONE,
                            startedAt = message.timestamp,
                        )
                        val newSteps = mutableListOf<AssistantStep>()
                        var lastToolIndex = -1
                        for (content in message.content) {
                            when (content) {
                                is ThinkingContent -> {
                                    if (content.thinking.isNotBlank()) {
                                        newSteps += AssistantStep.Reasoning(stepId++, content.thinking)
                                    }
                                }

                                is TextContent -> {
                                    if (content.text.isNotBlank()) {
                                        newSteps += AssistantStep.Text(stepId++, content.text)
                                    }
                                }

                                is ToolCall -> {
                                    val trace = ToolTrace(
                                        id = stepId++,
                                        name = content.name,
                                        arguments = content.arguments.toDisplayArguments(),
                                        toolCallId = content.id,
                                        rawArguments = content.arguments,
                                    )
                                    traces[content.id] = trace
                                    newSteps += AssistantStep.Tool(trace)
                                    lastToolIndex = newSteps.lastIndex
                                }

                                else -> Unit
                            }
                        }
                        val finalText = if (lastToolIndex >= 0) {
                            newSteps.drop(lastToolIndex + 1).filterIsInstance<AssistantStep.Text>().joinToString("") { it.text }
                        } else {
                            newSteps.filterIsInstance<AssistantStep.Text>().joinToString("") { it.text }
                        }
                        val processSteps = if (lastToolIndex >= 0) {
                            newSteps.filterIndexed { index, step -> !(step is AssistantStep.Text && index > lastToolIndex) }
                        } else {
                            newSteps.filterNot { it is AssistantStep.Text }
                        }
                        current = base.copy(
                            text = finalText.ifBlank { base.text },
                            steps = base.steps + processSteps,
                            startedAt = base.startedAt.takeIf { it > 0 } ?: message.timestamp,
                            finishedAt = message.timestamp,
                            status = turnStatus(base.status, message.stopReason),
                            rawError = message.errorMessage?.takeIf { it.isNotBlank() } ?: base.rawError,
                        )
                    }

                    is ToolResultMessage -> {
                        val payload = message.toToolResultPayload()
                        val status = if (message.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE
                        val existing = traces[message.toolCallId]
                        if (existing != null) {
                            val updated = existing.copy(
                                status = status,
                                output = payload.text().takeIf { it.isNotBlank() && !message.isError },
                                error = payload.text().takeIf { it.isNotBlank() && message.isError },
                                resultPayload = payload,
                                resultDetails = message.details as? JsonObject,
                                durationMs = message.durationMs ?: existing.durationMs,
                            )
                            traces[message.toolCallId] = updated
                            val messageWithStep = current
                            val ownerIndex = toolOwners[message.toolCallId]
                            when {
                                messageWithStep != null && messageWithStep.hasToolCall(message.toolCallId) ->
                                    current = messageWithStep.replaceTool(updated, message.timestamp)

                                ownerIndex != null -> result[ownerIndex] =
                                    result[ownerIndex].replaceTool(updated, message.timestamp)

                                else -> current = (messageWithStep ?: emptyAssistant(messageId++, message.timestamp))
                                    .appendTool(updated, message.timestamp)
                            }
                        } else {
                            val trace = ToolTrace(
                                id = stepId++,
                                name = message.toolName,
                                arguments = emptyMap(),
                                status = status,
                                output = payload.text().takeIf { it.isNotBlank() && !message.isError },
                                error = payload.text().takeIf { it.isNotBlank() && message.isError },
                                toolCallId = message.toolCallId,
                                resultPayload = payload,
                                resultDetails = message.details as? JsonObject,
                                durationMs = message.durationMs,
                            )
                            traces[message.toolCallId] = trace
                            current = (current ?: emptyAssistant(messageId++, message.timestamp))
                                .appendTool(trace, message.timestamp)
                        }
                    }

                    else -> Unit
                }

                is CompactionEntry -> {
                    flush()
                    result += AssistantMessage(
                        id = messageId++,
                        role = AssistantRole.ASSISTANT,
                        text = "",
                        summary = MessageSummary(
                            kind = MessageSummaryKind.COMPACTION,
                            text = entry.summary,
                            tokensBefore = entry.tokensBefore,
                        ),
                    )
                }

                is BranchSummaryEntry -> {
                    flush()
                    result += AssistantMessage(
                        id = messageId++,
                        role = AssistantRole.ASSISTANT,
                        text = "",
                        summary = MessageSummary(
                            kind = MessageSummaryKind.BRANCH,
                            text = entry.summary,
                            fromId = entry.fromId,
                        ),
                    )
                }

                else -> Unit
            }
        }
        flush()
        return result
    }

    private fun emptyAssistant(id: Long, timestamp: Long): AssistantMessage = AssistantMessage(
        id = id,
        role = AssistantRole.ASSISTANT,
        text = "",
        status = MessageStatus.DONE,
        startedAt = timestamp,
    )

    private fun AssistantMessage.hasToolCall(toolCallId: String): Boolean =
        steps.any { step -> step is AssistantStep.Tool && step.trace.toolCallId == toolCallId }

    private fun AssistantMessage.replaceTool(trace: ToolTrace, timestamp: Long): AssistantMessage = copy(
        steps = steps.map { step ->
            if (step is AssistantStep.Tool && step.trace.toolCallId == trace.toolCallId) {
                AssistantStep.Tool(trace)
            } else {
                step
            }
        },
        finishedAt = finishedAt ?: timestamp,
    )

    private fun AssistantMessage.appendTool(trace: ToolTrace, timestamp: Long): AssistantMessage = copy(
        steps = steps + AssistantStep.Tool(trace),
        finishedAt = finishedAt ?: timestamp,
    )

    private data class ReminderMessage(
        val name: String,
        val body: String,
    )

    private fun parseReminder(text: String): ReminderMessage? {
        if (!text.startsWith(REMINDER_PREFIX)) return null
        val firstLineEnd = text.indexOf('\n')
        if (firstLineEnd < 0) {
            val name = text.removePrefix(REMINDER_PREFIX).trim()
            return if (name.isEmpty()) null else ReminderMessage(name, "")
        }
        val name = text.substring(0, firstLineEnd).removePrefix(REMINDER_PREFIX).trim()
        if (name.isEmpty()) return null
        val rest = text.substring(firstLineEnd + 1)
        val separator = rest.indexOf("\n\n")
        val body = when {
            separator >= 0 -> rest.substring(separator + 2)
            rest.startsWith("\n") -> rest.trimStart('\n')
            else -> rest
        }
        return ReminderMessage(name, body.trim())
    }

    private fun turnStatus(base: MessageStatus, reason: StopReason): MessageStatus = when {
        base == MessageStatus.FAILED || reason == StopReason.Error -> MessageStatus.FAILED
        base == MessageStatus.STOPPED || reason == StopReason.Aborted -> MessageStatus.STOPPED
        else -> MessageStatus.DONE
    }

    private fun io.github.whywood.deepdeep.sdk.ai.MessageContent.asText(): String = when (this) {
        is PlainTextContent -> text
        is BlockContent -> blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
    }
}
