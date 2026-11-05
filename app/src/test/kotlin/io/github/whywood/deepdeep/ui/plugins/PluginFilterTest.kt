package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginInteraction
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginFilterTest {
    @Test
    fun tabIndexMapsToSource() {
        assertEquals(PluginSource.EXTERNAL, pluginTabSource(0))
        assertEquals(PluginSource.BUILTIN, pluginTabSource(1))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(pluginMatchesQuery("", "feishu", "Feishu", "Connect Feishu"))
        assertTrue(pluginMatchesQuery("   ", "goal", "目标", "推进长期目标"))
    }

    @Test
    fun groupsPluginsBySourcePreservingOrder() {
        val plugins = listOf(
            snapshot("a", PluginSource.BUILTIN),
            snapshot("b", PluginSource.EXTERNAL),
            snapshot("c", PluginSource.BUILTIN),
        )

        assertEquals(listOf("b"), pluginItems(plugins, PluginSource.EXTERNAL).map { it.snapshot.id })
        assertEquals(listOf("a", "c"), pluginItems(plugins, PluginSource.BUILTIN).map { it.snapshot.id })
    }

    @Test
    fun reinstallOnlyForInstallableInstalledPlugins() {
        assertTrue(pluginShowsReinstall(snapshot("ddp", PluginSource.EXTERNAL, installable = true, installedVersion = "1.0")))
        assertFalse(pluginShowsReinstall(snapshot("cli", PluginSource.EXTERNAL, installable = true)))
        assertFalse(pluginShowsReinstall(snapshot("builtin", PluginSource.BUILTIN)))
    }

    @Test
    fun deleteOnlyForInstallableExternalPlugins() {
        assertTrue(pluginShowsDelete(snapshot("ddp", PluginSource.EXTERNAL, installable = true, installedVersion = "1.0")))
        assertTrue(pluginShowsDelete(snapshot("cli", PluginSource.EXTERNAL, installable = true)))
        assertFalse(pluginShowsDelete(snapshot("micloud", PluginSource.EXTERNAL)))
        assertFalse(pluginShowsDelete(snapshot("builtin", PluginSource.BUILTIN, installable = true)))
    }

    @Test
    fun listInteractiveUnlessBusyOrInteracting() {
        assertTrue(pluginListInteractive(PluginsUiState()))
        assertFalse(pluginListInteractive(PluginsUiState(busy = true)))
        assertFalse(
            pluginListInteractive(
                PluginsUiState(interaction = PluginInteraction(pluginId = "a", title = "Title", stageTitle = "Stage")),
            ),
        )
    }

    @Test
    fun catalogReadyOnlyAfterManagerLoaded() {
        assertFalse(pluginCatalogReady(PluginsUiState(loading = true)))
        assertFalse(pluginCatalogReady(PluginsUiState()))
        assertTrue(pluginCatalogReady(PluginsUiState(plugins = listOf(snapshot("a", PluginSource.BUILTIN)))))
        assertTrue(pluginCatalogReady(PluginsUiState(error = "boom")))
    }

    @Test
    fun detailMissingOnlyWhenCatalogLoaded() {
        val plugin = snapshot("a", PluginSource.BUILTIN)

        assertFalse(pluginDetailMissing(PluginsUiState(loading = true), "a"))
        assertFalse(pluginDetailMissing(PluginsUiState(), "a"))
        assertFalse(pluginDetailMissing(PluginsUiState(plugins = listOf(plugin)), "a"))
        assertTrue(pluginDetailMissing(PluginsUiState(plugins = listOf(plugin)), "missing"))
    }

    @Test
    fun matchesNameSummaryAndIdCaseInsensitively() {
        assertTrue(pluginMatchesQuery("FEISHU", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("sync", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("fei", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("目标", "goal", "目标", "跨回合推进长期目标"))
        assertFalse(pluginMatchesQuery("missing", "feishu", "Feishu", "Sync messages"))
    }

    private fun snapshot(
        id: String,
        source: PluginSource,
        installable: Boolean = false,
        installedVersion: String? = null,
    ): PluginSnapshot = PluginSnapshot(
        id = id,
        name = id,
        summary = id,
        description = null,
        version = "1.0",
        installedVersion = installedVersion,
        previousVersion = null,
        sizeLabel = null,
        icon = null,
        source = source,
        installable = installable,
        requiresPrivilege = false,
        enabled = true,
        phase = PluginPhaseUi.ACTIVE,
        availability = PluginAvailabilityUi.Available,
        status = null,
        updateAvailable = false,
        settingsValues = emptyMap(),
        settings = emptyList(),
        actions = emptyList(),
        detailRows = emptyList(),
        skills = emptyList(),
        installedSizeBytes = null,
        lastError = null,
        publisher = null,
    )
}
