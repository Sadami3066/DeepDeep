package io.github.whywood.deepdeep.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketAutoUpdatePlannerTest {
    private var now = 1_000L
    private val planner = MarketAutoUpdatePlanner(retryIntervalMs = 60_000L, clock = { now })

    @Test
    fun plansOnlyPluginAndSkillUpdates() {
        val plugin = entry(MarketEntryType.Plugin, "plugin.alpha")
        val skill = entry(MarketEntryType.Skill, "skill.alpha")
        val mcp = entry(MarketEntryType.Mcp, "mcp.alpha")
        val plan = planner.plan(
            entries = listOf(plugin, skill, mcp),
            installStates = mapOf(
                "plugin.alpha" to MarketInstallState.UpdateAvailable("1.0.0"),
                "skill.alpha" to MarketInstallState.UpdateAvailable("1.0.0"),
                "mcp.alpha" to MarketInstallState.UpdateAvailable("1.0.0"),
            ),
        )
        assertEquals(listOf("plugin.alpha", "skill.alpha"), plan.map(MarketEntry::id))
    }

    @Test
    fun notInstalledAndInstalledEntriesAreSkipped() {
        val missing = entry(MarketEntryType.Plugin, "plugin.missing")
        val current = entry(MarketEntryType.Skill, "skill.current")
        val plan = planner.plan(
            entries = listOf(missing, current),
            installStates = mapOf(
                "plugin.missing" to MarketInstallState.NotInstalled,
                "skill.current" to MarketInstallState.Installed("1.0.0"),
            ),
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun repeatedPlanWithinIntervalIsEmpty() {
        val plugin = entry(MarketEntryType.Plugin, "plugin.alpha")
        val states = mapOf<String, MarketInstallState>("plugin.alpha" to MarketInstallState.UpdateAvailable("1.0.0"))
        assertEquals(1, planner.plan(listOf(plugin), states).size)
        planner.markAttempt("plugin.alpha")
        now += 1_000L
        assertTrue(planner.plan(listOf(plugin), states).isEmpty())
    }

    @Test
    fun failedUpdateIsRetriedAfterInterval() {
        val plugin = entry(MarketEntryType.Plugin, "plugin.alpha")
        val states = mapOf<String, MarketInstallState>("plugin.alpha" to MarketInstallState.UpdateAvailable("1.0.0"))
        planner.markAttempt("plugin.alpha")
        assertTrue(planner.plan(listOf(plugin), states).isEmpty())
        now += 60_000L
        assertEquals(1, planner.plan(listOf(plugin), states).size)
    }

    @Test
    fun successfulUpdateClearsThrottle() {
        val plugin = entry(MarketEntryType.Plugin, "plugin.alpha")
        planner.markAttempt("plugin.alpha")
        planner.markInstalled("plugin.alpha")
        val states = mapOf<String, MarketInstallState>("plugin.alpha" to MarketInstallState.UpdateAvailable("1.0.0"))
        assertEquals(1, planner.plan(listOf(plugin), states).size)
    }

    @Test
    fun staleSkillVersionIsPlannedForUpdate() {
        val skill = entry(MarketEntryType.Skill, "skill.alpha")
        val states = resolveInstallStates(
            entries = listOf(skill),
            installed = InstalledContent(skills = mapOf("skill.alpha" to "1.0.0")),
            previous = emptyMap(),
        )
        assertEquals(listOf("skill.alpha"), planner.plan(listOf(skill), states).map(MarketEntry::id))
    }

    private fun entry(type: MarketEntryType, id: String): MarketEntry = MarketEntry(
        type = type,
        id = id,
        name = LocalizedText(mapOf("zh" to id)),
        summary = LocalizedText(mapOf("zh" to id)),
        description = LocalizedText(mapOf("zh" to id)),
        icon = null,
        tags = emptyMap(),
        version = "2.0.0",
        artifact = MarketArtifact("https://example.com/$id", "0".repeat(64), 1024L),
    )
}
