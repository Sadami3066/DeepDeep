package io.github.whywood.deepdeep.ui.common.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPresentationTest {
    @Test
    fun toolIconKindMapsFileTools() {
        assertEquals(ToolIconKind.Book, toolIconKind("read"))
        assertEquals(ToolIconKind.Pen, toolIconKind("write"))
        assertEquals(ToolIconKind.Pen, toolIconKind("edit"))
        assertEquals(ToolIconKind.Folder, toolIconKind("ls"))
        assertEquals(ToolIconKind.Folder, toolIconKind("find"))
        assertEquals(ToolIconKind.File, toolIconKind("grep"))
    }

    @Test
    fun toolIconKindMapsShellAndWebTools() {
        assertEquals(ToolIconKind.Terminal, toolIconKind("bash"))
        assertEquals(ToolIconKind.Search, toolIconKind("web_search"))
        assertEquals(ToolIconKind.Search, toolIconKind("web_fetch"))
    }

    @Test
    fun toolIconKindMapsPrefixedTools() {
        assertEquals(ToolIconKind.Brain, toolIconKind("memory_read"))
        assertEquals(ToolIconKind.Brain, toolIconKind("memory_search"))
        assertEquals(ToolIconKind.Apps, toolIconKind("miniapp_load"))
        assertEquals(ToolIconKind.Clock, toolIconKind("job_start"))
        assertEquals(ToolIconKind.Clock, toolIconKind("timer_create"))
        assertEquals(ToolIconKind.Check, toolIconKind("todo_write"))
        assertEquals(ToolIconKind.Target, toolIconKind("get_goal"))
        assertEquals(ToolIconKind.Target, toolIconKind("update_goal"))
    }

    @Test
    fun toolIconKindFallsBackToPlug() {
        assertEquals(ToolIconKind.Plug, toolIconKind("unknown_tool"))
        assertEquals(ToolIconKind.Plug, toolIconKind("mcp__github__create_issue"))
        assertEquals(ToolIconKind.Plug, toolIconKind("present"))
    }

    @Test
    fun toolUsedLabelUsesDisplayName() {
        assertEquals("使用工具 read", toolUsedLabel("使用工具 %1\$s", "read"))
        assertEquals("Used tool bash", toolUsedLabel("Used tool %1\$s", "bash"))
    }

    @Test
    fun toolUsedLabelFormatsMcpName() {
        assertEquals(
            "使用工具 github · create_issue",
            toolUsedLabel("使用工具 %1\$s", "mcp__github__create_issue"),
        )
    }
}
