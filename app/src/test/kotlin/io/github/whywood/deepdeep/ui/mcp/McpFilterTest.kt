package io.github.whywood.deepdeep.ui.mcp

import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class McpFilterTest {
    private val entries = listOf(
        McpServerEntry("files", McpServerConfig.Stdio(command = "npx file-server", description = "Local files")),
        McpServerEntry("search", McpServerConfig.Http(url = "https://search.example.com/mcp", description = "Web search")),
        McpServerEntry("github", McpServerConfig.Http(url = "https://api.github.com/mcp")),
    )

    @Test
    fun filtersByNameUrlAndDescription() {
        assertEquals(listOf("search"), filterMcpServers(entries, "EXAMPLE").map { it.name })
        assertEquals(listOf("search"), filterMcpServers(entries, "web search").map { it.name })
        assertEquals(listOf("files"), filterMcpServers(entries, "local files").map { it.name })
        assertEquals(emptyList<McpServerEntry>(), filterMcpServers(entries, "npx"))
        assertEquals(entries, filterMcpServers(entries, ""))
        assertEquals(emptyList<McpServerEntry>(), filterMcpServers(entries, "missing"))
    }
}
