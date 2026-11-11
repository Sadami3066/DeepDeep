package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderModelDiscoveryTest {
    @Test
    fun parsesOpenAiStyleModelList() {
        val body = """{"object":"list","data":[{"id":"gpt-4o","object":"model"},{"id":"o3-mini"},{"id":"claude-3-7-sonnet"}]}"""
        assertEquals(listOf("claude-3-7-sonnet", "gpt-4o", "o3-mini"), ProviderModelDiscovery.parseModelIds(body))
    }

    @Test
    fun parsesAnthropicStyleModelListAndDeduplicates() {
        val body = """{"data":[{"id":"claude-sonnet-4-5","display_name":"Claude Sonnet 4.5"},{"id":"claude-sonnet-4-5"}],"has_more":false}"""
        assertEquals(listOf("claude-sonnet-4-5"), ProviderModelDiscovery.parseModelIds(body))
    }

    @Test
    fun returnsEmptyForUnexpectedPayloads() {
        assertEquals(emptyList<String>(), ProviderModelDiscovery.parseModelIds("""{"error":{"message":"unauthorized"}}"""))
        assertEquals(emptyList<String>(), ProviderModelDiscovery.parseModelIds("not json"))
        assertEquals(emptyList<String>(), ProviderModelDiscovery.parseModelIds("""{"data":[{"id":""},{"name":"x"}]}"""))
    }
}
