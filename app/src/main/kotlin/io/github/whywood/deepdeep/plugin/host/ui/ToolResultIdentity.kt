package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.ToolResultSource
import io.github.whywood.deepdeep.runtime.ToolResultDetails
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class ToolResultIdentity(
    val source: ToolResultSource,
    val server: String?,
    val tool: String,
)

internal fun ToolTrace.toolResultIdentity(isPluginTool: (String) -> Boolean): ToolResultIdentity {
    val innerName = if (name == TOOL_EXECUTE_NAME) {
        rawArguments.stringValue("name") ?: arguments[NAME_ARGUMENT]
    } else {
        null
    }
    val detailsServer = resultDetails.stringValue("server")
    val detailsTool = resultDetails.stringValue("tool")
    if (name == TOOL_EXECUTE_NAME && detailsServer != null) {
        return ToolResultIdentity(
            source = ToolResultSource.MCP,
            server = detailsServer,
            tool = detailsTool ?: innerName?.let { toolResultIdentity(it, isPluginTool).tool } ?: name,
        )
    }
    val parsed = toolResultIdentity(innerName ?: name, isPluginTool)
    if (parsed.source != ToolResultSource.MCP) return parsed
    return ToolResultIdentity(
        source = ToolResultSource.MCP,
        server = detailsServer ?: parsed.server,
        tool = detailsTool ?: parsed.tool,
    )
}

internal fun ToolTrace.toolResultArguments(): JsonObject {
    if (name == TOOL_EXECUTE_NAME) {
        return rawArguments?.get("arguments") as? JsonObject ?: JsonObject(emptyMap())
    }
    return rawArguments ?: JsonObject(arguments.mapValues { (_, value) -> JsonPrimitive(value) })
}

internal fun toolResultIdentity(name: String, isPluginTool: (String) -> Boolean): ToolResultIdentity {
    if (name.startsWith(MCP_TOOL_PREFIX)) {
        val rest = name.removePrefix(MCP_TOOL_PREFIX)
        val separator = rest.indexOf(MCP_TOOL_SEPARATOR)
        if (separator > 0 && separator + MCP_TOOL_SEPARATOR.length < rest.length) {
            return ToolResultIdentity(
                source = ToolResultSource.MCP,
                server = rest.substring(0, separator),
                tool = rest.substring(separator + MCP_TOOL_SEPARATOR.length),
            )
        }
        return ToolResultIdentity(ToolResultSource.MCP, server = null, tool = rest)
    }
    return ToolResultIdentity(
        source = if (isPluginTool(name)) ToolResultSource.PLUGIN else ToolResultSource.BUILTIN,
        server = null,
        tool = name,
    )
}

internal fun ToolTrace.structuredContent(): kotlinx.serialization.json.JsonElement? =
    ToolResultDetails.structuredContent(resultDetails)

private fun JsonObject?.stringValue(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

private const val TOOL_EXECUTE_NAME = "tool_execute"
private const val NAME_ARGUMENT = "name"
private const val MCP_TOOL_PREFIX = "mcp__"
private const val MCP_TOOL_SEPARATOR = "__"
