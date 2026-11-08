package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.ComposerAction
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry
import io.github.whywood.deepdeep.plugin.host.update.PluginRepositorySettings
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.io.File
import kotlinx.coroutines.flow.StateFlow

data class PluginComposerActionUi(
    val pluginId: String,
    val action: ComposerAction,
    val pageId: String,
)

interface PluginManagerFacade {
    val state: StateFlow<PluginsUiState>

    suspend fun start()

    suspend fun refreshNow()

    fun refresh()

    fun refreshAndSyncSkills()

    fun refreshIfStale(minIntervalMillis: Long = 120_000L)

    fun tools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<dev.pi.coding.tools.CodingTool>

    fun deferredTools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<io.github.whywood.deepdeep.runtime.DeferredTool>

    fun promptBody(excludedPlugins: Set<String> = emptySet()): String?

    fun setEnabled(pluginId: String, enabled: Boolean)

    fun uninstall(pluginId: String)

    suspend fun inspectExternal(file: File): ExternalInspectionUi?

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi

    suspend fun checkExternalUpdates(): List<PluginUpdateUi>

    suspend fun stageExternalUpdate(pluginId: String): String?

    suspend fun applyPendingUpdates()

    suspend fun pluginRepositorySettings(): PluginRepositorySettings

    suspend fun savePluginRepositorySettings(settings: PluginRepositorySettings)

    suspend fun refreshAvailableUpdates(): List<PluginUpdateUi>

    suspend fun retryActivation(pluginId: String): PluginRecoveryResult

    suspend fun rollbackPlugin(pluginId: String): Boolean

    fun slotEntries(slotId: String): List<SlotRenderEntry>

    fun pagesOf(pluginId: String): List<PluginPage>

    fun composerActions(): List<PluginComposerActionUi>

    fun resolveText(text: LocalizedText): String
}
