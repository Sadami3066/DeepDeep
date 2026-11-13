package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.ExternalInstallUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginRecoveryResult
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

    private val _updatingIds = MutableStateFlow<Set<String>>(emptySet())
    val updatingIds: StateFlow<Set<String>> = _updatingIds.asStateFlow()

    fun setQuery(query: String) {
        _query.value = query
    }

    fun load() = manager.refreshIfStale()

    fun refresh() = manager.refresh()

    fun uninstall(pluginId: String) = manager.uninstall(pluginId)

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

    fun applyPendingUpdate(pluginId: String) {
        if (pluginId in _updatingIds.value) return
        _updatingIds.update { it + pluginId }
        scope.launch {
            try {
                manager.applyPendingUpdate(pluginId)
            } finally {
                _updatingIds.update { it - pluginId }
            }
        }
    }

    suspend fun inspectExternal(file: File): ExternalInspectionUi? = manager.inspectExternal(file)

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        manager.installExternal(file, acceptPublisher)

    suspend fun retryActivation(pluginId: String): PluginRecoveryResult =
        runCatching { manager.retryActivation(pluginId) }
            .getOrElse { error ->
                PluginRecoveryResult(false, error.message ?: error.javaClass.simpleName)
            }

    suspend fun rollback(pluginId: String): Boolean =
        runCatching { manager.rollbackPlugin(pluginId) }.getOrDefault(false)
}
