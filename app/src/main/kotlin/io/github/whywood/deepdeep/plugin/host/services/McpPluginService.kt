package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.McpProbe
import io.github.whywood.deepdeep.plugin.api.McpServer
import io.github.whywood.deepdeep.plugin.api.McpServerStatus
import io.github.whywood.deepdeep.plugin.api.McpService
import io.github.whywood.deepdeep.plugin.api.McpTool
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
import kotlinx.serialization.json.JsonObject

internal class McpPluginService(
    private val config: McpConfigStore,
    private val probe: McpServerProbe?,
    private val toolsProvider: (suspend (String) -> List<McpTool>)?,
    private val caller: (suspend (String, String, JsonObject) -> McpCallResult)?,
) : McpService {
    override suspend fun servers(): List<McpServer> = config.loadServers()
        .map { (name, server) ->
            McpServer(
                name = name,
                url = (server as? McpServerConfig.Http)?.url,
                enabled = server.enabled,
                status = McpServerStatus.UNKNOWN,
                toolCount = 0,
            )
        }
        .sortedBy { it.name }

    override suspend fun probe(name: String): McpProbe {
        val server = config.loadServers()[name]
            ?: return McpProbe(status = McpServerStatus.FAILED, error = "unknown MCP server: $name")
        val prober = probe ?: return McpProbe(status = McpServerStatus.UNKNOWN, error = "MCP probe is not available")
        return prober.probe(name, server).toPluginProbe()
    }

    override suspend fun tools(server: String): List<McpTool> =
        toolsProvider?.invoke(server) ?: emptyList()

    override suspend fun call(server: String, tool: String, arguments: JsonObject): McpCallResult =
        caller?.invoke(server, tool, arguments) ?: McpCallResult(
            blocks = listOf(ToolBlock.Text("MCP tool calls are not available to plugins")),
            isError = true,
        )

    private fun McpProbeResult.toPluginProbe(): McpProbe = McpProbe(
        status = when (state) {
            McpProbeState.OK -> McpServerStatus.OK
            McpProbeState.AUTH_REQUIRED -> McpServerStatus.AUTH_REQUIRED
            McpProbeState.FAILED -> McpServerStatus.FAILED
            McpProbeState.UNSUPPORTED -> McpServerStatus.UNKNOWN
        },
        toolCount = toolCount,
        serverName = serverName,
        error = error,
    )
}
