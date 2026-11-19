package io.github.whywood.superpi.plugin.host.ui

import io.github.whywood.superpi.runtime.ToolResultDetails
import io.github.whywood.superpi.runtime.ToolTrace
import io.github.whywood.superpi.runtime.invocationKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class ToolInvocation(
    val key: String,
    val identity: ToolResultIdentity,
    val arguments: JsonObject,
    val structuredContent: JsonElement?,
)

internal fun ToolTrace.toolInvocation(isPluginTool: (String) -> Boolean): ToolInvocation = ToolInvocation(
    key = invocationKey,
    identity = toolResultIdentity(isPluginTool),
    arguments = toolResultArguments(),
    structuredContent = ToolResultDetails.structuredContent(resultDetails),
)
