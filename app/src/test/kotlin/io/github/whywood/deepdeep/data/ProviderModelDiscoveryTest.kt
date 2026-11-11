package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderModelDiscoveryTest {
    @Test
    fun parsesOpenAiStyleModelList() {
        val body = """{"object":"list","data":[{"id":"gpt-4o","object":"model"},{"id":"o3-mini"},{"id":"claude-3-7-sonnet"}]}"""
        assertEquals(
            listOf("claude-3-7-sonnet", "gpt-4o", "o3-mini"),
            ProviderModelDiscovery.parseModels(body).map { it.id },
        )
    }

    @Test
    fun parsesAnthropicStyleModelListAndDeduplicates() {
        val body = """{"data":[{"id":"claude-sonnet-4-5","display_name":"Claude Sonnet 4.5"},{"id":"claude-sonnet-4-5"}],"has_more":false}"""
        val models = ProviderModelDiscovery.parseModels(body)
        assertEquals(listOf("claude-sonnet-4-5"), models.map { it.id })
        assertEquals("Claude Sonnet 4.5", models.single().name)
    }

    @Test
    fun parsesOpenRouterStyleCapabilities() {
        val body = """
            {
              "data": [
                {
                  "id": "openai/gpt-4o",
                  "name": "OpenAI: GPT-4o",
                  "context_length": 128000,
                  "architecture": {"input_modalities": ["text", "image"]},
                  "top_provider": {"context_length": 128000, "max_completion_tokens": 16384},
                  "supported_parameters": ["tools", "reasoning"]
                },
                {
                  "id": "meta/llama",
                  "context_length": 8192,
                  "architecture": {"input_modalities": ["text"]},
                  "supported_parameters": ["tools"]
                }
              ]
            }
        """.trimIndent()
        val models = ProviderModelDiscovery.parseModels(body).associateBy { it.id }
        val gpt = models.getValue("openai/gpt-4o")
        assertEquals("OpenAI: GPT-4o", gpt.name)
        assertEquals(128_000L, gpt.contextWindow)
        assertEquals(16_384L, gpt.maxTokens)
        assertEquals(true, gpt.supportsImages)
        val llama = models.getValue("meta/llama")
        assertEquals(8_192L, llama.contextWindow)
        assertNull(llama.maxTokens)
        assertEquals(false, llama.supportsImages)
    }

    @Test
    fun parsesLooseCapabilityFields() {
        val body = """
            {
              "data": [
                {
                  "id": "m1",
                  "context_window": 200000,
                  "max_output_tokens": 8192,
                  "input_modalities": ["text", "image"],
                  "supports_vision": true
                },
                {
                  "id": "m2",
                  "inputTokenLimit": 32000,
                  "outputTokenLimit": 4096
                },
                {
                  "id": "m3",
                  "max_context_length": 1000000,
                  "maxTokens": 65536,
                  "capabilities": {"vision": true}
                }
              ]
            }
        """.trimIndent()
        val models = ProviderModelDiscovery.parseModels(body).associateBy { it.id }
        val m1 = models.getValue("m1")
        assertEquals(200_000L, m1.contextWindow)
        assertEquals(8_192L, m1.maxTokens)
        assertEquals(true, m1.supportsImages)
        val m2 = models.getValue("m2")
        assertEquals(32_000L, m2.contextWindow)
        assertEquals(4_096L, m2.maxTokens)
        assertNull(m2.supportsImages)
        val m3 = models.getValue("m3")
        assertEquals(1_000_000L, m3.contextWindow)
        assertEquals(65_536L, m3.maxTokens)
        assertEquals(true, m3.supportsImages)
    }

    @Test
    fun leavesUnknownCapabilitiesEmpty() {
        val body = """{"data":[{"id":"plain-model","name":"Plain","display_name":"Ignored"}]}"""
        val model = ProviderModelDiscovery.parseModels(body).single()
        assertEquals("plain-model", model.id)
        assertEquals("Plain", model.name)
        assertNull(model.contextWindow)
        assertNull(model.maxTokens)
        assertNull(model.supportsImages)
    }

    @Test
    fun returnsEmptyForUnexpectedPayloads() {
        assertEquals(emptyList<DiscoveredModel>(), ProviderModelDiscovery.parseModels("""{"error":{"message":"unauthorized"}}"""))
        assertEquals(emptyList<DiscoveredModel>(), ProviderModelDiscovery.parseModels("not json"))
        assertEquals(
            emptyList<DiscoveredModel>(),
            ProviderModelDiscovery.parseModels("""{"data":[{"id":""},{"name":"x"}]}"""),
        )
    }
}
