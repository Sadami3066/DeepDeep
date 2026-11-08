package io.github.whywood.deepdeep.plugin.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostToolNamesTest {
    private val expected = setOf(
        "read", "write", "edit", "bash",
        "ask_user_question", "present", "adb_shell", "root_shell",
        "get_goal", "create_goal", "update_goal",
        "job_start", "job_output", "job_list", "job_kill",
        "timer_create", "timer_list", "timer_update", "timer_delete",
        "miniapp_create", "miniapp_load", "miniapp_list", "miniapp_delete", "miniapp_edit",
        "todo_write",
        "memory_write", "memory_read", "memory_search", "memory_status",
        "web_search", "web_fetch", "calendar", "virtual_screen",
        "screen_status", "screen_observe", "screen_screenshot", "screen_tap", "screen_swipe",
        "screen_scroll", "screen_type", "screen_key", "screen_wait", "screen_list_apps", "screen_launch",
        "tool_search", "tool_execute",
        "list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource",
    )

    @Test
    fun reservedNamesMatchRegisteredTools() {
        assertEquals(expected.sorted(), HostToolNames.names.sorted())
    }

    @Test
    fun reservesMcpPrefix() {
        assertTrue(HostToolNames.isReserved("mcp__server__tool"))
        assertFalse(HostToolNames.isReserved("mcpx"))
    }

    @Test
    fun pluginOwnedNamesAreNotReserved() {
        assertFalse(HostToolNames.isReserved("sample_echo"))
        assertFalse(HostToolNames.isReserved("plugin_note"))
    }

    @Test
    fun conflictsFiltersAndSorts() {
        assertEquals(
            listOf("bash", "job_list"),
            HostToolNames.conflicts(listOf("job_list", "sample_echo", "bash")).toList(),
        )
    }
}
