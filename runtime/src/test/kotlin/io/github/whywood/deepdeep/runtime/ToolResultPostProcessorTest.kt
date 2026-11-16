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
import org.junit.Assert.assertFalse
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

    private fun AgentToolResult.text(): String = content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun cardDetails(
        raw: String? = null,
        title: String? = "高德天气（4 天）",
        guidance: String? = null,
    ): JsonObject = buildJsonObject {
        put("server", "amap")
        val card = buildJsonObject {
            title?.let { put("title", it) }
        }
        put(ToolResultDetails.CARD_KEY, card)
        raw?.let { put(ToolResultDetails.RAW_KEY, it) }
        guidance?.let { put(ToolResultDetails.GUIDANCE_KEY, it) }
    }

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
    fun appendsCompletePayloadAndCardHintThenStripsRaw() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(result(details = cardDetails(raw = """{"data":{"forecasts":[]}}"""))),
        )

        val replaced = (decision as ToolResultDecision.Replace).result
        val text = replaced.text()
        assertTrue(text.contains(COMPLETE_PAYLOAD_HEADING))
        assertTrue(text.contains("""{"data":{"forecasts":[]}}"""))
        assertTrue(text.contains("卡片 id：call-1（高德天气（4 天））"))
        val details = replaced.details as JsonObject
        assertFalse(details.containsKey(ToolResultDetails.RAW_KEY))
        assertEquals("amap", details["server"]!!.jsonPrimitive.content)
        assertTrue(details.containsKey(ToolResultDetails.CARD_KEY))
    }

    @Test
    fun keepsRawForErrorResultsWithoutCardHint() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(result(text = "查询失败", details = cardDetails(raw = "upstream: 500"), isError = true)),
        )

        val replaced = (decision as ToolResultDecision.Replace).result
        val text = replaced.text()
        assertTrue(text.contains("upstream: 500"))
        assertFalse(text.contains("卡片 id："))
    }

    @Test
    fun appendsHintWithoutTitle() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(result(details = cardDetails(title = null))),
        )

        val text = (decision as ToolResultDecision.Replace).result.text()
        assertTrue(text.trimEnd().endsWith("卡片 id：call-1"))
    }

    @Test
    fun doesNotDuplicateExistingCardHint() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(result(text = "结果\n\n卡片 id：call-1", details = cardDetails(title = null))),
        )

        val text = (decision as ToolResultDecision.Replace).result.text()
        assertEquals(1, Regex("卡片 id：call-1").findAll(text).count())
    }

    @Test
    fun postProcessesReplacedResultAfterChainedHook() = runBlocking {
        val chained = object : ToolExecutionHooks {
            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision =
                ToolResultDecision.Replace(
                    AgentToolResult(
                        content = listOf(TextContent("plugin replaced")),
                        details = cardDetails(raw = "raw payload"),
                    ),
                )
        }

        val text = (ToolResultPostProcessor(chained).afterToolResult(context(result())) as ToolResultDecision.Replace)
            .result.text()
        assertTrue(text.startsWith(COMPLETE_PAYLOAD_HEADING))
        assertTrue(text.contains("raw payload"))
        assertFalse(text.contains("plugin replaced"))
        assertTrue(text.contains("卡片 id：call-1"))
    }

    @Test
    fun replacesModelTextWithRawAndAppendsGuidance() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(
                result(
                    text = "找到 20 个地点：中山大学（12,000 字摘要）",
                    details = cardDetails(raw = """{"pois":[{"name":"中山大学"}]}""", guidance = "需要路线时用 amap_route_*。"),
                ),
            ),
        )

        val replaced = (decision as ToolResultDecision.Replace).result
        val text = replaced.text()
        assertTrue(text.startsWith("$COMPLETE_PAYLOAD_HEADING\n"))
        assertTrue(text.contains("""{"pois":[{"name":"中山大学"}]}"""))
        assertTrue(text.contains("$GUIDANCE_HEADING\n需要路线时用 amap_route_*。"))
        assertFalse(text.contains("12,000 字摘要"))
        val details = replaced.details as JsonObject
        assertFalse(details.containsKey(ToolResultDetails.RAW_KEY))
        assertFalse(details.containsKey(ToolResultDetails.GUIDANCE_KEY))
    }

    @Test
    fun appendsGuidanceWithoutRawToExistingContent() = runBlocking {
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(result(text = "current text", details = cardDetails(raw = null, guidance = "先登录。"))),
        )

        val text = (decision as ToolResultDecision.Replace).result.text()
        assertTrue(text.startsWith("current text"))
        assertTrue(text.contains("$GUIDANCE_HEADING\n先登录。"))
    }

    @Test
    fun keepsNonTextContentWhenRawReplacesText() = runBlocking {
        val image = ImageContent("aGVsbG8=", "image/png")
        val decision = ToolResultPostProcessor(null).afterToolResult(
            context(
                AgentToolResult(
                    content = listOf(TextContent("old summary"), image),
                    details = cardDetails(raw = "raw payload"),
                ),
            ),
        )

        val content = (decision as ToolResultDecision.Replace).result.content
        assertEquals(1, content.filterIsInstance<ImageContent>().size)
        assertTrue(content.filterIsInstance<TextContent>().none { it.text.contains("old summary") })
    }
}
