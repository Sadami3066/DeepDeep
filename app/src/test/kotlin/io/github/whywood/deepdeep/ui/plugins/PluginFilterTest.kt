package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginFilterTest {
    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(pluginMatchesQuery("", "sample.echo", "Sample", "Echo plugin"))
        assertTrue(pluginMatchesQuery("   ", "goal", "目标", "推进长期目标"))
    }

    @Test
    fun filterPluginsPreservesOrder() {
        val plugins = listOf(snapshot("a"), snapshot("b"), snapshot("c"))

        assertEquals(listOf("a", "b", "c"), filterPlugins(plugins, "").map { it.id })
        assertEquals(listOf("b"), filterPlugins(plugins, "b").map { it.id })
    }

    @Test
    fun listInteractiveUnlessBusy() {
        assertTrue(pluginListInteractive(PluginsUiState()))
        assertFalse(pluginListInteractive(PluginsUiState(busy = true)))
    }

    @Test
    fun catalogReadyOnlyAfterManagerLoaded() {
        assertFalse(pluginCatalogReady(PluginsUiState(loading = true)))
        assertTrue(pluginCatalogReady(PluginsUiState()))
        assertTrue(pluginCatalogReady(PluginsUiState(plugins = listOf(snapshot("a")))))
        assertTrue(pluginCatalogReady(PluginsUiState(error = "boom")))
    }

    @Test
    fun matchesNameSummaryAndIdCaseInsensitively() {
        assertTrue(pluginMatchesQuery("SAMPLE", "sample.echo", "Sample", "Echo plugin"))
        assertTrue(pluginMatchesQuery("echo", "sample.echo", "Sample", "Echo plugin"))
        assertTrue(pluginMatchesQuery("目标", "goal", "目标", "跨回合推进长期目标"))
        assertFalse(pluginMatchesQuery("missing", "sample.echo", "Sample", "Echo plugin"))
    }

    @Test
    fun pagesExposeRowEntryPoint() {
        assertTrue(snapshot("a", pages = listOf("home")).hasPages)
        assertFalse(snapshot("a").hasPages)
    }

    private fun snapshot(
        id: String,
        pages: List<String> = emptyList(),
    ): PluginSnapshot = PluginSnapshot(
        id = id,
        name = id,
        summary = id,
        description = null,
        version = "1.0",
        installedVersion = "1.0",
        previousVersion = null,
        icon = null,
        enabled = true,
        phase = PluginPhaseUi.ACTIVE,
        availability = PluginAvailabilityUi.Available,
        pages = pages.map { io.github.whywood.deepdeep.plugin.manager.PluginPageUi(it, it) },
        skills = emptyList(),
        installedSizeBytes = null,
        lastError = null,
        publisher = null,
    )
}
