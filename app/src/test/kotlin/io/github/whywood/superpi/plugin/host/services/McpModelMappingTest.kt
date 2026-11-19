package io.github.whywood.superpi.plugin.host.services

import io.github.whywood.deepdeep.sdk.mcp.protocol.McpCallResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class McpModelMappingTest {
    @Test
    fun structuredResultKeepsStructuredContent() {
        val sdk = McpCallResult(
            content = listOf(buildJsonObject { put("type", "text"); put("text", "晴天") }),
            structuredContent = buildJsonObject { put("temperature", 26) },
        )

        val mapped = McpModelMapping.structuredResult(sdk)

        assertEquals("晴天", (mapped.blocks.single() as io.github.whywood.superpi.plugin.api.TextBlock).text)
        assertEquals(26, (mapped.structuredContent as JsonObject)["temperature"]!!.toString().toInt())
        assertFalse(mapped.isError)
    }
}
