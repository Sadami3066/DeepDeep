package io.github.whywood.deepdeep.ui.chat

private const val MCP_PREFIX = "mcp__"
private const val MCP_SEPARATOR = "__"

fun displayToolName(name: String): String {
    if (!name.startsWith(MCP_PREFIX)) return name
    val rest = name.removePrefix(MCP_PREFIX)
    val separator = rest.indexOf(MCP_SEPARATOR)
    if (separator <= 0 || separator + MCP_SEPARATOR.length >= rest.length) return name
    val server = rest.substring(0, separator)
    val tool = rest.substring(separator + MCP_SEPARATOR.length)
    return "$server · $tool"
}
