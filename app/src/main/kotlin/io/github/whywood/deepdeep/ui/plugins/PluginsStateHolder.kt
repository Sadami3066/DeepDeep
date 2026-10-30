package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.PluginInteraction
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.plugin.PluginOperation
import io.github.whywood.deepdeep.plugin.PluginSnapshot
import io.github.whywood.deepdeep.plugin.PluginsUiState as ManagerPluginsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class PluginsUiState(
    val loading: Boolean = false,
    val plugins: List<PluginSnapshot> = emptyList(),
    val busy: Boolean = false,
    val operation: PluginOperation? = null,
    val interaction: PluginInteraction? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val togglingIds: Set<String> = emptySet(),
) {
    val external: List<PluginSnapshot> get() = plugins.filter { it.plugin.kind == PluginKind.EXTERNAL }

    val internal: List<PluginSnapshot> get() = plugins.filter { it.plugin.kind == PluginKind.INTERNAL }

    val interactive: Boolean get() = !busy && interaction == null

    fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.plugin.id == pluginId }

    fun isToggling(pluginId: String): Boolean = pluginId in togglingIds
}

internal interface PluginsDataSource {
    val state: StateFlow<ManagerPluginsUiState>

    suspend fun load()

    suspend fun refresh()

    suspend fun install(pluginId: String)

    suspend fun update(pluginId: String)

    suspend fun uninstall(pluginId: String)

    suspend fun runAction(pluginId: String, actionId: String)

    suspend fun setEnabled(pluginId: String, enabled: Boolean)

    suspend fun setSetting(pluginId: String, key: String, value: String)
}

internal class ManagerPluginsDataSource(
    private val manager: PluginManager,
) : PluginsDataSource {
    override val state: StateFlow<ManagerPluginsUiState> = manager.state

    override suspend fun load() {
        manager.refreshIfStale()
    }

    override suspend fun refresh() {
        manager.refresh()
    }

    override suspend fun install(pluginId: String) {
        manager.install(pluginId)
    }

    override suspend fun update(pluginId: String) {
        manager.update(pluginId)
    }

    override suspend fun uninstall(pluginId: String) {
        manager.uninstall(pluginId)
    }

    override suspend fun runAction(pluginId: String, actionId: String) {
        manager.runAction(pluginId, actionId)
    }

    override suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        manager.setEnabled(pluginId, enabled)
    }

    override suspend fun setSetting(pluginId: String, key: String, value: String) {
        manager.setSetting(pluginId, key, value)
    }
}

class PluginsStateHolder internal constructor(
    private val source: PluginsDataSource,
    private val scope: CoroutineScope,
) {
    constructor(manager: PluginManager, scope: CoroutineScope) : this(ManagerPluginsDataSource(manager), scope)

    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshPending = false
    private var retryAction: (suspend () -> Unit)? = null

    init {
        scope.launch {
            source.state.collect { managerState ->
                _state.update {
                    it.copy(
                        loading = managerState.loading,
                        plugins = managerState.plugins,
                        busy = managerState.busy,
                        operation = managerState.operation,
                        interaction = managerState.interaction,
                        error = managerState.error,
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    suspend fun retry() {
        val action = retryAction
        retryAction = null
        _state.update { it.copy(error = null) }
        if (action != null) {
            action()
        } else {
            load()
        }
    }

    suspend fun load() {
        reload { source.load() }
    }

    suspend fun refresh() {
        reload { source.refresh() }
    }

    suspend fun install(pluginId: String) {
        command { source.install(pluginId) }
    }

    suspend fun update(pluginId: String) {
        command { source.update(pluginId) }
    }

    suspend fun uninstall(pluginId: String) {
        command { source.uninstall(pluginId) }
    }

    suspend fun runAction(pluginId: String, actionId: String) {
        command { source.runAction(pluginId, actionId) }
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        if (pluginId in _state.value.togglingIds) return
        _state.update { it.copy(togglingIds = it.togglingIds + pluginId) }
        try {
            source.setEnabled(pluginId, enabled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(error) { setEnabled(pluginId, enabled) }
        } finally {
            _state.update { it.copy(togglingIds = it.togglingIds - pluginId) }
        }
    }

    suspend fun setSetting(pluginId: String, key: String, value: String) {
        command { source.setSetting(pluginId, key, value) }
    }

    private suspend fun reload(block: suspend () -> Unit) {
        if (refreshMutex.tryLock()) {
            try {
                do {
                    refreshPending = false
                    try {
                        block()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        setError(error) { reload(block) }
                    }
                } while (refreshPending)
            } finally {
                refreshMutex.unlock()
            }
        } else {
            refreshPending = true
        }
    }

    private suspend fun command(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(error) { command(block) }
        }
    }

    private fun setError(error: Exception, retry: suspend () -> Unit) {
        retryAction = retry
        _state.update {
            it.copy(error = error.message?.takeIf { message -> message.isNotBlank() } ?: error.javaClass.simpleName)
        }
    }
}
