package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.ExternalInstallUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginInteraction
import io.github.whywood.deepdeep.plugin.manager.PluginOperation
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginUpdateUi
import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState as ManagerPluginsUiState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PluginsUiState(
    val loading: Boolean = false,
    val plugins: List<PluginSnapshot> = emptyList(),
    val busy: Boolean = false,
    val operation: PluginOperation? = null,
    val interaction: PluginInteraction? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val togglingIds: Set<String> = emptySet(),
    val availableUpdates: List<PluginUpdateUi> = emptyList(),
) {
    val external: List<PluginSnapshot> get() = plugins.filter { it.source == PluginSource.EXTERNAL }

    val builtin: List<PluginSnapshot> get() = plugins.filter { it.source == PluginSource.BUILTIN }

    val interactive: Boolean get() = !busy && interaction == null

    fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.id == pluginId }

    fun isToggling(pluginId: String): Boolean = pluginId in togglingIds
}

internal class PluginsStateHolder(
    private val manager: PluginManagerFacade,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            manager.state.collect { managerState -> mirror(managerState) }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun load() {
        manager.refreshIfStale()
    }

    fun refresh() {
        manager.refresh()
    }

    fun refreshNow() {
        scope.launch { runCatching { manager.refreshNow() } }
    }

    fun install(pluginId: String) = manager.install(pluginId)

    fun update(pluginId: String) = manager.update(pluginId)

    fun uninstall(pluginId: String) = manager.uninstall(pluginId)

    fun runAction(pluginId: String, actionId: String) = manager.runAction(pluginId, actionId)

    fun connect(pluginId: String) = manager.connect(pluginId)

    fun confirmInstall() = manager.confirmInstall()

    fun cancelInteraction() = manager.cancelInteraction()

    fun setEnabled(pluginId: String, enabled: Boolean) {
        if (pluginId in _state.value.togglingIds) return
        _state.update { it.copy(togglingIds = it.togglingIds + pluginId) }
        scope.launch {
            try {
                manager.setEnabled(pluginId, enabled)
            } finally {
                _state.update { it.copy(togglingIds = it.togglingIds - pluginId) }
            }
        }
    }

    fun setSetting(pluginId: String, key: String, value: String) = manager.setSetting(pluginId, key, value)

    suspend fun inspectExternal(file: File): ExternalInspectionUi? = manager.inspectExternal(file)

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        manager.installExternal(file, acceptPublisher)

    suspend fun checkExternalUpdates(): List<PluginUpdateUi> = manager.checkExternalUpdates()

    suspend fun loadSourceSettings(): PluginSourceSettings = manager.pluginSourceSettings()

    fun saveSourceSettings(settings: PluginSourceSettings) {
        scope.launch {
            runCatching { manager.savePluginSourceSettings(settings) }
            runCatching { manager.refreshAvailableUpdates() }
        }
    }

    fun stageUpdate(pluginId: String) {
        scope.launch {
            runCatching { manager.stageExternalUpdate(pluginId) }
            runCatching { manager.refreshNow() }
        }
    }

    private fun mirror(managerState: ManagerPluginsUiState) {
        _state.update {
            it.copy(
                loading = managerState.loading,
                plugins = managerState.plugins,
                busy = managerState.busy,
                operation = managerState.operation,
                interaction = managerState.interaction,
                error = managerState.error,
                availableUpdates = managerState.availableUpdates,
            )
        }
    }
}
