package io.github.whywood.deepdeep.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRegistryParserTest {
    @Test
    fun parsesPluginsSkillsAndMcpEntries() {
        val registry = MarketRegistryParser.parse(registry().encodeToByteArray())

        assertEquals(2, registry.schemaVersion)
        assertEquals("ec256:test", registry.keyId)
        assertEquals(3, registry.entries.size)
        val plugin = registry.entries[0]
        assertEquals(MarketEntryType.Plugin, plugin.type)
        assertEquals("io.example.plugin", plugin.id)
        assertEquals("plugin-key", plugin.plugin?.publisherKeyId)
        val skill = registry.entries[1]
        assertEquals(MarketEntryType.Skill, skill.type)
        assertEquals("MIT", skill.skill?.license)
        val mcp = registry.entries[2]
        assertEquals(MarketEntryType.Mcp, mcp.type)
        assertEquals("http", mcp.mcp?.transport)
        assertEquals("https://mcp.example.com/mcp", mcp.mcp?.url)
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
            """{"type":"skill","id":"s$index","name":"n","summary":"s","version":"1","artifact":{"url":"https://example.com/$index.zip","sha256":"${"a".repeat(64)}","size":1}}"""
        }
        MarketRegistryParser.parse("""{"schemaVersion":2,"generatedAt":"now","keyId":"k","entries":[$entries]}""".encodeToByteArray())
    }

    @Test
    fun exposesEntryTypeSpecificMetadata() {
        val registry = MarketRegistryParser.parse(registry().encodeToByteArray())
        val tags = registry.entries.flatMap { it.tags }
        assertTrue(tags.containsAll(listOf("插件", "技能", "MCP")))
    }

    private fun registry(
        duplicateId: Boolean = false,
        sha256: String = "a".repeat(64),
        transport: String = "http",
    ): String {
        val secondId = if (duplicateId) "io.example.plugin" else "example-skill"
        return """
        {
          "schemaVersion": 2,
          "generatedAt": "2026-11-08T00:00:00Z",
          "keyId": "ec256:test",
          "entries": [
            {
              "type": "plugin",
              "id": "io.example.plugin",
              "name": "插件",
              "summary": "一句话",
              "description": "说明",
              "icon": "icons/plugin.png",
              "tags": ["插件"],
              "version": "1.0.0",
              "artifact": {"url": "https://example.com/p.ddp", "sha256": "$sha256", "size": 1024},
              "plugin": {"apiLevel": 4, "minHostVersion": "0.2.0", "publisherKeyId": "plugin-key"}
            },
            {
              "type": "skill",
              "id": "$secondId",
              "name": "技能",
              "summary": "一句话",
              "description": "说明",
              "tags": ["技能"],
              "version": "1.0.0",
              "artifact": {"url": "https://example.com/s.zip", "sha256": "$sha256", "size": 2048},
              "skill": {"license": "MIT", "source": "https://example.com"}
            },
            {
              "type": "mcp",
              "id": "example-mcp",
              "name": "MCP",
              "summary": "一句话",
              "description": "说明",
              "tags": ["MCP"],
              "version": "1.0.0",
              "mcp": {"url": "https://mcp.example.com/mcp", "transport": "$transport", "headers": {}, "authHint": "首次授权"}
            }
          ]
        }
        """.trimIndent()
    }
}
