package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentToolResult
import dev.pi.ai.ImageContent
import dev.pi.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import java.util.Base64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
            isError = true,
        )
        val converted = plugin.toAgentToolResult()
        assertEquals(4, converted.content.size)
        assertEquals("caption", (converted.content[0] as TextContent).text)
        assertEquals(Base64.getEncoder().encodeToString(bytes), (converted.content[1] as ImageContent).data)
        assertEquals("file: /tmp/a.pdf (application/pdf)", (converted.content[2] as TextContent).text)
        assertEquals("file: /tmp/b.txt", (converted.content[3] as TextContent).text)
        assertEquals(true, converted.isError)
    }

    @Test
    fun nonObjectDetailsAreDroppedForPlugins() {
        val agent = AgentToolResult(content = listOf(TextContent("hello")), details = JsonPrimitive("raw"))
        assertNull((agent.toPluginToolResult() as PluginToolResult.Text).details)
    }
}
