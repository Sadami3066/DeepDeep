package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
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

    private fun tool(id: Long): AssistantStep.Tool = AssistantStep.Tool(
        ToolTrace(id = id, name = "tool", arguments = emptyMap()),
    )

    @Test
    fun consecutiveToolsBecomeGroup() {
        val entries = groupToolSteps(
            listOf(
                AssistantStep.Reasoning(id = 1, text = "想"),
                tool(2),
                tool(3),
                AssistantStep.Text(id = 4, text = "答"),
            ),
        )
        assertEquals(3, entries.size)
        assertEquals(AssistantStep.Reasoning(id = 1, text = "想"), (entries[0] as ProcessEntry.Single).step)
        assertEquals(listOf(tool(2), tool(3)), (entries[1] as ProcessEntry.ToolGroup).tools)
        assertEquals(AssistantStep.Text(id = 4, text = "答"), (entries[2] as ProcessEntry.Single).step)
    }

    @Test
    fun singleToolStaysSingle() {
        val entries = groupToolSteps(listOf(tool(1), AssistantStep.Reasoning(id = 2, text = "想")))
        assertEquals(2, entries.size)
        assertEquals(tool(1), (entries[0] as ProcessEntry.Single).step)
        assertEquals(AssistantStep.Reasoning(id = 2, text = "想"), (entries[1] as ProcessEntry.Single).step)
    }

    @Test
    fun nonToolStepsBreakGroups() {
        val entries = groupToolSteps(
            listOf(tool(1), tool(2), AssistantStep.Text(id = 3, text = "中"), tool(4), tool(5), tool(6)),
        )
        assertEquals(3, entries.size)
        assertEquals(listOf(tool(1), tool(2)), (entries[0] as ProcessEntry.ToolGroup).tools)
        assertEquals(AssistantStep.Text(id = 3, text = "中"), (entries[1] as ProcessEntry.Single).step)
        assertEquals(listOf(tool(4), tool(5), tool(6)), (entries[2] as ProcessEntry.ToolGroup).tools)
    }

    @Test
    fun processStatusMapsMessageState() {
        assertEquals(ProcessStatus.Running, processStatus(streaming = true, status = MessageStatus.RUNNING))
        assertEquals(ProcessStatus.Stopped, processStatus(streaming = false, status = MessageStatus.STOPPED))
        assertEquals(ProcessStatus.Failed, processStatus(streaming = false, status = MessageStatus.FAILED))
        assertEquals(ProcessStatus.Done, processStatus(streaming = false, status = MessageStatus.DONE))
    }
}
