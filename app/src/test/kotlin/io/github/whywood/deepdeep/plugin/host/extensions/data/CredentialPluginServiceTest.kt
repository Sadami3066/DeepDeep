package io.github.whywood.deepdeep.plugin.host.extensions.data

import io.github.whywood.deepdeep.data.CustomProviderEntry
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthState
import io.github.whywood.deepdeep.sdk.mcp.oauth.OAuthTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialPluginServiceTest {
    private fun summary(id: String, name: String, baseUrl: String? = null) = ProviderSummary(
        id = id,
        name = name,
        modelCount = 1,
        baseUrl = baseUrl,
        hasOAuth = false,
        supportsApiKey = true,
    )

    private fun custom(id: String, name: String, baseUrl: String, apiKey: String) = CustomProviderEntry(
        id = id,
        name = name,
        api = "openai-completions",
        baseUrl = baseUrl,
        apiKey = apiKey,
        models = emptyList(),
    )

    @Test
    fun customProvidersOverrideCatalogEntries() {
        val merged = mergedProviderCredentials(
            custom = listOf(custom("deepseek", "My DeepSeek", "https://example.test/v1", "custom-key")),
            catalog = listOf(summary("deepseek", "DeepSeek", "https://api.deepseek.com")),
            apiKey = { "catalog-key" },
            authKind = { "api-key" },
        )
        val entry = merged.single()
        assertEquals("My DeepSeek", entry.name)
        assertEquals("https://example.test/v1", entry.baseUrl)
        assertEquals("custom-key", entry.apiKey)
        assertEquals("api-key", entry.authKind)
    }

    @Test
    fun catalogEntriesUseCredentialStoreValues() {
        val merged = mergedProviderCredentials(
            custom = emptyList(),
            catalog = listOf(summary("openai", "OpenAI", "https://api.openai.com")),
            apiKey = { "stored-key" },
            authKind = { "oauth" },
        )
        val entry = merged.single()
        assertEquals("openai", entry.id)
        assertEquals("stored-key", entry.apiKey)
        assertEquals("oauth", entry.authKind)
    }

    @Test
    fun blankKeysAndUrlsAreDropped() {
        val merged = mergedProviderCredentials(
            custom = emptyList(),
            catalog = listOf(summary("empty", "Empty", baseUrl = "  ")),
            apiKey = { "   " },
            authKind = { null },
        )
        assertNull(merged.single().apiKey)
        assertNull(merged.single().baseUrl)
        assertNull(merged.single().authKind)
    }

    @Test
    fun mcpTokenRequiresAccessToken() {
        val state = McpOAuthState(
            serverUrl = "https://mcp.example.com/mcp",
            tokens = OAuthTokens(accessToken = "access", tokenType = "Bearer", refreshToken = "refresh"),
            tokensExpireAt = 123_456,
        )
        val token = pluginMcpToken("demo", "https://mcp.example.com/mcp", state)
        assertEquals("demo", token?.server)
        assertEquals("access", token?.accessToken)
        assertEquals("refresh", token?.refreshToken)
        assertEquals("Bearer", token?.tokenType)
        assertEquals(123_456L, token?.expiresAtMs)

        assertNull(pluginMcpToken("demo", "url", null))
        assertNull(
            pluginMcpToken(
                "demo",
                "url",
                McpOAuthState(serverUrl = "url", tokens = OAuthTokens(accessToken = " ", tokenType = "Bearer")),
            ),
        )
    }

    @Test
    fun hostValuesSkipBlankEntries() {
        assertEquals(
            mapOf(
                HOST_VALUE_INSTALLATION_ID to "install-1",
                HOST_VALUE_ADB_TOKEN to "token-1",
            ),
            hostCredentialValues("install-1", "token-1"),
        )
        assertTrue(hostCredentialValues(null, null).isEmpty())
        assertEquals(mapOf(HOST_VALUE_ADB_TOKEN to "t"), hostCredentialValues(" ", "t"))
    }
}
