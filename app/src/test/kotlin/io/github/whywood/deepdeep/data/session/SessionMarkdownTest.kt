package io.github.whywood.deepdeep.data.session

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMarkdownTest {
    private val labels = SessionMarkdownLabels(
        untitled = "Untitled",
        updatedAt = "Updated: ",
        messageCount = "Messages: ",
        user = "User",
        assistant = "Assistant",
        reasoning = "Thinking",
        tool = "Tool",
        running = "Running",
        done = "Done",
        failed = "Failed",
        stopped = "Stopped",
        error = "Run failed: ",
    )

    @Test
    fun rendersHeaderAndMessages() {
        val markdown = SessionMarkdown.render(
            title = "Session One",
            updatedAt = 0L,
            messages = listOf(
                message(id = 1, role = AssistantRole.USER, text = "hello"),
                message(id = 2, role = AssistantRole.ASSISTANT, text = "hi there"),
            ),
            labels = labels,
            zoneId = ZoneId.of("UTC"),
        )
        assertTrue(markdown.startsWith("# Session One\n\n"))
        assertTrue(markdown.contains("- Updated: 1970-01-01 00:00\n"))
        assertTrue(markdown.contains("- Messages: 2\n"))
        assertTrue(markdown.contains("## User\n\nhello\n"))
        assertTrue(markdown.contains("## Assistant\n\nhi there\n"))
    }

    @Test
    fun rendersReasoningAndToolSteps() {
        val tool = ToolTrace(
            id = 7L,
            name = "bash",
            arguments = mapOf("command" to "echo \"hi\""),
            status = ToolActivityStatus.DONE,
            output = "hi\nthere",
        )
        val markdown = SessionMarkdown.render(
            title = "Steps",
            updatedAt = 1L,
            messages = listOf(
                message(
                    id = 5,
                    role = AssistantRole.ASSISTANT,
                    text = "final answer",
                    steps = listOf(
                        AssistantStep.Reasoning(id = 1L, text = "think"),
                        AssistantStep.Tool(tool),
                    ),
                ),
            ),
            labels = labels,
            zoneId = ZoneId.of("UTC"),
        )
        assertTrue(markdown.contains("final answer"))
        assertTrue(markdown.contains("<details><summary>Thinking</summary>\n\nthink"))
        assertTrue(markdown.contains("<details><summary>Tool bash · Done</summary>"))
        assertTrue(markdown.contains("\"command\": \"echo \\\"hi\\\"\""))
        assertTrue(markdown.contains("````text\nhi\nthere\n````"))
    }

    @Test
    fun rendersFailedAndStoppedMessages() {
        val markdown = SessionMarkdown.render(
            title = "Status",
            updatedAt = 0L,
            messages = listOf(
                message(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "oops",
                    status = MessageStatus.FAILED,
                    rawError = "boom",
                ),
                message(id = 2, role = AssistantRole.ASSISTANT, text = "cut", status = MessageStatus.STOPPED),
            ),
            labels = labels,
            zoneId = ZoneId.of("UTC"),
        )
        assertTrue(markdown.contains("> Run failed: boom"))
        assertTrue(markdown.contains("> Stopped"))
    }

    @Test
    fun rendersToolError() {
        val tool = ToolTrace(
            id = 1L,
            name = "grep",
            arguments = emptyMap(),
            status = ToolActivityStatus.FAILED,
            error = "not found",
        )
        val markdown = SessionMarkdown.render(
            title = "Tool",
            updatedAt = 0L,
            messages = listOf(
                message(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "done",
                    steps = listOf(AssistantStep.Tool(tool)),
                ),
            ),
            labels = labels,
            zoneId = ZoneId.of("UTC"),
        )
        assertTrue(markdown.contains("<details><summary>Tool grep · Failed</summary>"))
        assertTrue(markdown.contains("> Run failed: not found"))
    }

    @Test
    fun fallsBackToUntitledLabel() {
        val markdown = SessionMarkdown.render("   ", 0L, emptyList(), labels, ZoneId.of("UTC"))
        assertTrue(markdown.startsWith("# Untitled\n\n"))
        assertTrue(markdown.contains("- Messages: 0\n"))
    }

    private fun message(
        id: Long,
        role: AssistantRole,
        text: String,
        steps: List<AssistantStep> = emptyList(),
        status: MessageStatus = MessageStatus.DONE,
        error: String? = null,
        rawError: String? = null,
    ): AssistantMessage = AssistantMessage(
        id = id,
        role = role,
        text = text,
        steps = steps,
        status = status,
        error = error,
        rawError = rawError,
    )
}
