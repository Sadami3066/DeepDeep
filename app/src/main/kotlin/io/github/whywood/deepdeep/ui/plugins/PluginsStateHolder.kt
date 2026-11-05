package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.ExternalInstallUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginUpdateUi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PluginsStateHolder(
    private val manager: PluginManagerFacade,
    private val scope: CoroutineScope,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _togglingIds = MutableStateFlow<Set<String>>(emptySet())
    val togglingIds: StateFlow<Set<String>> = _togglingIds.asStateFlow()

    fun setQuery(query: String) {
        _query.value = query
    }

    fun load() = manager.refreshIfStale()

    fun refresh() = manager.refresh()

    fun install(pluginId: String) = manager.install(pluginId)

    fun update(pluginId: String) = manager.update(pluginId)

    fun uninstall(pluginId: String) = manager.uninstall(pluginId)

    fun runAction(pluginId: String, actionId: String) = manager.runAction(pluginId, actionId)

    fun setEnabled(pluginId: String, enabled: Boolean) {
        if (pluginId in _togglingIds.value) return
        _togglingIds.update { it + pluginId }
        scope.launch {
            try {
                manager.setEnabled(pluginId, enabled)
            } finally {
                _togglingIds.update { it - pluginId }
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

    suspend fun stageUpdate(pluginId: String): String? =
        runCatching { manager.stageExternalUpdate(pluginId) }
            .getOrElse { error -> error.message ?: error.javaClass.simpleName }
}
