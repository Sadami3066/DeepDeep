package io.github.whywood.deepdeep.ui.common.market

import io.github.whywood.deepdeep.market.LocalizedText
import io.github.whywood.deepdeep.market.MarketArtifact
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketInstallState
import io.github.whywood.deepdeep.market.MarketRequiredAccess
import io.github.whywood.deepdeep.runtime.host.HostAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketLogicTest {
    private fun text(value: String) = LocalizedText(mapOf("zh" to value))

    private fun entry(
        id: String,
        name: String = id,
        nameText: LocalizedText? = null,
        summary: String = "",
        tags: List<String> = emptyList(),
        version: String = "1.0.0",
    ) = MarketEntry(
        type = MarketEntryType.Skill,
        id = id,
        name = nameText ?: text(name),
        summary = text(summary),
        description = LocalizedText(emptyMap()),
        icon = null,
        tags = if (tags.isEmpty()) emptyMap() else mapOf("zh" to tags),
        version = version,
        artifact = MarketArtifact(url = "", sha256 = "", size = 2048L),
    )

    @Test
    fun blankQueryKeepsAllEntries() {
        val entries = listOf(entry("a"), entry("b"))
        assertEquals(entries, filterMarketEntries(entries, "   ", "zh"))
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
        assertEquals(expected, filterMarketEntries(entries, "writ", "zh"))
    }

    @Test
    fun queryDoesNotMatchOtherFields() {
        val entries = listOf(entry("a", name = "Alpha"))
        assertTrue(filterMarketEntries(entries, "alpha-id", "zh").isEmpty())
    }

    @Test
    fun localizedTextResolvesRequestedLanguageAndFallsBackToChinese() {
        val bilingual = entry(
            id = "a",
            nameText = LocalizedText(mapOf("zh" to "写作", "en" to "Writer")),
        )
        assertEquals("Writer", bilingual.name("en-US"))
        assertEquals("写作", bilingual.name("zh"))
        val chineseOnly = entry(id = "b", nameText = LocalizedText(mapOf("zh" to "写作")))
        assertEquals("写作", chineseOnly.name("en"))
    }

    @Test
    fun filterUsesLanguageSpecificText() {
        val entries = listOf(
            entry(id = "a", nameText = LocalizedText(mapOf("zh" to "写作", "en" to "Writer"))),
        )
        assertEquals(1, filterMarketEntries(entries, "writ", "en").size)
        assertEquals(1, filterMarketEntries(entries, "写作", "zh").size)
        assertTrue(filterMarketEntries(entries, "写作", "en").isEmpty())
    }

    @Test
    fun tagsFallBackToChinese() {
        val candidate = entry("a", tags = listOf("写作"))
        assertEquals(listOf("写作"), candidate.tags("en"))
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

    @Test
    fun requiredAccessComparesAgainstHostAccess() {
        assertTrue(MarketRequiredAccess.Normal.satisfiedBy(HostAccess.LOCAL))
        assertTrue(MarketRequiredAccess.Adb.satisfiedBy(HostAccess.ADB))
        assertTrue(MarketRequiredAccess.Adb.satisfiedBy(HostAccess.ROOT))
        assertFalse(MarketRequiredAccess.Adb.satisfiedBy(HostAccess.LOCAL))
        assertTrue(MarketRequiredAccess.Root.satisfiedBy(HostAccess.ROOT))
        assertFalse(MarketRequiredAccess.Root.satisfiedBy(HostAccess.LOCAL))
        assertFalse(MarketRequiredAccess.Root.satisfiedBy(HostAccess.ADB))
    }
}
