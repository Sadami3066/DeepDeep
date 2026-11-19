package io.github.whywood.superpi.plugin.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsTest {
    @Test
    fun deliverAssemblesModelTextAndKeepsCardDescriptor() {
        val card = Cards.descriptor("sample.card", "取件提醒", buildJsonObject { put("id", "a") })
        val result = textResult(
            "原始正文",
            buildJsonObject {
                put(Cards.KEY, card)
                put(Cards.RAW_KEY, JsonPrimitive("{\"ok\":true}"))
                put(Cards.GUIDANCE_KEY, JsonPrimitive("下一步：去取件"))
            },
        )

        val delivered = Cards.deliver("call-1", result)

        val text = delivered.text.orEmpty()
        assertTrue(text.startsWith("原始正文"))
        assertTrue(text.contains("完整返回（上游原样）："))
        assertTrue(text.contains("使用说明：\n下一步：去取件"))
        assertEquals(
            "展示这张卡片：present({\"items\":[{\"type\":\"card\",\"id\":\"call-1\"}]})（取件提醒）",
            text.lines().last(),
        )
        val details = delivered.details ?: JsonObject(emptyMap())
        assertNull(details[Cards.RAW_KEY])
        assertNull(details[Cards.GUIDANCE_KEY])
        assertEquals("取件提醒", ((details[Cards.DISPLAY_KEY]) as JsonPrimitive).content)
        assertNotNull(details[Cards.KEY])
    }

    @Test
    fun deliverSkipsCardInstructionForErrors() {
        val card = Cards.descriptor("sample.card", "失败卡", null)
        val result = textResult(
            "出错了",
            buildJsonObject {
                put(Cards.KEY, card)
                put(Cards.GUIDANCE_KEY, JsonPrimitive("稍后重试"))
            },
            isError = true,
        )

        val delivered = Cards.deliver("call-2", result)

        val text = delivered.text.orEmpty()
        assertTrue(text.contains("使用说明：\n稍后重试"))
        assertFalse(text.contains("present("))
        assertNull((delivered.details ?: JsonObject(emptyMap()))[Cards.KEY])
    }

    @Test
    fun deliverReturnsResultWithoutCardKeys() {
        val result = textResult("普通结果", buildJsonObject { put("value", "1") })

        val delivered = Cards.deliver("call-3", result)

        assertEquals("普通结果", delivered.text)
        assertEquals(result.details, delivered.details)
    }

    @Test
    fun pluginToolDeliversResultWithCallId() {
        var received = ""
        val tool = pluginTool(
            name = "sample",
            description = "sample",
            parameters = buildJsonObject { put("type", "object") },
        ) { call ->
            received = call.callId
            textResult(
                "",
                buildJsonObject {
                    put(Cards.KEY, Cards.descriptor(null, "标题", null))
                },
            )
        }

        val delivered = kotlinx.coroutines.runBlocking {
            tool.execute(
                object : PluginToolCall {
                    override val callId: String = "call-4"
                    override val args: JsonObject = JsonObject(emptyMap())
                    override val signal: PluginRunSignal? = null
                    override val sessionId: String = "session"

                    override fun partial(result: PluginToolResult): Unit = Unit
                },
            )
        }

        assertEquals("call-4", received)
        assertTrue(delivered.text.orEmpty().contains("present({\"items\":[{\"type\":\"card\",\"id\":\"call-4\"}]})"))
    }

    @Test
    fun deliverIsIdempotentForWrappedTools() {
        val card = Cards.descriptor(null, "标题", null)
        val once = Cards.deliver(
            "call-6",
            textResult("", buildJsonObject { put(Cards.KEY, card) }),
        )

        val twice = Cards.deliver("call-6", once)

        assertEquals(once.text, twice.text)
    }

    @Test
    fun displayFallsBackToGuidanceWithoutCard() {
        val result = textResult(
            "",
            buildJsonObject { put(Cards.GUIDANCE_KEY, JsonPrimitive("先登录")) },
        )

        val delivered = Cards.deliver("call-5", result)

        assertEquals("先登录", (delivered.details?.get(Cards.DISPLAY_KEY) as JsonPrimitive).content)
        assertTrue(delivered.text.orEmpty().contains("使用说明：\n先登录"))
    }
}
