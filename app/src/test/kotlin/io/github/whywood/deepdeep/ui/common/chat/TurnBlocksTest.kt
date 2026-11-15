package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.UserQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnBlocksTest {
    @Test
    fun keepsCustomCardAndTrailingTextAsContent() {
        val blocks = turnBlocks(
            assistant(
                AssistantStep.CustomCard(1, "mail.search", "{\"count\":2}", "邮件卡片"),
                AssistantStep.Text(2, "答案"),
            ),
        )
        assertEquals(2, blocks.size)
        val card = (blocks[0] as TurnBlock.Card).source as AnswerCardSource.Custom
        assertEquals("mail.search", card.step.customType)
        assertEquals("答案", (blocks[1] as TurnBlock.Text).step.text)
    }

    @Test
    fun groupsActivityBeforeTrailingAnswer() {
        val blocks = turnBlocks(
            assistant(
                AssistantStep.Reasoning(1, "想"),
                tool(2, "bash"),
                AssistantStep.Text(3, "中间说明"),
                tool(4, "bash"),
                AssistantStep.Text(5, "最终答案"),
            ),
        )
        assertEquals(2, blocks.size)
        val activity = blocks[0] as TurnBlock.Activity
        assertEquals(listOf(1L, 2L, 3L, 4L), activity.steps.map { it.id })
        assertEquals("最终答案", (blocks[1] as TurnBlock.Text).step.text)
    }

    @Test
    fun customCardSplitsActivitySegments() {
        val blocks = turnBlocks(
            assistant(
                tool(1, "bash"),
                AssistantStep.CustomCard(2, "mail.search", "{}", "卡片"),
                AssistantStep.Reasoning(3, "再想"),
                AssistantStep.Text(4, "答案"),
            ),
        )
        assertEquals(4, blocks.size)
        assertEquals(listOf(1L), (blocks[0] as TurnBlock.Activity).steps.map { it.id })
        assertTrue(blocks[1] is TurnBlock.Card)
        assertEquals(listOf(3L), (blocks[2] as TurnBlock.Activity).steps.map { it.id })
        assertTrue(blocks[3] is TurnBlock.Text)
    }

    @Test
    fun pluginToolCardSplitsActivitySegmentsLikeCliCard() {
        val blocks = turnBlocks(
            assistant(
                tool(1, "bash"),
                tool(2, "sysu_materials_search", answerCard = true),
                tool(3, "bash"),
            ),
        )
        assertEquals(3, blocks.size)
        assertEquals(listOf(1L, 2L), (blocks[0] as TurnBlock.Activity).steps.map { it.id })
        val card = (blocks[1] as TurnBlock.Card).source as AnswerCardSource.Tool
        assertEquals("sysu_materials_search", card.trace.name)
        assertEquals(listOf(3L), (blocks[2] as TurnBlock.Activity).steps.map { it.id })
    }

    @Test
    fun toolExecuteStaysInActivityBeforeInlineCard() {
        val blocks = turnBlocks(
            assistant(
                tool(1, "tool_search", callId = "call-1"),
                tool(2, "tool_execute", callId = "call-2", answerCard = true),
            ),
        )

        assertEquals(2, blocks.size)
        assertEquals(listOf(1L, 2L), (blocks[0] as TurnBlock.Activity).steps.map { it.id })
        assertEquals("tool_execute", ((blocks[1] as TurnBlock.Card).source as AnswerCardSource.Tool).trace.name)
    }

    @Test
    fun pluginCardFollowsActivityBeforeTrailingAnswer() {
        val blocks = turnBlocks(
            assistant(
                tool(1, "hotel_search", callId = "call-1", answerCard = true),
                AssistantStep.Text(2, "找到 6 家酒店"),
            ),
        )

        assertEquals(3, blocks.size)
        assertEquals(listOf(1L), (blocks[0] as TurnBlock.Activity).steps.map { it.id })
        assertEquals("hotel_search", ((blocks[1] as TurnBlock.Card).source as AnswerCardSource.Tool).trace.name)
        assertEquals("找到 6 家酒店", (blocks[2] as TurnBlock.Text).step.text)
    }

    @Test
    fun presentToolBecomesVisibleCardAfterActivity() {
        val presented = """{"items":[{"type":"file","path":"/tmp/a.pdf","name":"a.pdf"}]}"""
        val blocks = turnBlocks(
            assistant(
                tool(1, "present", output = presented),
                AssistantStep.Text(2, "已发送"),
            ),
        )
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is TurnBlock.Activity)
        val present = ((blocks[1] as TurnBlock.Card).source as AnswerCardSource.Host).card
        assertEquals("a.pdf", (present as HostAnswerCard.Present).items.single().name)
        assertEquals("已发送", (blocks[2] as TurnBlock.Text).step.text)
    }

    @Test
    fun answeredAskToolBecomesVisibleCard() {
        val questions = """[{"id":"q1","question":"选哪个"}]"""
        val answers = """{"answers":[{"id":"q1","selected":["A"]}]}"""
        val blocks = turnBlocks(
            assistant(
                tool(
                    id = 1,
                    name = "ask_user_question",
                    callId = "ask-1",
                    arguments = mapOf("questions" to questions),
                    output = answers,
                ),
            ),
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is TurnBlock.Activity)
        val answered = ((blocks[1] as TurnBlock.Card).source as AnswerCardSource.Host).card
            as HostAnswerCard.AnsweredQuestion
        assertEquals("ask-1", answered.toolCallId)
        assertEquals("选哪个", answered.record.questions.single().question)
    }

    @Test
    fun pendingQuestionFollowsItsActivity() {
        val pending = PendingQuestion(
            requestId = "request-1",
            sessionId = "session-1",
            toolCallId = "ask-1",
            questions = listOf(UserQuestion(id = "q1", question = "选哪个")),
        )
        val blocks = turnBlocks(
            assistant(tool(1, "ask_user_question", status = ToolActivityStatus.RUNNING, callId = "ask-1")),
            pendingQuestions = listOf(pending),
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is TurnBlock.Activity)
        assertEquals("request-1", (blocks[1] as TurnBlock.LiveQuestion).question.requestId)
    }

    @Test
    fun todoCardIsHiddenFromBlocks() {
        val blocks = turnBlocks(
            assistant(
                AssistantStep.CustomCard(1, TodoWriteCustomType, "{\"items\":[]}", "待办"),
                AssistantStep.Text(2, "答案"),
            ),
        )
        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is TurnBlock.Text)
    }

    @Test
    fun streamingMessageWithoutStepsKeepsLiveActivity() {
        val blocks = turnBlocks(assistant().copy(streaming = true))
        assertEquals(1, blocks.size)
        assertTrue((blocks.single() as TurnBlock.Activity).steps.isEmpty())
    }

    @Test
    fun answerFallsBackToTrailingTextStep() {
        val message = assistant(AssistantStep.Text(1, "流式答案"))
        val blocks = turnBlocks(message)
        assertEquals("", message.text)
        assertEquals("流式答案", answerText(message, blocks))
    }

    @Test
    fun latestTodoPayloadPrefersNewestCardThenStandaloneMessage() {
        val messages = listOf(
            assistant(AssistantStep.CustomCard(1, TodoWriteCustomType, "{\"round\":1}", "第一版")),
            assistant(AssistantStep.CustomCard(2, TodoWriteCustomType, "{\"round\":2}", "第二版"))
                .copy(id = 2),
            AssistantMessage(
                id = 3,
                role = AssistantRole.ASSISTANT,
                text = "独立",
                customType = TodoWriteCustomType,
                customPayload = "{\"round\":3}",
            ),
        )
        assertEquals("{\"round\":3}", latestTodoPayload(messages))
        assertEquals("{\"round\":2}", latestTodoPayload(messages.take(2)))
    }

    private fun assistant(vararg steps: AssistantStep): AssistantMessage = AssistantMessage(
        id = 1,
        role = AssistantRole.ASSISTANT,
        text = "",
        status = MessageStatus.DONE,
        steps = steps.toList(),
    )

    private fun tool(
        id: Long,
        name: String,
        status: ToolActivityStatus = ToolActivityStatus.DONE,
        callId: String = "call-$id",
        arguments: Map<String, String> = emptyMap(),
        output: String? = null,
        answerCard: Boolean = false,
    ): AssistantStep.Tool = AssistantStep.Tool(
        ToolTrace(
            id = id,
            name = name,
            arguments = arguments,
            status = status,
            output = output,
            toolCallId = callId,
            answerCard = answerCard,
        ),
    )
}
