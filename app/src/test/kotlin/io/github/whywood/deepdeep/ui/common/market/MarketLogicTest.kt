package io.github.whywood.deepdeep.ui.common.market

import io.github.whywood.deepdeep.market.MarketArtifact
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketInstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketLogicTest {
    private fun entry(
        id: String,
        name: String = id,
        summary: String = "",
        tags: List<String> = emptyList(),
        version: String = "1.0.0",
    ) = MarketEntry(
        type = MarketEntryType.Skill,
        id = id,
        name = name,
        summary = summary,
        description = "",
        icon = null,
        tags = tags,
        version = version,
        artifact = MarketArtifact(url = "", sha256 = "", size = 2048L),
    )

    @Test
    fun blankQueryKeepsAllEntries() {
        val entries = listOf(entry("a"), entry("b"))
        assertEquals(entries, filterMarketEntries(entries, "   "))
    }

    @Test
    fun queryMatchesNameSummaryAndTagsIgnoringCase() {
        val entries = listOf(
            entry("a", name = "Writer"),
            entry("b", summary = "Write meeting notes"),
            entry("c", tags = listOf("Writing")),
            entry("d", name = "Other"),
        )
        val expected = listOf(entries[0], entries[1], entries[2])
        assertEquals(expected, filterMarketEntries(entries, "writ"))
    }

    @Test
    fun queryDoesNotMatchOtherFields() {
        val entries = listOf(entry("a", name = "Alpha"))
        assertTrue(filterMarketEntries(entries, "alpha-id").isEmpty())
    }

    @Test
    fun installedVersionDecidesStatusWithoutInstallState() {
        val candidate = entry("a", version = "2.0.0")
        assertEquals(MarketEntryStatus.Install, marketEntryStatus(candidate, null, null))
        assertEquals(MarketEntryStatus.Installed, marketEntryStatus(candidate, null, "2.0.0"))
        assertEquals(MarketEntryStatus.Update, marketEntryStatus(candidate, null, "1.0.0"))
    }

    @Test
    fun installStateOverridesInstalledVersion() {
        val candidate = entry("a", version = "2.0.0")
        assertEquals(
            MarketEntryStatus.Installing,
            marketEntryStatus(candidate, MarketInstallState.Installing(null), null),
        )
        assertEquals(
            MarketEntryStatus.Installed,
            marketEntryStatus(candidate, MarketInstallState.Installed("2.0.0"), "1.0.0"),
        )
        assertEquals(
            MarketEntryStatus.Update,
            marketEntryStatus(candidate, MarketInstallState.UpdateAvailable("1.0.0"), "1.0.0"),
        )
        assertEquals(
            MarketEntryStatus.Failed,
            marketEntryStatus(candidate, MarketInstallState.Failed("boom"), null),
        )
        assertEquals(
            MarketEntryStatus.Install,
            marketEntryStatus(candidate, MarketInstallState.NotInstalled, null),
        )
    }

    @Test
    fun sizeFormattingUsesBinaryUnits() {
        assertEquals("512 B", formatMarketSize(512L))
        assertEquals("1 KB", formatMarketSize(1024L))
        assertEquals("1.5 KB", formatMarketSize(1536L))
        assertEquals("2 MB", formatMarketSize(2L * 1024L * 1024L))
        assertEquals("1.3 GB", formatMarketSize((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun iconUrlRequiresRelativePath() {
        assertNull(marketIconUrl(null))
        assertNull(marketIconUrl("  "))
        assertEquals(
            "https://sadami3066.github.io/deepdeep-market/icons/a.png",
            marketIconUrl("icons/a.png"),
        )
        assertEquals(
            "https://sadami3066.github.io/deepdeep-market/icons/a.png",
            marketIconUrl("/icons/a.png"),
        )
    }
}
