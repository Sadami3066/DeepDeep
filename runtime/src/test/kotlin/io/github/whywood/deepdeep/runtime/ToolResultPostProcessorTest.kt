package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultPostProcessorTest {
    private fun result(
        text: String = "forecast",
        details: JsonObject? = buildJsonObject { put("server", "amap") },
        structured: JsonObject? = buildJsonObject { put("city", "杭州") },
        isError: Boolean? = null,
    ): AgentToolResult = AgentToolResult(
        content = listOf(TextContent(text)),
        details = details,
        structuredContent = structured,
        isError = isError,
    )

    private fun context(result: AgentToolResult): ToolResultContext = ToolResultContext(
        toolName = "mcp__amap__weather",
        callId = "call-1",
        args = JsonObject(emptyMap()),
        result = result,
    )

    @Test
    fun copiesStructuredContentIntoDetails() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(context(result()))

        val replaced = decision as ToolResultDecision.Replace
        val details = replaced.result.details as JsonObject
        val deep = details["deepdeep"]!!.jsonObject
        assertEquals("杭州", deep["structuredContent"]!!.jsonObject["city"]!!.jsonPrimitive.content)
        assertEquals("amap", details["server"]!!.jsonPrimitive.content)
        assertEquals("forecast", replaced.result.content.single().let { (it as TextContent).text })
    }

    @Test
    fun keepsResultTextUntouched() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(context(result(text = "原样文本")))

        val replaced = decision as ToolResultDecision.Replace
        assertEquals("原样文本", (replaced.result.content.single() as TextContent).text)
    }

    @Test
    fun keepsOriginalResultWhenToolHasNothingToPostProcess() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(AgentToolResult(content = listOf(TextContent("plain")), details = null)),
        )

        assertEquals(ToolResultDecision.Keep, decision)
    }

    @Test
    fun omitsOversizedStructuredContent() = runBlocking {
        val huge = buildJsonObject { put("payload", JsonPrimitive("x".repeat(300 * 1024))) }
        val decision = ToolResultPostProcessor(null).afterToolResult(context(result(structured = huge)))

        val details = (decision as ToolResultDecision.Replace).result.details as JsonObject
        val deep = details["deepdeep"]!!.jsonObject
        assertEquals(true, deep["structuredContentOmitted"]!!.jsonPrimitive.content.toBoolean())
        assertNull(ToolResultDetails.structuredContent(details))
    }

    @Test
    fun beforeToolCallDelegatesToChainedHook() = runBlocking {
        val chained = object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                ToolCallDecision.Block("blocked by plugin")
        }

        val decision = ToolResultPostProcessor(chained).beforeToolCall(
            ToolCallContext(toolName = "bash", callId = "call-1", args = JsonObject(emptyMap())),
        )

        assertEquals(ToolCallDecision.Block("blocked by plugin"), decision)
    }

    @Test
    fun chainedHookRunsBeforeEnrichmentAndKeepsItsDetails() = runBlocking {
        val chained = object : ToolExecutionHooks {
            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision =
                ToolResultDecision.Replace(
                    AgentToolResult(
                        content = context.result.content,
                        details = buildJsonObject { put("plugin", "kept") },
                        structuredContent = context.result.structuredContent,
                    ),
                )
        }

        val decision = ToolResultPostProcessor(chained).afterToolResult(context(result()))

        val details = (decision as ToolResultDecision.Replace).result.details as JsonObject
        assertEquals("kept", details["plugin"]!!.jsonPrimitive.content)
        assertTrue(details.containsKey("deepdeep"))
        assertEquals("杭州", ToolResultDetails.structuredContent(details)!!.jsonObject["city"]!!.jsonPrimitive.content)
    }

    @Test
    fun keepsNonTextContent() = runBlocking {
        val image = ImageContent("aGVsbG8=", "image/png")
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(
                AgentToolResult(
                    content = listOf(TextContent("summary"), image),
                    details = buildJsonObject { put("server", "amap") },
                    structuredContent = buildJsonObject { put("city", "杭州") },
                ),
            ),
        )

        val content = (decision as ToolResultDecision.Replace).result.content
        assertEquals(1, content.filterIsInstance<ImageContent>().size)
        assertTrue(content.filterIsInstance<TextContent>().any { it.text == "summary" })
    }
}
