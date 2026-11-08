package io.github.whywood.deepdeep.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRegistryParserTest {
    @Test
    fun parsesPluginsSkillsAndMcpEntries() {
        val registry = MarketRegistryParser.parse(registry().encodeToByteArray())

        assertEquals(3, registry.schemaVersion)
        assertEquals("ec256:test", registry.keyId)
        assertEquals(3, registry.entries.size)
        val plugin = registry.entries[0]
        assertEquals(MarketEntryType.Plugin, plugin.type)
        assertEquals("io.example.plugin", plugin.id)
        assertEquals("plugin-key", plugin.plugin?.publisherKeyId)
        assertEquals("插件", plugin.name("zh"))
        assertEquals("Plugin", plugin.name("en"))
        val skill = registry.entries[1]
        assertEquals(MarketEntryType.Skill, skill.type)
        assertEquals("MIT", skill.skill?.license)
        assertEquals(listOf("技能"), skill.tags("zh"))
        assertEquals(listOf("Skill"), skill.tags("en"))
        val mcp = registry.entries[2]
        assertEquals(MarketEntryType.Mcp, mcp.type)
        assertEquals("http", mcp.mcp?.transport)
        assertEquals("https://mcp.example.com/mcp", mcp.mcp?.url)
        assertEquals(MarketMcpAuthType.OAuth, mcp.mcp?.auth?.type)
        assertEquals("首次授权", mcp.mcp?.authHint?.resolve("zh"))
    }

    @Test
    fun parsesApiKeyAuth() {
        val json = registry(mcpAuthType = "apiKey")
        val mcp = MarketRegistryParser.parse(json.encodeToByteArray()).entries[2]
        assertEquals(MarketMcpAuthType.ApiKey, mcp.mcp?.auth?.type)
        assertEquals("Authorization", mcp.mcp?.auth?.header)
        assertEquals("Bearer ", mcp.mcp?.auth?.prefix)
        assertEquals("粘贴令牌", mcp.mcp?.auth?.placeholder?.resolve("zh"))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsApiKeyWithoutHeader() {
        val json = registry(mcpAuthType = "apiKey", apiKeyHeader = null)
        MarketRegistryParser.parse(json.encodeToByteArray())
    }

    @Test
    fun resolvesHeadersForApiKey() {
        val json = registry(mcpAuthType = "apiKey")
        val mcp = MarketRegistryParser.parse(json.encodeToByteArray()).entries[2].mcp!!
        assertEquals("Bearer token-1", mcp.resolvedHeaders("token-1")["Authorization"])
        assertEquals("secret", mcp.resolvedHeaders("secret")["X-Existing"])
    }

    @Test
    fun resolvesHeadersForOAuthWithoutToken() {
        val json = registry(mcpAuthType = "oauth")
        val mcp = MarketRegistryParser.parse(json.encodeToByteArray()).entries[2].mcp!!
        assertEquals(mapOf("X-Existing" to "secret"), mcp.resolvedHeaders(null))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsDuplicateIds() {
        val json = registry(duplicateId = true)
        MarketRegistryParser.parse(json.encodeToByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsInvalidSha256() {
        val json = registry(sha256 = "not-a-sha")
        MarketRegistryParser.parse(json.encodeToByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsStdioMcpTransport() {
        val json = registry(transport = "stdio")
        MarketRegistryParser.parse(json.encodeToByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsTooManyEntries() {
        val entries = (0 until MarketDefaults.MAX_ENTRIES + 1).joinToString(",") { index ->
            """{"type":"skill","id":"s$index","name":{"zh":"n"},"summary":{"zh":"s"},"version":"1","artifact":{"url":"https://example.com/$index.zip","sha256":"${"a".repeat(64)}","size":1}}"""
        }
        MarketRegistryParser.parse("""{"schemaVersion":3,"generatedAt":"now","keyId":"k","entries":[$entries]}""".encodeToByteArray())
    }

    @Test
    fun exposesEntryTypeSpecificMetadata() {
        val registry = MarketRegistryParser.parse(registry().encodeToByteArray())
        val tags = registry.entries.flatMap { it.tags("zh") }
        assertTrue(tags.containsAll(listOf("插件", "技能", "MCP")))
    }

    @Test
    fun acceptsLegacySchemaWithPlainText() {
        val legacy = registry()
            .replace("\"name\": {\"zh\": \"插件\", \"en\": \"Plugin\"}", "\"name\": \"插件\"")
            .replace("\"summary\": {\"zh\": \"一句话\", \"en\": \"Summary\"}", "\"summary\": \"一句话\"")
            .replace("\"description\": {\"zh\": \"说明\", \"en\": \"Description\"}", "\"description\": \"说明\"")
            .replace("\"tags\": {\"zh\": [\"插件\"], \"en\": [\"Plugin\"]}", "\"tags\": [\"插件\"]")
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
        val parsed = MarketRegistryParser.parse(legacy.encodeToByteArray())
        assertEquals("插件", parsed.entries[0].name("zh"))
        assertEquals(listOf("插件"), parsed.entries[0].tags("zh"))
    }

    private fun registry(
        duplicateId: Boolean = false,
        sha256: String = "a".repeat(64),
        transport: String = "http",
        mcpAuthType: String = "oauth",
        apiKeyHeader: String? = "Authorization",
    ): String {
        val secondId = if (duplicateId) "io.example.plugin" else "example-skill"
        val auth = when (mcpAuthType) {
            "apiKey" -> """{"type":"apiKey","header":${apiKeyHeader?.let { "\"$it\"" } ?: "null"},"prefix":"Bearer ","placeholder":{"zh":"粘贴令牌","en":"Paste token"},"helpUrl":"https://example.com/help"}"""
            "oauth" -> """{"type":"oauth"}"""
            else -> """{"type":"none"}"""
        }
        return """
        {
          "schemaVersion": 3,
          "generatedAt": "2026-11-08T00:00:00Z",
          "keyId": "ec256:test",
          "entries": [
            {
              "type": "plugin",
              "id": "io.example.plugin",
              "name": {"zh": "插件", "en": "Plugin"},
              "summary": {"zh": "一句话", "en": "Summary"},
              "description": {"zh": "说明", "en": "Description"},
              "icon": "icons/plugin.png",
              "tags": {"zh": ["插件"], "en": ["Plugin"]},
              "version": "1.0.0",
              "artifact": {"url": "https://example.com/p.ddp", "sha256": "$sha256", "size": 1024},
              "plugin": {"apiLevel": 4, "minHostVersion": "0.2.0", "publisherKeyId": "plugin-key"}
            },
            {
              "type": "skill",
              "id": "$secondId",
              "name": {"zh": "技能", "en": "Skill"},
              "summary": {"zh": "一句话", "en": "Summary"},
              "description": {"zh": "说明", "en": "Description"},
              "tags": {"zh": ["技能"], "en": ["Skill"]},
              "version": "1.0.0",
              "artifact": {"url": "https://example.com/s.zip", "sha256": "$sha256", "size": 2048},
              "skill": {"license": "MIT", "source": "https://example.com"}
            },
            {
              "type": "mcp",
              "id": "example-mcp",
              "name": {"zh": "MCP", "en": "MCP"},
              "summary": {"zh": "一句话", "en": "Summary"},
              "description": {"zh": "说明", "en": "Description"},
              "tags": {"zh": ["MCP"], "en": ["MCP"]},
              "version": "1.0.0",
              "mcp": {"url": "https://mcp.example.com/mcp", "transport": "$transport", "headers": {"X-Existing": "secret"}, "authHint": {"zh": "首次授权", "en": "Authorize first"}, "auth": $auth}
            }
          ]
        }
        """.trimIndent()
    }
}
