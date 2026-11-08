package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.mcp.McpDeferredTool
import io.github.whywood.deepdeep.sdk.coding.mcp.McpToolSearch
import io.github.whywood.deepdeep.sdk.coding.session.AgentSession
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DeferredTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val source: String,
    val execute: suspend (
        toolCallId: String,
        args: JsonObject,
        signal: RunSignal?,
        onUpdate: (suspend (AgentToolResult) -> Unit)?,
    ) -> AgentToolResult,
)

class DeferredToolCatalog(
    private val tools: () -> List<DeferredTool>,
) {
    fun search(query: String, limit: Int = 8): List<DeferredTool> {
        val all = tools()
        if (all.isEmpty()) return emptyList()
        val byName = all.associateBy { it.name }
        val search = McpToolSearch(
            catalog = {
                all.map { tool ->
                    McpDeferredTool(
                        name = tool.name,
                        description = tool.description,
                        server = tool.source,
                        parameters = tool.parameters,
                    )
                }
            },
            onLoaded = {},
        )
        return search.search(query, limit).mapNotNull { byName[it.name] }
    }

    fun find(name: String): DeferredTool? = tools().firstOrNull { it.name == name }
}


internal fun deferredTools(
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession? = { null },
): List<CodingTool> = listOf(
    deferredSearchTool(context, binding, session),
    deferredExecuteTool(context, binding, session),
)

private const val DEFERRED_SEARCH_LIMIT = 8
private const val DEFERRED_DESCRIPTION_LIMIT = 240

private fun deferredSearchTool(
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession?,
): CodingTool = CodingTool(
    name = "tool_search",
    description = "Search deferred tools from MCP servers and plugins. Returns tool names and parameters; call tool_execute to run one.",
    parameters = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "What the tool should do.")
                    },
                )
                put(
                    "limit",
                    buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum number of tools to return (1-8).")
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("query"))))
    },
    label = "搜索按需工具",
    promptSnippet = "Use tool_search to discover deferred MCP or plugin tools, then tool_execute to call one.",
    promptGuidelines = listOf(
        "Deferred tools are not callable directly; search first, then execute by name.",
        "Do not guess deferred tool names without calling tool_search first.",
    ),
    execute = { _, args, _, _ ->
        val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (query.isEmpty()) {
            textResult("tool_search requires a non-empty query.", isError = true)
        } else {
            val limit = args["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.coerceIn(1, DEFERRED_SEARCH_LIMIT)
                ?: DEFERRED_SEARCH_LIMIT
            val matches = DeferredToolCatalog { deferredCatalog(context, binding, session) }
                .search(query, limit)
            if (matches.isEmpty()) {
                textResult("No deferred tools matched: $query")
            } else {
                val details = buildJsonObject {
                    put(
                        "tools",
                        JsonArray(
                            matches.map { tool ->
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("source", tool.source)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                }
                            },
                        ),
                    )
                }
                val text = buildString {
                    appendLine("Matched ${matches.size} deferred tool(s). Call tool_execute with name and arguments.")
                    matches.forEach { tool ->
                        appendLine()
                        appendLine("- ${tool.name} [${tool.source}]")
                        appendLine("  ${tool.description.replace(Regex("\\s+"), " ").take(DEFERRED_DESCRIPTION_LIMIT)}")
                        append("  parameters: ${tool.parameters}")
                    }
                }
                textResult(text.trimEnd(), details)
            }
        }
    },
)

private fun deferredExecuteTool(
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession?,
): CodingTool = CodingTool(
    name = "tool_execute",
    description = "Execute one deferred MCP or plugin tool found with tool_search.",
    parameters = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "name",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Deferred tool name returned by tool_search.")
                    },
                )
                put(
                    "arguments",
                    buildJsonObject {
                        put("type", "object")
                        put("description", "Arguments for the deferred tool.")
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("arguments"))))
    },
    label = "执行按需工具",
    promptSnippet = "Run a deferred tool by name after finding it with tool_search.",
    promptGuidelines = listOf("Unknown names fail; call tool_search before tool_execute."),
    execute = { toolCallId, args, signal, onUpdate ->
        val name = args["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (name.isEmpty()) {
            textResult("tool_execute requires a tool name.", isError = true)
        } else {
            val tool = DeferredToolCatalog { deferredCatalog(context, binding, session) }.find(name)
            if (tool == null) {
                textResult("Unknown deferred tool: $name. Call tool_search first.", isError = true)
            } else {
                val arguments = when (val value = args["arguments"]) {
                    is JsonObject -> value
                    is JsonPrimitive -> runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(value.content) as JsonObject
                    }.getOrElse {
                        return@CodingTool textResult("tool_execute arguments must be a JSON object.", isError = true)
                    }
                    else -> return@CodingTool textResult("tool_execute arguments must be a JSON object.", isError = true)
                }
                tool.execute(toolCallId, arguments, signal, onUpdate)
            }
        }
    },
)

private fun deferredCatalog(
    context: AppToolContext,
    binding: SessionBinding,
    session: () -> AgentSession?,
): List<DeferredTool> {
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<DeferredTool>()
    runCatching { context.pluginDeferredTools(binding) }.getOrDefault(emptyList()).forEach { tool ->
        if (seen.add(tool.name)) result += tool
    }
    val mcp = runCatching { session()?.mcp?.deferredCatalog().orEmpty() }.getOrDefault(emptyList())
    val activeSession = session()
    mcp.forEach { tool ->
        if (seen.add(tool.name)) {
            result += DeferredTool(
                name = tool.name,
                description = tool.description.orEmpty(),
                parameters = tool.parameters,
                source = "mcp:${tool.server}",
                execute = { _, arguments, mcpSignal, mcpUpdate ->
                    val active = activeSession
                    if (active == null) {
                        textResult("MCP session is not available.", isError = true)
                    } else {
                        runCatching {
                            active.mcp.callDeferredTool(tool.name, arguments, mcpSignal, mcpUpdate)
                        }.getOrNull()
                            ?: textResult("MCP tool ${tool.name} is not available.", isError = true)
                    }
                },
            )
        }
    }
    return result
}
