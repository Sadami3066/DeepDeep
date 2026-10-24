package io.github.whywood.deepdeep.ui.mcp

import dev.pi.mcp.config.McpExposure
import dev.pi.mcp.config.McpOAuthConfig
import dev.pi.mcp.config.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerDraftTest {
    private val draft = McpServerDraft(name = "docs", url = "https://mcp.example.com/mcp")

    @Test
    fun validDraftPasses() {
        assertNull(draft.validate(emptySet()))
        assertNull(draft.copy(url = "http://127.0.0.1:8000/mcp").validate(emptySet()))
        assertNull(draft.copy(headers = "Authorization: Bearer token").validate(emptySet()))
    }

    @Test
    fun emptyNameRejected() {
        assertEquals(McpDraftError.EMPTY_NAME, draft.copy(name = "   ").validate(emptySet()))
    }

    @Test
    fun invalidNameRejected() {
        assertEquals(McpDraftError.INVALID_NAME, draft.copy(name = "bad name").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_NAME, draft.copy(name = "bad/name").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_NAME, draft.copy(name = "文档").validate(emptySet()))
        assertNull(draft.copy(name = "a_b-1").validate(emptySet()))
    }

    @Test
    fun duplicateNameRejected() {
        assertEquals(McpDraftError.DUPLICATE_NAME, draft.validate(setOf("docs", "other")))
        assertNull(draft.validate(setOf("other")))
    }

    @Test
    fun invalidUrlRejected() {
        assertEquals(McpDraftError.INVALID_URL, draft.copy(url = "").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_URL, draft.copy(url = "mcp.example.com").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_URL, draft.copy(url = "ftp://example.com").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_URL, draft.copy(url = "https://").validate(emptySet()))
        assertEquals(McpDraftError.INVALID_URL, draft.copy(url = "https://exa mple.com").validate(emptySet()))
    }

    @Test
    fun headersParsed() {
        val headers = McpServerDraft.parseHeaders(
            "X-Key: value\n\nAuthorization: Bearer a:b\n",
        )
        assertEquals(mapOf("X-Key" to "value", "Authorization" to "Bearer a:b"), headers)
        assertEquals(emptyMap<String, String>(), McpServerDraft.parseHeaders("  \n "))
    }

    @Test
    fun invalidHeadersRejected() {
        assertNull(McpServerDraft.parseHeaders("missing-colon"))
        assertNull(McpServerDraft.parseHeaders("X-Key:"))
        assertNull(McpServerDraft.parseHeaders(": value"))
        assertNull(McpServerDraft.parseHeaders("X Key: value"))
        assertNull(McpServerDraft.parseHeaders("X-Key: one\nx-key: two"))
        assertEquals(
            McpDraftError.INVALID_HEADERS,
            draft.copy(headers = "X-Key").validate(emptySet()),
        )
    }

    @Test
    fun toConfigKeepsDraftValues() {
        val config = draft.copy(
            description = "Docs server",
            headers = "X-Key: value",
            enabled = false,
        ).toConfig()
        assertEquals("https://mcp.example.com/mcp", config.url)
        assertEquals(mapOf("X-Key" to "value"), config.headers)
        assertEquals("Docs server", config.description)
        assertFalse(config.enabled)
    }

    @Test
    fun toConfigKeepsExistingSettings() {
        val existing = McpServerConfig.Http(
            url = "https://old.example.com/mcp",
            oauth = McpOAuthConfig(clientId = "client-id"),
            timeoutSeconds = 120,
            exposure = McpExposure.Direct,
        )
        val config = draft.toConfig(existing)
        assertEquals("https://mcp.example.com/mcp", config.url)
        assertEquals("client-id", config.oauth?.clientId)
        assertEquals(120L, config.timeoutSeconds)
        assertEquals(McpExposure.Direct, config.exposure)
    }

    @Test
    fun fromRoundTripsHeadersAndEnabled() {
        val original = McpServerConfig.Http(
            url = "https://mcp.example.com/mcp",
            headers = mapOf("X-Key" to "value"),
            description = "Docs",
            enabled = false,
        )
        val loaded = McpServerDraft.from("docs", original)
        assertEquals("docs", loaded.name)
        assertEquals("X-Key: value", loaded.headers)
        assertEquals("Docs", loaded.description)
        assertFalse(loaded.enabled)
        val config = loaded.toConfig(original)
        assertEquals(original.headers, config.headers)
        assertTrue(original.headers.isNotEmpty())
    }
}
