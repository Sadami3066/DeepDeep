package io.github.whywood.deepdeep.data.session

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SessionMarkdownLabels(
    val untitled: String,
    val updatedAt: String,
    val messageCount: String,
    val user: String,
    val assistant: String,
    val reasoning: String,
    val tool: String,
    val running: String,
    val done: String,
    val failed: String,
    val stopped: String,
    val error: String,
)

object SessionMarkdown {
    private val timestamps = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun render(
        title: String,
        updatedAt: Long,
        messages: List<AssistantMessage>,
        labels: SessionMarkdownLabels,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val builder = StringBuilder()
        builder.append("# ").append(title.trim().ifBlank { labels.untitled }).append("\n\n")
        builder.append("- ").append(labels.updatedAt)
            .append(timestamps.withZone(zoneId).format(Instant.ofEpochMilli(updatedAt))).append('\n')
        builder.append("- ").append(labels.messageCount).append(messages.size).append("\n\n")
        messages.forEach { message -> builder.appendMessage(message, labels) }
        return builder.toString().trimEnd() + "\n"
    }

    private fun StringBuilder.appendMessage(message: AssistantMessage, labels: SessionMarkdownLabels) {
        append("## ").append(if (message.role == AssistantRole.USER) labels.user else labels.assistant)
        append("\n\n")
        val text = message.text.trim()
        if (text.isNotEmpty()) append(text).append("\n\n")
        message.steps.forEach { step ->
            when (step) {
                is AssistantStep.Text -> {
                    val stepText = step.text.trim()
                    if (stepText.isNotEmpty()) append(stepText).append("\n\n")
                }
                is AssistantStep.Reasoning -> {
                    val thinking = step.text.trim()
                    if (thinking.isNotEmpty()) appendDetails(labels.reasoning, thinking)
                }
                is AssistantStep.Tool -> appendTool(step.trace, labels)
            }
        }
        when (message.status) {
            MessageStatus.FAILED -> {
                val detail = message.rawError?.trim()?.takeIf { it.isNotEmpty() }
                    ?: message.error?.trim()?.takeIf { it.isNotEmpty() }
                append("> ").append(labels.error)
                if (detail != null) append(detail)
                append("\n\n")
            }
            MessageStatus.STOPPED -> append("> ").append(labels.stopped).append("\n\n")
            MessageStatus.RUNNING, MessageStatus.DONE -> Unit
        }
    }

    private fun StringBuilder.appendDetails(summary: String, body: String) {
        append("<details><summary>").append(summary).append("</summary>\n\n")
        append(body).append("\n\n</details>\n\n")
    }

    private fun StringBuilder.appendTool(trace: ToolTrace, labels: SessionMarkdownLabels) {
        append("<details><summary>")
        append(labels.tool).append(" ").append(trace.name).append(" · ").append(toolStatus(trace.status, labels))
        append("</summary>\n\n")
        if (trace.arguments.isNotEmpty()) {
            append("```json\n").append(argumentsJson(trace.arguments)).append("\n```\n\n")
        }
        val output = trace.output?.trim().orEmpty()
        if (output.isNotEmpty()) appendCode(output)
        val error = trace.error?.trim().orEmpty()
        if (error.isNotEmpty()) append("> ").append(labels.error).append(error).append("\n\n")
        append("</details>\n\n")
    }

    private fun StringBuilder.appendCode(text: String) {
        val fence = if (text.contains("````")) "`````" else "````"
        append(fence).append("text\n").append(text).append('\n').append(fence).append("\n\n")
    }

    private fun toolStatus(status: ToolActivityStatus, labels: SessionMarkdownLabels): String = when (status) {
        ToolActivityStatus.RUNNING -> labels.running
        ToolActivityStatus.DONE -> labels.done
        ToolActivityStatus.FAILED -> labels.failed
    }

    private fun argumentsJson(arguments: Map<String, String>): String =
        arguments.entries.joinToString(",\n", prefix = "{\n", postfix = "\n}") { (key, value) ->
            "  " + quoted(key) + ": " + quoted(value)
        }

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
