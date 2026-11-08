package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.ToolResultDetails
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginToolResultConversionTest {
    @Test
    fun textResultMapsBothWays() {
        val details = buildJsonObject { put("key", "value") }
        val agent = AgentToolResult(content = listOf(TextContent("hello")), details = details, isError = true)
        assertEquals(PluginToolResult.Text("hello", details, isError = true), agent.toPluginToolResult())
        val converted = PluginToolResult.Text("hello", details, isError = true).toAgentToolResult()
        assertEquals("hello", (converted.content.single() as TextContent).text)
        assertEquals(details, converted.details)
        assertEquals(true, converted.isError)
    }

    @Test
    fun richAgentResultDecodesImages() {
        val bytes = byteArrayOf(1, 2, 3)
        val agent = AgentToolResult(
            content = listOf(
                TextContent("caption"),
                ImageContent(Base64.getEncoder().encodeToString(bytes), "image/png"),
            ),
        )
        val plugin = agent.toPluginToolResult() as PluginToolResult.Rich
        assertEquals(ToolBlock.Text("caption"), plugin.blocks[0])
        val image = plugin.blocks[1] as ToolBlock.Image
        assertArrayEquals(bytes, image.data)
        assertEquals("image/png", image.mimeType)
        assertNull(plugin.details)
        assertEquals(false, plugin.isError)
    }

    @Test
    fun richPluginResultEncodesImagesAndFileRefs() {
        val bytes = byteArrayOf(9, 8)
        val plugin = PluginToolResult.Rich(
            blocks = listOf(
                ToolBlock.Text("caption"),
                ToolBlock.Image(bytes, "image/jpeg"),
                ToolBlock.FileRef("/tmp/a.pdf", "application/pdf"),
                ToolBlock.FileRef("/tmp/b.txt"),
            ),
            details = buildJsonObject { put("kind", "rich") },
            isError = true,
        )
        val converted = plugin.toAgentToolResult()
        assertEquals(4, converted.content.size)
        assertEquals("caption", (converted.content[0] as TextContent).text)
        assertEquals(Base64.getEncoder().encodeToString(bytes), (converted.content[1] as ImageContent).data)
        assertEquals("file: /tmp/a.pdf (application/pdf)", (converted.content[2] as TextContent).text)
        assertEquals("file: /tmp/b.txt", (converted.content[3] as TextContent).text)
        assertEquals(
            listOf(
                ToolResultPayload.Block.FileRef("/tmp/a.pdf", "application/pdf"),
                ToolResultPayload.Block.FileRef("/tmp/b.txt"),
            ),
            ToolResultDetails.decodeFileRefs(converted.details),
        )
        assertEquals("rich", (converted.details as JsonObject)["kind"]?.jsonPrimitive?.content)
        assertEquals(true, converted.isError)
    }

    @Test
    fun pluginReplacementKeepsFileRefDetails() {
        val details = buildJsonObject {
            put(
                ToolResultDetails.FILE_REFS_KEY,
                ToolResultDetails.encodeFileRefs(listOf(ToolResultPayload.Block.FileRef("/tmp/a.pdf", "application/pdf"))),
            )
        }
        val converted = PluginToolResult.Rich(
            blocks = listOf(ToolBlock.Text("rewritten")),
            details = details,
        ).toAgentToolResult()

        assertEquals(details, converted.details)
        assertEquals(
            listOf(ToolResultPayload.Block.FileRef("/tmp/a.pdf", "application/pdf")),
            ToolResultDetails.decodeFileRefs(converted.details),
        )
    }

    @Test
    fun nonObjectDetailsAreDroppedForPlugins() {
        val agent = AgentToolResult(content = listOf(TextContent("hello")), details = JsonPrimitive("raw"))
        assertNull((agent.toPluginToolResult() as PluginToolResult.Text).details)
    }
}
