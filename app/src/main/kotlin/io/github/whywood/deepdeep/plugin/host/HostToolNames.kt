package io.github.whywood.deepdeep.plugin.host

internal object HostToolNames {
    private val engine = setOf("read", "write", "edit", "bash")

    private val hostTools = setOf(
        "ask_user_question",
        "present",
        "adb_shell",
        "root_shell",
        "get_goal",
        "create_goal",
        "update_goal",
        "job_start",
        "job_output",
        "job_list",
        "job_kill",
        "timer_create",
        "timer_list",
        "timer_update",
        "timer_delete",
        "miniapp_create",
        "miniapp_load",
        "miniapp_list",
        "miniapp_delete",
        "miniapp_edit",
        "todo_write",
        "memory_write",
        "memory_read",
        "memory_search",
        "memory_status",
        "web_search",
        "web_fetch",
        "calendar",
        "virtual_screen",
        "screen_status",
        "screen_observe",
        "screen_screenshot",
        "screen_tap",
        "screen_swipe",
        "screen_scroll",
        "screen_type",
        "screen_key",
        "screen_wait",
        "screen_list_apps",
        "screen_launch",
    )

    private val sdkTools = setOf(
        "tool_search",
        "tool_execute",
        "list_mcp_resources",
        "list_mcp_resource_templates",
        "read_mcp_resource",
    )

    private val reservedPrefixes = setOf("mcp__")

    val names: Set<String> = engine + hostTools + sdkTools

    fun isReserved(name: String): Boolean = name in names || reservedPrefixes.any(name::startsWith)

    fun conflicts(candidates: Iterable<String>): Set<String> =
        candidates.filterTo(sortedSetOf(), ::isReserved)
}
