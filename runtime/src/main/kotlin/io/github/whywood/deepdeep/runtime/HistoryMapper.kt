package io.github.whywood.deepdeep.runtime

import dev.pi.ai.AssistantMessage as PiAssistantMessage
import dev.pi.ai.BlockContent
import dev.pi.ai.PlainTextContent
import dev.pi.ai.StopReason
import dev.pi.ai.TextContent
import dev.pi.ai.ThinkingContent
import dev.pi.ai.ToolCall
import dev.pi.ai.ToolResultMessage
import dev.pi.ai.UserMessage
import dev.pi.coding.session.BranchSummaryEntry
import dev.pi.coding.session.CompactionEntry
import dev.pi.coding.session.SessionEntry
import dev.pi.coding.session.SessionMessageEntry
import kotlinx.serialization.json.JsonObject

private const val REMINDER_PREFIX = "[定时提醒]"

object HistoryMapper {
    fun mapEntries(entries: List<SessionEntry>): List<AssistantMessage> {
        val result = mutableListOf<AssistantMessage>()
        val traces = mutableMapOf<String, ToolTrace>()
        var current: AssistantMessage? = null
        var messageId = 0L
        var stepId = 0L

        fun flush() {
            current?.let { message ->
                if (message.text.isNotBlank() || message.steps.isNotEmpty() || message.status == MessageStatus.FAILED) {
                    result += message
                }
            }
            current = null
        }

        for (entry in entries) {
            when (entry) {
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
                                        arguments = content.arguments.toStringMap(),
                                        toolCallId = content.id,
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
                            error = message.errorMessage?.takeIf { it.isNotBlank() } ?: base.error,
                        )
                    }

                    is ToolResultMessage -> {
                        val output = message.content.filterIsInstance<TextContent>().joinToString("") { it.text }
                        val existing = traces[message.toolCallId]
                        val base = current ?: AssistantMessage(
                            id = messageId++,
                            role = AssistantRole.ASSISTANT,
                            text = "",
                            status = MessageStatus.DONE,
                            startedAt = message.timestamp,
                        )
                        if (existing == null) {
                            val trace = ToolTrace(
                                id = stepId++,
                                name = message.toolName,
                                arguments = emptyMap(),
                                status = if (message.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                                output = output.takeIf { it.isNotBlank() && !message.isError },
                                error = output.takeIf { it.isNotBlank() && message.isError },
                                toolCallId = message.toolCallId,
                            )
                            traces[message.toolCallId] = trace
                            current = base.copy(
                                steps = base.steps + AssistantStep.Tool(trace),
                                finishedAt = message.timestamp,
                            )
                        } else {
                            val updated = existing.copy(
                                status = if (message.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                                output = output.takeIf { it.isNotBlank() && !message.isError },
                                error = output.takeIf { it.isNotBlank() && message.isError },
                            )
                            traces[message.toolCallId] = updated
                            current = base.copy(
                                steps = base.steps.map { step ->
                                    if (step is AssistantStep.Tool && step.trace.toolCallId == message.toolCallId) {
                                        AssistantStep.Tool(updated)
                                    } else {
                                        step
                                    }
                                },
                                finishedAt = message.timestamp,
                            )
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

    private fun dev.pi.ai.MessageContent.asText(): String = when (this) {
        is PlainTextContent -> text
        is BlockContent -> blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
    }

    private fun JsonObject.toStringMap(): Map<String, String> = entries.associate { (key, value) ->
        key to value.toString().trim('"')
    }
}
