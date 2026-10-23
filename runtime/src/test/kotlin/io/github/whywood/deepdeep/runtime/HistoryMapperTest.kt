package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentMessage
import dev.pi.agent.StandardAgentMessage
import dev.pi.ai.AssistantMessage as PiAssistantMessage
import dev.pi.ai.Content
import dev.pi.ai.PlainTextContent
import dev.pi.ai.StopReason
import dev.pi.ai.TextContent
import dev.pi.ai.ToolCall
import dev.pi.ai.ToolResultMessage
import dev.pi.ai.Usage
import dev.pi.ai.UserMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMapperTest {
    @Test
    fun errorAssistantMarksTurnFailedWithPartialText() {
        val messages = HistoryMapper.mapHistory(
            listOf(
                user("hi", 1L),
                assistant(
                    content = listOf(TextContent("partial answer")),
                    stopReason = StopReason.Error,
                    errorMessage = "quota exceeded",
                    timestamp = 2L,
                ),
            ),
        )

        assertEquals(2, messages.size)
        val answer = messages[1]
        assertEquals(AssistantRole.ASSISTANT, answer.role)
        assertEquals(MessageStatus.FAILED, answer.status)
        assertEquals("quota exceeded", answer.error)
        assertEquals("partial answer", answer.text)
    }

    @Test
    fun errorAssistantWithoutContentIsStillMapped() {
        val messages = HistoryMapper.mapHistory(
            listOf(
                user("hi", 1L),
                assistant(stopReason = StopReason.Error, errorMessage = "boom", timestamp = 2L),
            ),
        )

        assertEquals(2, messages.size)
        assertEquals(MessageStatus.FAILED, messages[1].status)
        assertEquals("boom", messages[1].error)
        assertEquals("", messages[1].text)
    }

    @Test
    fun abortedAssistantMarksTurnStopped() {
        val messages = HistoryMapper.mapHistory(
            listOf(
                user("hi", 1L),
                assistant(content = listOf(TextContent("half")), stopReason = StopReason.Aborted, timestamp = 2L),
            ),
        )

        assertEquals(2, messages.size)
        assertEquals(MessageStatus.STOPPED, messages[1].status)
        assertNull(messages[1].error)
    }

    @Test
    fun reminderMessageSplitsNameAndBody() {
        val messages = HistoryMapper.mapHistory(
            listOf(user("[定时提醒] 写周报\n\n请提交本周周报", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals("写周报", messages[0].reminder)
        assertEquals("请提交本周周报", messages[0].text)
    }

    @Test
    fun batchReminderKeepsCountAsName() {
        val messages = HistoryMapper.mapHistory(
            listOf(user("[定时提醒] 2 个提醒\n\n1. 写周报\n2. 喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("2 个提醒", messages[0].reminder)
        assertEquals("1. 写周报\n2. 喝水", messages[0].text)
    }

    @Test
    fun reminderFramingSentenceIsHiddenFromBody() {
        val messages = HistoryMapper.mapHistory(
            listOf(user("[定时提醒] 喝水\n这是你设置的定时提醒，现在到时间了。\n\n去喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("喝水", messages[0].reminder)
        assertEquals("去喝水", messages[0].text)
    }

    @Test
    fun reminderWithoutBlankLineKeepsRemainderAsBody() {
        val messages = HistoryMapper.mapHistory(
            listOf(user("[定时提醒] 喝水\n记得喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("喝水", messages[0].reminder)
        assertEquals("记得喝水", messages[0].text)
    }

    @Test
    fun plainUserMessageIsNotTreatedAsReminder() {
        val messages = HistoryMapper.mapHistory(listOf(user("普通消息", 1L)))

        assertEquals(1, messages.size)
        assertNull(messages[0].reminder)
        assertEquals("普通消息", messages[0].text)
        assertEquals(MessageStatus.DONE, messages[0].status)
    }

    @Test
    fun toolTurnAggregatesIntoProcessStepsAndAnswer() {
        val messages = HistoryMapper.mapHistory(
            listOf(
                user("check", 1L),
                assistant(
                    content = listOf(
                        TextContent("Let me check"),
                        toolCall("call-1", "bash"),
                    ),
                    stopReason = StopReason.ToolUse,
                    timestamp = 2L,
                ),
                toolResult("call-1", "ok", timestamp = 3L),
                assistant(content = listOf(TextContent("All done")), timestamp = 4L),
            ),
        )

        assertEquals(2, messages.size)
        val answer = messages[1]
        assertEquals(AssistantRole.ASSISTANT, answer.role)
        assertEquals("All done", answer.text)
        assertEquals(2, answer.steps.size)
        assertTrue(answer.steps[0] is AssistantStep.Text)
        val tool = answer.steps[1] as AssistantStep.Tool
        assertEquals(ToolActivityStatus.DONE, tool.trace.status)
        assertEquals("ok", tool.trace.output)
        assertEquals(MessageStatus.DONE, answer.status)
    }

    private fun user(text: String, timestamp: Long): AgentMessage =
        StandardAgentMessage(UserMessage(PlainTextContent(text), timestamp))

    private fun assistant(
        content: List<Content> = emptyList(),
        stopReason: StopReason = StopReason.Stop,
        errorMessage: String? = null,
        timestamp: Long,
    ): AgentMessage = StandardAgentMessage(
        PiAssistantMessage(
            content = content.toMutableList(),
            api = "",
            provider = "",
            model = "",
            usage = Usage.empty(),
            stopReason = stopReason,
            errorMessage = errorMessage,
            timestamp = timestamp,
        ),
    )

    private fun toolResult(callId: String, output: String, timestamp: Long): AgentMessage =
        StandardAgentMessage(
            ToolResultMessage(
                toolCallId = callId,
                toolName = "bash",
                content = listOf(TextContent(output)),
                isError = false,
                timestamp = timestamp,
            ),
        )

    private fun toolCall(id: String, name: String): ToolCall =
        ToolCall(id, name, JsonObject(emptyMap<String, JsonElement>()))
}
