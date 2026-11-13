package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.AssistantMessage as PiAssistantMessage
import io.github.whywood.deepdeep.sdk.ai.Content
import io.github.whywood.deepdeep.sdk.ai.Message
import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.ToolResultMessage
import io.github.whywood.deepdeep.sdk.ai.Usage
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.coding.paths.AgentPaths
import io.github.whywood.deepdeep.sdk.coding.session.BranchSummaryEntry
import io.github.whywood.deepdeep.sdk.coding.session.CompactionEntry
import io.github.whywood.deepdeep.sdk.coding.session.ContextEditEntry
import io.github.whywood.deepdeep.sdk.coding.session.CustomMessageEntry
import io.github.whywood.deepdeep.sdk.coding.session.ModelChangeEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionManager
import io.github.whywood.deepdeep.sdk.coding.session.SessionMessageEntry
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMapperTest {
    private var entryId = 0

    @Test
    fun errorAssistantMarksTurnFailedWithPartialText() {
        val messages = HistoryMapper.mapEntries(
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
        assertNull(answer.error)
        assertEquals("quota exceeded", answer.rawError)
        assertEquals("partial answer", answer.text)
    }

    @Test
    fun errorAssistantWithoutContentIsStillMapped() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("hi", 1L),
                assistant(stopReason = StopReason.Error, errorMessage = "boom", timestamp = 2L),
            ),
        )

        assertEquals(2, messages.size)
        assertEquals(MessageStatus.FAILED, messages[1].status)
        assertNull(messages[1].error)
        assertEquals("boom", messages[1].rawError)
        assertEquals("", messages[1].text)
    }

    @Test
    fun errorAssistantWithoutErrorMessageMarksTurnFailedWithoutRawError() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("hi", 1L),
                assistant(stopReason = StopReason.Error, timestamp = 2L),
            ),
        )

        assertEquals(2, messages.size)
        assertEquals(MessageStatus.FAILED, messages[1].status)
        assertNull(messages[1].error)
        assertNull(messages[1].rawError)
    }

    @Test
    fun abortedAssistantMarksTurnStopped() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("hi", 1L),
                assistant(content = listOf(TextContent("half")), stopReason = StopReason.Aborted, timestamp = 2L),
            ),
        )

        assertEquals(2, messages.size)
        assertEquals(MessageStatus.STOPPED, messages[1].status)
        assertNull(messages[1].error)
        assertNull(messages[1].rawError)
    }

    @Test
    fun reminderMessageSplitsNameAndBody() {
        val messages = HistoryMapper.mapEntries(
            listOf(user("[定时提醒] 写周报\n\n请提交本周周报", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals("写周报", messages[0].reminder)
        assertEquals("请提交本周周报", messages[0].text)
    }

    @Test
    fun batchReminderKeepsCountAsName() {
        val messages = HistoryMapper.mapEntries(
            listOf(user("[定时提醒] 2 个提醒\n\n1. 写周报\n2. 喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("2 个提醒", messages[0].reminder)
        assertEquals("1. 写周报\n2. 喝水", messages[0].text)
    }

    @Test
    fun reminderFramingSentenceIsHiddenFromBody() {
        val messages = HistoryMapper.mapEntries(
            listOf(user("[定时提醒] 喝水\n这是你设置的定时提醒，现在到时间了。\n\n去喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("喝水", messages[0].reminder)
        assertEquals("去喝水", messages[0].text)
    }

    @Test
    fun reminderWithoutBlankLineKeepsRemainderAsBody() {
        val messages = HistoryMapper.mapEntries(
            listOf(user("[定时提醒] 喝水\n记得喝水", 1L)),
        )

        assertEquals(1, messages.size)
        assertEquals("喝水", messages[0].reminder)
        assertEquals("记得喝水", messages[0].text)
    }

    @Test
    fun plainUserMessageIsNotTreatedAsReminder() {
        val messages = HistoryMapper.mapEntries(listOf(user("普通消息", 1L)))

        assertEquals(1, messages.size)
        assertNull(messages[0].reminder)
        assertEquals("普通消息", messages[0].text)
        assertEquals(MessageStatus.DONE, messages[0].status)
    }

    @Test
    fun attachmentBlockStaysInUserText() {
        val text = "<attachments>\n- /workspace/a.png | a.png | image/png | 100\n" +
            "files are in the workspace; use the read tool to inspect them\n</attachments>\n\n描述图片"
        val messages = HistoryMapper.mapEntries(listOf(user(text, 1L)))

        assertEquals(1, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals(text, messages[0].text)
        assertNull(messages[0].reminder)
    }

    @Test
    fun toolTurnAggregatesIntoProcessStepsAndAnswer() {
        val messages = HistoryMapper.mapEntries(
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

    @Test
    fun toolResultStillBackfillsToolRecordAfterCustomCard() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("check", 1L),
                assistant(
                    content = listOf(toolCall("call-1", "bash")),
                    stopReason = StopReason.ToolUse,
                    timestamp = 2L,
                ),
                customCard("plugin.card", "卡片内容", timestamp = 3L),
                toolResult("call-1", "ok", timestamp = 4L),
            ),
        )

        assertEquals(3, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        val toolMessage = messages[1]
        assertEquals(AssistantRole.ASSISTANT, toolMessage.role)
        assertEquals(1, toolMessage.steps.size)
        val trace = (toolMessage.steps.single() as AssistantStep.Tool).trace
        assertEquals(ToolActivityStatus.DONE, trace.status)
        assertEquals("ok", trace.output)
        assertEquals("卡片内容", messages[2].text)
    }

    @Test
    fun toolTraceKeepsStructuredArgumentsAndResultPayload() {
        val arguments = JsonObject(mapOf("command" to kotlinx.serialization.json.JsonPrimitive("ls -la")))
        val messages = HistoryMapper.mapEntries(
            listOf(
                assistant(
                    content = listOf(ToolCall("call-1", "bash", arguments)),
                    stopReason = StopReason.ToolUse,
                    timestamp = 1L,
                ),
                toolResult("call-1", "ok", timestamp = 2L),
            ),
        )

        val trace = (messages.single().steps.single() as AssistantStep.Tool).trace
        assertEquals(arguments, trace.rawArguments)
        assertEquals("ls -la", trace.arguments["command"])
        assertEquals(ToolResultPayload.Text("ok"), trace.resultPayload)
    }

    @Test
    fun toolResultDetailsRestoreForSlots() {
        val details = buildJsonObject { put("draftId", "draft-1") }
        val messages = HistoryMapper.mapEntries(
            listOf(
                assistant(
                    content = listOf(toolCall("call-1", "mail_draft")),
                    stopReason = StopReason.ToolUse,
                    timestamp = 1L,
                ),
                messageEntry(
                    ToolResultMessage(
                        toolCallId = "call-1",
                        toolName = "mail_draft",
                        content = listOf(TextContent("ok")),
                        details = details,
                        isError = false,
                        timestamp = 2L,
                    ),
                ),
            ),
        )

        val trace = (messages.single().steps.single() as AssistantStep.Tool).trace
        assertEquals(details, trace.resultDetails)
    }

    @Test
    fun toolResultImagesSurviveAsStructuredBlocks() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                assistant(
                    content = listOf(toolCall("call-1", "screenshot")),
                    stopReason = StopReason.ToolUse,
                    timestamp = 1L,
                ),
                messageEntry(
                    ToolResultMessage(
                        toolCallId = "call-1",
                        toolName = "screenshot",
                        content = listOf(TextContent("took one"), io.github.whywood.deepdeep.sdk.ai.ImageContent("aGVsbG8=", "image/png")),
                        isError = false,
                        timestamp = 2L,
                    ),
                ),
            ),
        )

        val payload = (messages.single().steps.single() as AssistantStep.Tool).trace.resultPayload
        assertEquals(
            ToolResultPayload.Blocks(
                listOf(
                    ToolResultPayload.Block.Text("took one"),
                    ToolResultPayload.Block.Image("aGVsbG8=", "image/png"),
                ),
            ),
            payload,
        )
    }

    @Test
    fun toolResultFileRefsRestoreFromDetails() {
        val fileRef = ToolResultPayload.Block.FileRef("/tmp/report.pdf", "application/pdf")
        val messages = HistoryMapper.mapEntries(
            listOf(
                assistant(
                    content = listOf(toolCall("call-1", "export_report")),
                    stopReason = StopReason.ToolUse,
                    timestamp = 1L,
                ),
                messageEntry(
                    ToolResultMessage(
                        toolCallId = "call-1",
                        toolName = "export_report",
                        content = listOf(TextContent(fileRefDisplayText(fileRef.path, fileRef.mimeType))),
                        details = buildJsonObject {
                            put(ToolResultDetails.FILE_REFS_KEY, ToolResultDetails.encodeFileRefs(listOf(fileRef)))
                        },
                        isError = false,
                        timestamp = 2L,
                    ),
                ),
            ),
        )

        val trace = (messages.single().steps.single() as AssistantStep.Tool).trace
        assertEquals(
            ToolResultPayload.Blocks(
                listOf(
                    ToolResultPayload.Block.Text("file: /tmp/report.pdf (application/pdf)"),
                    fileRef,
                ),
            ),
            trace.resultPayload,
        )
        assertEquals("file: /tmp/report.pdf (application/pdf)", trace.output)
    }

    @Test
    fun toolResultFileRefsSurviveSessionFileReopen() = runTest {
        val root = Files.createTempDirectory("deepdeep-file-refs").toFile()
        val dir = root.absolutePath.toPath()
        try {
            val paths = AgentPaths(dir.resolve("workspace"), dir.resolve("agent"))
            val fileRef = ToolResultPayload.Block.FileRef("/tmp/report.pdf", "application/pdf")
            val manager = SessionManager.create(paths, FileSystem.SYSTEM)
            manager.appendMessage(UserMessage(PlainTextContent("export it"), 1L))
            manager.appendMessage(
                ToolResultMessage(
                    toolCallId = "call-1",
                    toolName = "export_report",
                    content = listOf(TextContent(fileRefDisplayText(fileRef.path, fileRef.mimeType))),
                    details = buildJsonObject {
                        put(ToolResultDetails.FILE_REFS_KEY, ToolResultDetails.encodeFileRefs(listOf(fileRef)))
                    },
                    isError = false,
                    timestamp = 2L,
                ),
            )
            val reopened = SessionManager.open(manager.sessionFile!!, FileSystem.SYSTEM)

            val payload = (HistoryMapper.mapEntries(reopened.getBranch()).last().steps.single() as AssistantStep.Tool)
                .trace.resultPayload

            assertEquals(
                ToolResultPayload.Blocks(
                    listOf(
                        ToolResultPayload.Block.Text("file: /tmp/report.pdf (application/pdf)"),
                        fileRef,
                    ),
                ),
                payload,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun compactionEntryBecomesSummaryCardBetweenTurns() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("旧问题", 1L),
                assistant(content = listOf(TextContent("旧回答")), timestamp = 2L),
                compaction(
                    summary = "之前聊过旧问题",
                    tokensBefore = 4096L,
                ),
                user("新问题", 4L),
                assistant(content = listOf(TextContent("新回答")), timestamp = 5L),
            ),
        )

        assertEquals(5, messages.size)
        assertEquals("旧问题", messages[0].text)
        assertEquals("旧回答", messages[1].text)
        val card = messages[2]
        assertEquals(AssistantRole.ASSISTANT, card.role)
        assertEquals("", card.text)
        assertEquals(MessageSummaryKind.COMPACTION, card.summary?.kind)
        assertEquals("之前聊过旧问题", card.summary?.text)
        assertEquals(4096L, card.summary?.tokensBefore)
        assertNull(card.summary?.tokensAfter)
        assertNull(card.summary?.fromId)
        assertEquals("新问题", messages[3].text)
    }

    @Test
    fun branchSummaryEntryBecomesSummaryCard() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("旧问题", 1L),
                branchSummary(summary = "从另一条分支回来", fromId = "entry-old"),
                user("新问题", 3L),
            ),
        )

        assertEquals(3, messages.size)
        val card = messages[1]
        assertEquals(MessageSummaryKind.BRANCH, card.summary?.kind)
        assertEquals("从另一条分支回来", card.summary?.text)
        assertEquals("entry-old", card.summary?.fromId)
        assertNull(card.summary?.tokensBefore)
        assertEquals("新问题", messages[2].text)
    }

    @Test
    fun nonMessageEntriesAreSkippedWithoutChangingOrder() {
        val messages = HistoryMapper.mapEntries(
            listOf(
                user("先问", 1L),
                assistant(content = listOf(TextContent("先答")), timestamp = 2L),
                modelChange(),
                contextEdit("assistant-1"),
                user("再问", 3L),
                assistant(content = listOf(TextContent("再答")), timestamp = 4L),
            ),
        )

        assertEquals(4, messages.size)
        assertEquals("先问", messages[0].text)
        assertEquals("先答", messages[1].text)
        assertEquals("再问", messages[2].text)
        assertEquals("再答", messages[3].text)
    }

    @Test
    fun blankUserEntriesAreSkipped() {
        val messages = HistoryMapper.mapEntries(
            listOf(user("", 1L), user("  ", 2L), user("有内容", 3L)),
        )

        assertEquals(1, messages.size)
        assertEquals("有内容", messages[0].text)
    }

    @Test
    fun realBranchPlacesCompactionCardAfterOldMessages() = runTest {
        val manager = SessionManager.inMemory("/tmp".toPath())
        manager.appendMessage(UserMessage(PlainTextContent("旧问题"), 1L))
        val answer = manager.appendMessage(
            PiAssistantMessage(
                content = mutableListOf(TextContent("旧回答")),
                api = "",
                provider = "",
                model = "",
                usage = Usage.empty(),
                stopReason = StopReason.Stop,
                timestamp = 2L,
            ),
        )
        manager.appendCompaction(summary = "旧对话摘要", firstKeptEntryId = answer.id, tokensBefore = 1000L)
        manager.appendMessage(UserMessage(PlainTextContent("新问题"), 4L))

        val messages = HistoryMapper.mapEntries(manager.getBranch())

        assertEquals(4, messages.size)
        assertEquals("旧问题", messages[0].text)
        assertEquals("旧回答", messages[1].text)
        assertEquals(MessageSummaryKind.COMPACTION, messages[2].summary?.kind)
        assertEquals("旧对话摘要", messages[2].summary?.text)
        assertEquals("新问题", messages[3].text)
    }

    @Test
    fun realBranchPlacesBranchSummaryCardAfterTargetPath() = runTest {
        val manager = SessionManager.inMemory("/tmp".toPath())
        val target = manager.appendMessage(UserMessage(PlainTextContent("回到这里"), 1L))
        manager.appendMessage(UserMessage(PlainTextContent("新分支消息"), 2L))
        manager.branchWithSummary(entryId = target.id, summary = "回退摘要")

        val messages = HistoryMapper.mapEntries(manager.getBranch())

        assertEquals(2, messages.size)
        assertEquals("回到这里", messages[0].text)
        assertEquals(MessageSummaryKind.BRANCH, messages[1].summary?.kind)
        assertEquals("回退摘要", messages[1].summary?.text)
        assertNotNull(messages[1].summary?.fromId)
    }

    private fun nextId(): String = "entry-${entryId++}"

    private fun messageEntry(message: Message): SessionMessageEntry = SessionMessageEntry(
        id = nextId(),
        parentId = null,
        timestamp = TIMESTAMP,
        message = message,
    )

    private fun user(text: String, timestamp: Long): SessionEntry =
        messageEntry(UserMessage(PlainTextContent(text), timestamp))

    private fun assistant(
        content: List<Content> = emptyList(),
        stopReason: StopReason = StopReason.Stop,
        errorMessage: String? = null,
        timestamp: Long,
    ): SessionEntry = messageEntry(
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

    private fun toolResult(callId: String, output: String, timestamp: Long): SessionEntry = messageEntry(
        ToolResultMessage(
            toolCallId = callId,
            toolName = "bash",
            content = listOf(TextContent(output)),
            isError = false,
            timestamp = timestamp,
        ),
    )

    private fun compaction(summary: String, tokensBefore: Long): SessionEntry = CompactionEntry(
        id = nextId(),
        parentId = null,
        timestamp = TIMESTAMP,
        summary = summary,
        firstKeptEntryId = "entry-0",
        tokensBefore = tokensBefore,
    )

    private fun branchSummary(summary: String, fromId: String): SessionEntry = BranchSummaryEntry(
        id = nextId(),
        parentId = null,
        timestamp = TIMESTAMP,
        fromId = fromId,
        summary = summary,
    )

    private fun modelChange(): SessionEntry = ModelChangeEntry(
        id = nextId(),
        parentId = null,
        timestamp = TIMESTAMP,
        provider = "deepseek",
        modelId = "deepseek-chat",
    )

    private fun contextEdit(targetId: String): SessionEntry = ContextEditEntry(
        id = nextId(),
        parentId = null,
        timestamp = TIMESTAMP,
        targetId = targetId,
        replacement = null,
    )

    private fun toolCall(id: String, name: String): ToolCall =
        ToolCall(id, name, JsonObject(emptyMap<String, JsonElement>()))

    private fun customCard(customType: String, text: String, timestamp: Long): SessionEntry = CustomMessageEntry(
        id = nextId(),
        parentId = null,
        timestamp = "2026-10-29T10:00:00Z",
        customType = customType,
        content = PlainTextContent(text),
        display = true,
        details = null,
    )

    private companion object {
        const val TIMESTAMP = "2026-10-29T10:00:00Z"
    }
}
