package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredToolsTest {
    private fun tool(name: String, description: String) = DeferredTool(
        name = name,
        description = description,
        parameters = buildJsonObject { put("type", "object") },
        source = "plugin:demo",
        execute = { _, _, _, _ -> AgentToolResult(emptyList()) },
    )

    @Test
    fun catalogSearchesByNameAndFindsExactTool() {
        val catalog = DeferredToolCatalog {
            listOf(
                tool("mail_send", "send an email message"),
                tool("calendar_read", "read calendar events"),
            )
        }
        assertEquals("mail_send", catalog.search("mail").first().name)
        assertEquals("calendar_read", catalog.find("calendar_read")?.name)
        assertNull(catalog.find("missing"))
    }

    @Test
    fun catalogSearchRespectsLimit() {
        val catalog = DeferredToolCatalog {
            (1..12).map { tool("tool_$it", "utility $it") }
        }
        assertTrue(catalog.search("utility", limit = 3).size <= 3)
    }
}
