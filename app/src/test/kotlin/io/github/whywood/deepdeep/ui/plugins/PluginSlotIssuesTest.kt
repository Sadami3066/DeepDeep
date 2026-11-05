package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.host.PluginFailureMarker
import io.github.whywood.deepdeep.plugin.host.ui.SlotDisabledReason
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSlotIssuesTest {
    private fun entry(
        slotId: String,
        manifestVersion: String = "1.0.0",
        disabledReason: SlotDisabledReason? = null,
    ): SlotRenderEntry = SlotRenderEntry(
        pluginId = "alpha",
        manifestVersion = manifestVersion,
        slotId = slotId,
        priority = 0,
        content = SlotContent.Compose(uiApiLevel = 1) { },
        assetsDir = File("/tmp/alpha"),
        disabledReason = disabledReason,
    )

    private fun marker(key: String): PluginFailureMarker = PluginFailureMarker(key, "crash", 1L)

    @Test
    fun groupsDetailSectionsBySectionId() {
        val groups = detailSectionEntries(
            listOf(
                entry("plugin.detail.section:account"),
                entry("setup.content"),
                entry("plugin.detail.section:usage"),
                entry("plugin.detail.section:account"),
            ),
        )

        assertEquals(listOf("plugin.detail.section:account", "plugin.detail.section:usage"), groups.map { it.slotId })
        assertEquals(2, groups.first().entries.size)
        assertEquals(1, groups.last().entries.size)
    }

    @Test
    fun extractsSlotIdFromGuardKey() {
        assertEquals("setup.content", slotIdFromGuardKey("alpha@1.0.0:setup.content"))
        assertEquals("tool.card.content:echo", slotIdFromGuardKey("alpha@1.0.0:tool.card.content:echo"))
    }

    @Test
    fun crashMarkersAreFilteredToCurrentVersion() {
        val entries = listOf(entry("setup.content", manifestVersion = "2.0.0"))
        val markers = listOf(
            marker("alpha@1.0.0:setup.content"),
            marker("alpha@2.0.0:setup.content"),
            marker("beta@2.0.0:setup.content"),
        )

        assertEquals(listOf("alpha@2.0.0:setup.content"), crashMarkersForVersion("alpha", entries, markers).map { it.key })
    }

    @Test
    fun crashMarkersAllShownWhenPluginHasNoEntries() {
        val markers = listOf(marker("alpha@1.0.0:setup.content"), marker("beta@1.0.0:setup.content"))

        assertEquals(listOf("alpha@1.0.0:setup.content"), crashMarkersForVersion("alpha", emptyList(), markers).map { it.key })
    }

    @Test
    fun disabledEntriesCarryReason() {
        val disabled = listOf(
            entry("setup.content", disabledReason = SlotDisabledReason.ComposeVersionMismatch("1.9.0", "1.10.5")),
            entry("tool.card.content:echo"),
        ).filter { it.disabledReason != null }

        assertEquals(1, disabled.size)
        assertEquals(SlotDisabledReason.ComposeVersionMismatch("1.9.0", "1.10.5"), disabled.single().disabledReason)
        assertTrue(disabled.single().composeCompatible.not())
    }
}
