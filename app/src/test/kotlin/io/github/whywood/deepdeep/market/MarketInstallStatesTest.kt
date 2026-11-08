package io.github.whywood.deepdeep.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketInstallStatesTest {
    @Test
    fun removedPluginReturnsToNotInstalled() {
        val entry = entry(MarketEntryType.Plugin, "plugin.alpha", "1.0.0")
        val installed = resolveInstallStates(
            entries = listOf(entry),
            installed = InstalledContent(plugins = mapOf("plugin.alpha" to "1.0.0")),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.Installed("1.0.0"), installed["plugin.alpha"])

        val removed = resolveInstallStates(
            entries = listOf(entry),
            installed = InstalledContent(),
            previous = installed,
        )
        assertEquals(MarketInstallState.NotInstalled, removed["plugin.alpha"])
    }

    @Test
    fun removedSkillAndMcpReturnToNotInstalled() {
        val skill = entry(MarketEntryType.Skill, "skill.alpha", "2.0.0")
        val mcp = entry(MarketEntryType.Mcp, "mcp.alpha", "3.0.0")
        val entries = listOf(skill, mcp)
        val installed = resolveInstallStates(
            entries = entries,
            installed = InstalledContent(
                skills = mapOf("skill.alpha" to "2.0.0"),
                mcp = mapOf("mcp.alpha" to MCP_URL),
            ),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.Installed("2.0.0"), installed["skill.alpha"])
        assertEquals(MarketInstallState.Installed("3.0.0"), installed["mcp.alpha"])

        val removed = resolveInstallStates(entries, InstalledContent(), installed)
        assertEquals(MarketInstallState.NotInstalled, removed["skill.alpha"])
        assertEquals(MarketInstallState.NotInstalled, removed["mcp.alpha"])
    }

    @Test
    fun olderInstalledVersionShowsUpdateAvailable() {
        val entry = entry(MarketEntryType.Plugin, "plugin.alpha", "1.2.0")
        val states = resolveInstallStates(
            entries = listOf(entry),
            installed = InstalledContent(plugins = mapOf("plugin.alpha" to "1.1.0")),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.UpdateAvailable("1.1.0"), states["plugin.alpha"])
    }

    @Test
    fun installingStateSurvivesRecompute() {
        val entry = entry(MarketEntryType.Skill, "skill.alpha", "1.0.0")
        val previous = mapOf<String, MarketInstallState>("skill.alpha" to MarketInstallState.Installing(null))
        val states = resolveInstallStates(listOf(entry), InstalledContent(), previous)
        assertEquals(MarketInstallState.Installing(null), states["skill.alpha"])
    }

    @Test
    fun failedStateIsRecomputedForRetry() {
        val entry = entry(MarketEntryType.Plugin, "plugin.alpha", "1.0.0")
        val previous = mapOf<String, MarketInstallState>("plugin.alpha" to MarketInstallState.Failed("下载失败"))
        val states = resolveInstallStates(listOf(entry), InstalledContent(), previous)
        assertEquals(MarketInstallState.NotInstalled, states["plugin.alpha"])
    }

    @Test
    fun installedVersionReadsPluginAndStoredSkillVersions() {
        val plugin = entry(MarketEntryType.Plugin, "plugin.alpha", "9.0.0")
        val skill = entry(MarketEntryType.Skill, "skill.alpha", "2.0.0")
        val mcp = entry(MarketEntryType.Mcp, "mcp.alpha", "3.0.0")
        val content = InstalledContent(
            plugins = mapOf("plugin.alpha" to "8.0.0"),
            skills = mapOf("skill.alpha" to null),
            mcp = mapOf("renamed" to MCP_URL),
        )
        assertEquals("8.0.0", content.versionOf(plugin))
        assertEquals("2.0.0", content.versionOf(skill))
        assertEquals("3.0.0", content.versionOf(mcp))
        assertNull(content.versionOf(entry(MarketEntryType.Skill, "skill.other", "1.0.0")))
    }

    @Test
    fun staleSkillVersionShowsUpdateAvailable() {
        val skill = entry(MarketEntryType.Skill, "skill.alpha", "2.0.0")
        val states = resolveInstallStates(
            entries = listOf(skill),
            installed = InstalledContent(skills = mapOf("skill.alpha" to "1.0.0")),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.UpdateAvailable("1.0.0"), states["skill.alpha"])
    }

    @Test
    fun renamedMcpServerIsStillInstalled() {
        val mcp = entry(MarketEntryType.Mcp, "mcp.alpha", "3.0.0")
        val states = resolveInstallStates(
            entries = listOf(mcp),
            installed = InstalledContent(mcp = mapOf("my-docs" to MCP_URL)),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.Installed("3.0.0"), states["mcp.alpha"])
    }

    @Test
    fun mcpServerWithDifferentUrlIsNotInstalled() {
        val mcp = entry(MarketEntryType.Mcp, "mcp.alpha", "3.0.0")
        val states = resolveInstallStates(
            entries = listOf(mcp),
            installed = InstalledContent(mcp = mapOf("mcp.alpha" to "https://other.example.com/mcp")),
            previous = emptyMap(),
        )
        assertEquals(MarketInstallState.NotInstalled, states["mcp.alpha"])
    }

    @Test
    fun uniqueMcpNameKeepsIdWhenFree() {
        assertEquals("mcp.alpha", uniqueMcpName(emptySet(), "mcp.alpha"))
    }

    @Test
    fun uniqueMcpNameAvoidsExistingNames() {
        assertEquals("mcp.alpha-2", uniqueMcpName(setOf("mcp.alpha"), "mcp.alpha"))
        assertEquals("mcp.alpha-3", uniqueMcpName(setOf("mcp.alpha", "mcp.alpha-2"), "mcp.alpha"))
    }

    private fun entry(type: MarketEntryType, id: String, version: String): MarketEntry = MarketEntry(
        type = type,
        id = id,
        name = LocalizedText(mapOf("zh" to id)),
        summary = LocalizedText(mapOf("zh" to id)),
        description = LocalizedText(mapOf("zh" to id)),
        icon = null,
        tags = emptyMap(),
        version = version,
        artifact = MarketArtifact("https://example.com/$id", "0".repeat(64), 1024L),
        mcp = if (type == MarketEntryType.Mcp) {
            MarketMcpMeta(url = MCP_URL, transport = "http", headers = emptyMap(), authHint = null)
        } else {
            null
        },
    )

    private companion object {
        const val MCP_URL = "https://mcp.example.com/mcp"
    }
}
