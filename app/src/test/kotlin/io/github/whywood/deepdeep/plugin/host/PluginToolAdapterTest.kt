package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.ToolResultDetails
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginToolAdapterTest {
    private val emptyArgs = JsonObject(emptyMap())

    @Test
    fun mapsToolMetadata() {
        val parameters = buildJsonObject {
            put("type", "object")
            put("required", "text")
        }
        val tool = PluginTool(
            name = "sample_tool",
            label = "Sample Label",
            description = "Sample description",
            parameters = parameters,
            promptSnippet = "use sample_tool",
            promptGuidelines = listOf("first guideline", "second guideline"),
            execute = { PluginToolResult.Text("ok") },
        )

        val coding = PluginToolAdapter.toCodingTool(tool)
        assertEquals("sample_tool", coding.name)
        assertEquals("Sample Label", coding.label)
        assertEquals("Sample description", coding.description)
        assertEquals(parameters, coding.parameters)
        assertEquals("use sample_tool", coding.promptSnippet)
        assertEquals(listOf("first guideline", "second guideline"), coding.promptGuidelines)
        assertNull(coding.prepareArguments)
    }

    @Test
    fun emptyPromptGuidelinesBecomeNull() {
        val tool = PluginTool(
            name = "plain_tool",
            label = "plain_tool",
            description = "plain",
            parameters = emptyArgs,
            execute = { PluginToolResult.Text("ok") },
        )
        val coding = PluginToolAdapter.toCodingTool(tool)
        assertNull(coding.promptGuidelines)
        assertNull(coding.promptSnippet)
    }

    @Test
    fun convertsBatchPreservingOrder() = runTest {
        val tools = listOf(testTool("first"), testTool("second"))
        assertEquals(listOf("first", "second"), PluginToolAdapter.toCodingTools(tools).map { it.name })
    }

    @Test
    fun mapsTextResult() = runTest {
        val tool = testTool("text_tool") {
            PluginToolResult.Text("hello", details = buildJsonObject { put("kind", "greeting") })
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("hello", (result.content.single() as TextContent).text)
        assertEquals(buildJsonObject { put("kind", "greeting") }, result.details)
        assertNull(result.isError)
    }

    @Test
    fun mapsTextErrorResult() = runTest {
        val tool = testTool("error_tool") {
            PluginToolResult.Text("failed", isError = true)
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("failed", (result.content.single() as TextContent).text)
        assertEquals(true, result.isError)
    }

    @Test
    fun mapsRichBlocks() = runTest {
        val imageBytes = byteArrayOf(1, 2, 3)
        val tool = testTool("rich_tool") {
            PluginToolResult.Rich(
                listOf(
                    ToolBlock.Text("before"),
                    ToolBlock.Image(imageBytes, "image/png"),
                    ToolBlock.FileRef("/tmp/plugin-output.txt", "text/plain"),
                ),
                details = buildJsonObject { put("kind", "rich") },
            )
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals(3, result.content.size)
        assertEquals("before", (result.content[0] as TextContent).text)
        val image = result.content[1] as ImageContent
        assertEquals(Base64.getEncoder().encodeToString(imageBytes), image.data)
        assertEquals("image/png", image.mimeType)
        assertEquals("file: /tmp/plugin-output.txt (text/plain)", (result.content[2] as TextContent).text)
        assertEquals(
            listOf(ToolResultPayload.Block.FileRef("/tmp/plugin-output.txt", "text/plain")),
            ToolResultDetails.decodeFileRefs(result.details),
        )
        assertEquals("rich", (result.details as JsonObject)["kind"]?.jsonPrimitive?.content)
        assertNull(result.isError)
    }

    @Test
    fun mapsFileRefWithoutMimeType() = runTest {
        val tool = testTool("file_tool") {
            PluginToolResult.Rich(listOf(ToolBlock.FileRef("/tmp/output.bin")))
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("file: /tmp/output.bin", (result.content.single() as TextContent).text)
        assertEquals(
            listOf(ToolResultPayload.Block.FileRef("/tmp/output.bin")),
            ToolResultDetails.decodeFileRefs(result.details),
        )
    }

    @Test
    fun mapsRichErrorResult() = runTest {
        val tool = testTool("rich_error") {
            PluginToolResult.Rich(listOf(ToolBlock.Text("broken")), isError = true)
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("broken", (result.content.single() as TextContent).text)
        assertEquals(true, result.isError)
    }

    @Test
    fun streamsPartialUpdatesBeforeFinalResult() = runTest {
        val tool = testTool("streaming_tool") { call ->
            call.partial(PluginToolResult.Text("half"))
            call.partial(PluginToolResult.Text("almost"))
            PluginToolResult.Text("done")
        }.let(PluginToolAdapter::toCodingTool)
        val updates = mutableListOf<String>()

        val result = tool.execute("call-1", emptyArgs, null) { partial ->
            updates += (partial.content.single() as TextContent).text
        }
        assertEquals(listOf("half", "almost"), updates)
        assertEquals("done", (result.content.single() as TextContent).text)
    }

    @Test
    fun convertsThrownExceptionToErrorResult() = runTest {
        val tool = testTool("failing_tool") {
            throw IllegalStateException("boom")
        }.let(PluginToolAdapter::toCodingTool)

        val result = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("boom", (result.content.single() as TextContent).text)
        assertEquals(true, result.isError)
    }

    @Test
    fun abortedSignalIsRejected() {
        val tool = testTool("slow_tool").let(PluginToolAdapter::toCodingTool)
        val signal = RunSignal().apply { abort("stop") }

        assertThrows(RequestAbortedException::class.java) {
            runBlocking { tool.execute("call-1", emptyArgs, signal, null) }
        }
    }

    @Test
    fun toolReceivesCallIdAndArguments() = runTest {
        var receivedCallId: String? = null
        var receivedArgs: JsonObject? = null
        val tool = testTool("args_tool") { call ->
            receivedCallId = call.callId
            receivedArgs = call.args
            PluginToolResult.Text("ok")
        }.let(PluginToolAdapter::toCodingTool)
        val args = buildJsonObject { put("text", "hello") }

        val result = tool.execute("call-42", args, null, null)
        assertTrue(result.content.single() is TextContent)
        assertEquals("call-42", receivedCallId)
        assertEquals(args, receivedArgs)
    }

    @Test
    fun sessionIdIsResolvedAtExecuteTime() = runTest {
        var current = "session-1"
        var seen: String? = null
        val tool = testTool("session_tool") { call ->
            seen = call.sessionId
            PluginToolResult.Text(call.sessionId)
        }.let { PluginToolAdapter.toCodingTool(it) { current } }

        val first = tool.execute("call-1", emptyArgs, null, null)
        assertEquals("session-1", seen)
        assertEquals("session-1", (first.content.single() as TextContent).text)

        current = "session-2"
        val second = tool.execute("call-2", emptyArgs, null, null)
        assertEquals("session-2", seen)
        assertEquals("session-2", (second.content.single() as TextContent).text)
    }

    @Test
    fun sessionIdDefaultsToEmptyString() = runTest {
        var seen: String? = null
        val tool = testTool("session_tool") { call ->
            seen = call.sessionId
            PluginToolResult.Text("ok")
        }.let(PluginToolAdapter::toCodingTool)

        tool.execute("call-1", emptyArgs, null, null)
        assertEquals("", seen)
    }
}
