package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentToolTest {
    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun filesArgs(vararg builders: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            putJsonArray("files") {
                builders.forEach { builder -> add(buildJsonObject(builder)) }
            }
        }

    @Test
    fun unboundSessionReturnsErrorText() = runTest {
        val tool = presentTool { null }
        val result = tool.execute("call-unbound", filesArgs({ put("path", "report.md") }), null, null)
        assertEquals(true, result.isError)
        assertEquals("Session is not bound", resultText(result))
    }

    @Test
    fun emptyPathReturnsErrorText() = runTest {
        val tool = presentTool { BoundSession("session-1", null, "/tmp") }
        val result = tool.execute("call-empty", filesArgs({ put("path", "   ") }), null, null)
        assertEquals(true, result.isError)
        assertTrue(resultText(result).isNotBlank())
    }

    @Test
    fun filesAreSerializedForTheModel() = runTest {
        val tool = presentTool { BoundSession("session-1", null, "/tmp") }
        val args = filesArgs(
            { put("path", "  reports/q3.md  ") },
            { put("path", "/tmp/slides.pptx") },
        )
        val result = tool.execute("call-1", args, null, null)
        assertNull(result.isError)
        val files = Json.parseToJsonElement(resultText(result)).jsonObject.getValue("files").jsonArray
        assertEquals(2, files.size)
        assertEquals("reports/q3.md", (files[0].jsonObject.getValue("path") as JsonPrimitive).content)
        assertEquals("/tmp/slides.pptx", (files[1].jsonObject.getValue("path") as JsonPrimitive).content)
        files.forEach { assertFalse(it.jsonObject.containsKey("description")) }
    }

    @Test
    fun schemaAcceptsOnlyPathPerFile() {
        val item = presentTool { null }
            .parameters
            .jsonObject.getValue("properties")
            .jsonObject.getValue("files")
            .jsonObject.getValue("items")
            .jsonObject
        val properties = item.getValue("properties").jsonObject
        assertEquals(setOf("path"), properties.keys)
        assertEquals(setOf("path"), item.getValue("required").jsonArray.map { (it as JsonPrimitive).content }.toSet())
    }
}
