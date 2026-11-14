package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpStructuredCallResultTest {
    @Test
    fun defaultStructuredCallFallsBackToPlainCall() = runBlocking {
        val service = object : McpService {
            override suspend fun servers(): List<McpServer> = emptyList()

            override suspend fun probe(name: String): McpProbe = McpProbe(McpServerStatus.UNKNOWN)

            override suspend fun tools(server: String): List<McpTool> = emptyList()

            override suspend fun call(server: String, tool: String, arguments: JsonObject): McpCallResult =
                McpCallResult(blocks = listOf(textBlock("hello")), isError = false)
        }

        val result = service.callStructured("demo", "weather")

        assertEquals("hello", (result.blocks.single() as TextBlock).text)
        assertNull(result.structuredContent)
        assertFalse(result.isError)
    }

    @Test
    fun factoryKeepsStructuredContent() {
        val structured = buildJsonObject { put("city", "杭州") }
        val result = mcpStructuredCallResult(listOf(textBlock("text")), structured)

        assertEquals(structured, result.structuredContent)
        assertTrue(result.blocks.single() is TextBlock)
    }
}
