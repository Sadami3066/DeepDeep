package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.ImageContent
import dev.pi.ai.TextContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPayloadMappingTest {
    @Test
    fun textOnlyContentBecomesTextPayload() {
        val result = AgentToolResult(content = listOf(TextContent("hello"), TextContent(" world")))

        assertEquals(ToolResultPayload.Text("hello world"), result.toToolResultPayload())
        assertEquals("hello world", result.toToolResultPayload().text())
    }

    @Test
    fun imageContentBecomesStructuredBlocks() {
        val result = AgentToolResult(
            content = listOf(TextContent("shot"), ImageContent("aGk=", "image/png")),
        )

        assertEquals(
            ToolResultPayload.Blocks(
                listOf(
                    ToolResultPayload.Block.Text("shot"),
                    ToolResultPayload.Block.Image("aGk=", "image/png"),
                ),
            ),
            result.toToolResultPayload(),
        )
    }

    @Test
    fun emptyContentBecomesEmptyPayload() {
        assertEquals(ToolResultPayload.Empty, AgentToolResult().toToolResultPayload())
        assertEquals("", ToolResultPayload.Empty.text())
    }

    @Test
    fun fileRefDetailsBecomeStructuredBlocks() {
        val details = buildJsonObject {
            put("kind", "report")
            put(
                ToolResultDetails.FILE_REFS_KEY,
                ToolResultDetails.encodeFileRefs(
                    listOf(
                        ToolResultPayload.Block.FileRef("/tmp/report.pdf", "application/pdf"),
                        ToolResultPayload.Block.FileRef("/tmp/raw.bin"),
                    ),
                ),
            )
        }
        val result = AgentToolResult(
            content = listOf(
                TextContent("before"),
                TextContent(fileRefDisplayText("/tmp/report.pdf", "application/pdf")),
                TextContent(fileRefDisplayText("/tmp/raw.bin", null)),
            ),
            details = details,
        )

        assertEquals(
            ToolResultPayload.Blocks(
                listOf(
                    ToolResultPayload.Block.Text("before"),
                    ToolResultPayload.Block.Text("file: /tmp/report.pdf (application/pdf)"),
                    ToolResultPayload.Block.Text("file: /tmp/raw.bin"),
                    ToolResultPayload.Block.FileRef("/tmp/report.pdf", "application/pdf"),
                    ToolResultPayload.Block.FileRef("/tmp/raw.bin"),
                ),
            ),
            result.toToolResultPayload(),
        )
    }

    @Test
    fun malformedFileRefDetailsAreIgnored() {
        val malformed = AgentToolResult(
            content = listOf(TextContent("ok")),
            details = buildJsonObject { put(ToolResultDetails.FILE_REFS_KEY, "not-an-array") },
        )
        val incomplete = AgentToolResult(
            content = listOf(TextContent("ok")),
            details = buildJsonObject {
                put(ToolResultDetails.FILE_REFS_KEY, ToolResultDetails.encodeFileRefs(listOf(ToolResultPayload.Block.FileRef(""))))
            },
        )

        assertEquals(ToolResultPayload.Text("ok"), malformed.toToolResultPayload())
        assertEquals(ToolResultPayload.Text("ok"), incomplete.toToolResultPayload())
    }

    @Test
    fun displayArgumentsKeepScalarsReadable() {
        val arguments = buildJsonObject {
            put("command", "ls -la")
            put("limit", 5)
            put("nested", buildJsonObject { put("x", 1) })
        }

        val display = arguments.toDisplayArguments()

        assertEquals("ls -la", display["command"])
        assertEquals("5", display["limit"])
        assertEquals("{\"x\":1}", display["nested"])
    }
}
