package io.github.whywood.superpi.plugin.api

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@JvmInline
public value class ToolResultSource private constructor(
    public val wire: String,
) {
    public companion object {
        public val PLUGIN: ToolResultSource = ToolResultSource("plugin")
        public val MCP: ToolResultSource = ToolResultSource("mcp")
        public val BUILTIN: ToolResultSource = ToolResultSource("builtin")
    }
}

public interface ToolResultMatch {
    public val source: ToolResultSource?
    public val server: String?
    public val tool: String?
    public val toolPrefix: String?

    public fun matches(
        source: ToolResultSource,
        server: String?,
        tool: String,
    ): Boolean

    public fun specificity(): Int
}

public fun toolResultMatch(
    source: ToolResultSource? = null,
    server: String? = null,
    tool: String? = null,
    toolPrefix: String? = null,
): ToolResultMatch = ToolResultMatchImpl(source, server, tool, toolPrefix)

internal data class ToolResultMatchImpl(
    override val source: ToolResultSource?,
    override val server: String?,
    override val tool: String?,
    override val toolPrefix: String?,
) : ToolResultMatch {
    override fun matches(source: ToolResultSource, server: String?, tool: String): Boolean {
        if (this.source != null && this.source != source) return false
        if (this.server != null && this.server != server) return false
        if (this.tool != null && this.tool != tool) return false
        if (toolPrefix != null && !tool.startsWith(toolPrefix)) return false
        return true
    }

    override fun specificity(): Int = listOfNotNull(source, server, tool, toolPrefix).size * 10 +
        (if (tool != null) 5 else 0) +
        (if (toolPrefix != null) 3 else 0)
}

public interface ToolResultRendererContributor {
    public val toolResultRenderers: List<ToolResultRenderer>
}

public interface ToolResultState {
    public val callId: String

    public val toolName: String

    public val source: ToolResultSource

    public val server: String?

    public val arguments: JsonObject

    public val result: PluginToolResult?

    public val structuredContent: JsonElement?
}

public interface ToolResultRenderer {
    public val id: String

    public val priority: Int
        get() = 0

    public val match: ToolResultMatch

    @Composable
    public fun content(state: ToolResultState)
}

public fun toolResultRenderer(
    id: String,
    match: ToolResultMatch,
    priority: Int = 0,
    content: @Composable (ToolResultState) -> Unit,
): ToolResultRenderer = object : ToolResultRenderer {
    override val id: String = id
    override val priority: Int = priority
    override val match: ToolResultMatch = match

    @Composable
    override fun content(state: ToolResultState) {
        content(state)
    }
}
