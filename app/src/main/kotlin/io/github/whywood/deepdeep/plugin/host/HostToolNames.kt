package io.github.whywood.deepdeep.plugin.host

internal object HostToolNames {
    private val engine = setOf("read", "write", "edit", "bash", "ls", "grep", "find")

    private val hostTools = setOf(
        "ask_user_question",
        "plugin_setup",
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
    )

    private val sdkTools = setOf(
        "tool_search",
        "mcp_loaded_tools",
        "mcp_resource",
        "mcp_resources",
        "mcp_resource_templates",
        "mcp_servers",
    )

    private val reservedPrefixes = setOf("mcp__")

    val names: Set<String> = engine + hostTools + sdkTools

    fun isReserved(name: String): Boolean = name in names || reservedPrefixes.any(name::startsWith)

    fun conflicts(candidates: Iterable<String>): Set<String> =
        candidates.filterTo(sortedSetOf(), ::isReserved)
}
