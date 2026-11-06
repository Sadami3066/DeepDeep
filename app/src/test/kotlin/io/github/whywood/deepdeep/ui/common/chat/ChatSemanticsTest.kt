package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSemanticsTest {
    @Test
    fun reasoningPreviewUsesLastParagraphFirstLine() {
        val text = "先想一下 **重点**\n第二行\n\n## 结论\n- 细节"
        assertEquals("结论", reasoningPreview(text))
    }

    @Test
    fun reasoningPreviewUsesFirstLineOfSingleParagraph() {
        assertEquals("第一行", reasoningPreview("第一行\n第二行"))
    }

    @Test
    fun reasoningPreviewStripsMarkdownMarkers() {
        assertEquals("思考中 重点 内容", reasoningPreview("思考中 **重点** 内容"))
        assertEquals("引用", reasoningPreview("> 引用"))
        assertEquals("列表", reasoningPreview("- 列表"))
    }

    @Test
    fun reasoningPreviewBlankReturnsEmpty() {
        assertEquals("", reasoningPreview("\n\n   "))
    }

    private fun tool(id: Long, status: ToolActivityStatus = ToolActivityStatus.RUNNING): AssistantStep.Tool =
        AssistantStep.Tool(ToolTrace(id = id, name = "tool", arguments = emptyMap(), status = status))

    @Test
    fun processActivityOnlyWhileStreaming() {
        assertEquals(null, processActivity(emptyList(), streaming = false))
        assertEquals(null, processActivity(listOf(tool(1)), streaming = false))
    }

    @Test
    fun processActivityDefaultsToThinking() {
        assertEquals(ProcessActivity.Thinking, processActivity(emptyList(), streaming = true))
        assertEquals(
            ProcessActivity.Thinking,
            processActivity(listOf(AssistantStep.Reasoning(id = 1, text = "想")), streaming = true),
        )
        assertEquals(
            ProcessActivity.Thinking,
            processActivity(listOf(tool(1, ToolActivityStatus.DONE)), streaming = true),
        )
    }

    @Test
    fun processActivityReportsRunningTool() {
        assertEquals(
            ProcessActivity.CallingTool,
            processActivity(listOf(tool(1, ToolActivityStatus.RUNNING)), streaming = true),
        )
        assertEquals(
            ProcessActivity.CallingTool,
            processActivity(
                listOf(AssistantStep.Text(id = 1, text = "上段"), tool(2, ToolActivityStatus.RUNNING)),
                streaming = true,
            ),
        )
    }

    @Test
    fun processActivityReportsReplying() {
        assertEquals(
            ProcessActivity.Replying,
            processActivity(listOf(AssistantStep.Text(id = 1, text = "答")), streaming = true),
        )
        assertEquals(
            ProcessActivity.Thinking,
            processActivity(listOf(AssistantStep.Text(id = 1, text = " ")), streaming = true),
        )
    }

    @Test
    fun processStatusMapsMessageState() {
        assertEquals(ProcessStatus.Running, processStatus(streaming = true, status = MessageStatus.RUNNING))
        assertEquals(ProcessStatus.Stopped, processStatus(streaming = false, status = MessageStatus.STOPPED))
        assertEquals(ProcessStatus.Failed, processStatus(streaming = false, status = MessageStatus.FAILED))
        assertEquals(ProcessStatus.Done, processStatus(streaming = false, status = MessageStatus.DONE))
    }
}
