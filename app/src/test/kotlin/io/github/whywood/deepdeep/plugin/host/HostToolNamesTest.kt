package io.github.whywood.deepdeep.plugin.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostToolNamesTest {
    @Test
    fun reservesEngineAndHostTools() {
        val expected = listOf(
            "read", "write", "edit", "bash", "ls", "grep", "find",
            "ask_user_question", "present", "adb_shell", "root_shell",
            "get_goal", "create_goal", "update_goal",
            "job_start", "job_output", "job_list", "job_kill",
            "timer_create", "timer_list", "timer_update", "timer_delete",
            "miniapp_create", "miniapp_load", "miniapp_list", "miniapp_delete", "miniapp_edit",
            "tool_search", "mcp_loaded_tools", "mcp_resource", "mcp_resources",
            "mcp_resource_templates", "mcp_servers",
        )
        expected.forEach { name ->
            assertTrue(name, HostToolNames.isReserved(name))
            assertTrue(name, name in HostToolNames.names)
        }
    }

    @Test
    fun reservesMcpPrefix() {
        assertTrue(HostToolNames.isReserved("mcp__server__tool"))
        assertFalse(HostToolNames.isReserved("mcpx"))
    }

    @Test
    fun pluginOwnedNamesAreNotReserved() {
        assertFalse(HostToolNames.isReserved("sample_echo"))
        assertFalse(HostToolNames.isReserved("screen_tap"))
    }

    @Test
    fun conflictsFiltersAndSorts() {
        assertEquals(
            listOf("bash", "job_list"),
            HostToolNames.conflicts(listOf("job_list", "sample_echo", "bash")).toList(),
        )
    }
}
