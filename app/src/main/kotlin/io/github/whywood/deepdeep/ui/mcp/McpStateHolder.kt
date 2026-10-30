package io.github.whywood.deepdeep.ui.mcp

import androidx.annotation.StringRes
import dev.pi.ai.RunSignal
import dev.pi.mcp.config.McpServerConfig
import dev.pi.mcp.oauth.McpOAuthInteraction
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.McpConfigSnapshot
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class McpError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int? = null,
    val detail: String? = null,
    val retryable: Boolean = true,
)

data class McpUiState(
    val loading: Boolean = true,
    val servers: List<McpServerEntry> = emptyList(),
    val notices: List<String> = emptyList(),
    val error: McpError? = null,
    val searchQuery: String = "",
    val probes: Map<String, McpProbeResult> = emptyMap(),
    val probingIds: Set<String> = emptySet(),
    val busyName: String? = null,
    val oauthSignedIn: Map<String, Boolean> = emptyMap(),
    val oauthBusyServer: String? = null,
) {
    val visibleServers: List<McpServerEntry> get() = filterMcpServers(servers, searchQuery)

    val probing: Boolean get() = probingIds.isNotEmpty()

    val toggleEnabled: Boolean get() = busyName == null
}

internal interface McpDataSource {
    suspend fun load(): McpConfigSnapshot

    suspend fun setEnabled(name: String, enabled: Boolean)

    suspend fun save(name: String, config: McpServerConfig)

    suspend fun remove(name: String)

    suspend fun probe(name: String, config: McpServerConfig): McpProbeResult

    suspend fun signIn(
        server: String,
        config: McpServerConfig.Http,
        interaction: McpOAuthInteraction,
        redirectUri: String,
        signal: RunSignal?,
    ): Result<Unit>

    suspend fun signOut(server: String, config: McpServerConfig.Http)
}

internal class RuntimeMcpDataSource(
    private val store: McpConfigStore,
    private val probe: McpServerProbe,
    private val oauth: McpOAuthService,
) : McpDataSource {
    override suspend fun load(): McpConfigSnapshot = store.load()

    override suspend fun setEnabled(name: String, enabled: Boolean) {
        store.setEnabled(name, enabled)
    }

    override suspend fun save(name: String, config: McpServerConfig) {
        store.upsert(name, config)
    }

    override suspend fun remove(name: String) {
        store.remove(name)
    }

    override suspend fun probe(name: String, config: McpServerConfig): McpProbeResult = probe.probe(name, config)

    override suspend fun signIn(
        server: String,
        config: McpServerConfig.Http,
        interaction: McpOAuthInteraction,
        redirectUri: String,
        signal: RunSignal?,
    ): Result<Unit> = oauth.signIn(server, config, interaction, redirectUri, signal)

    override suspend fun signOut(server: String, config: McpServerConfig.Http) {
        oauth.signOut(server, config)
    }
}

class McpStateHolder internal constructor(
    private val source: McpDataSource,
    private val scope: CoroutineScope,
    private val cache: McpProbeCache = McpProbeCache(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        store: McpConfigStore,
        probe: McpServerProbe,
        oauth: McpOAuthService,
        scope: CoroutineScope,
    ) : this(RuntimeMcpDataSource(store, probe, oauth), scope)

    private val _state = MutableStateFlow(McpUiState())
    val state: StateFlow<McpUiState> = _state.asStateFlow()

    private val reloadMutex = Mutex()
    private var reloadPending = false
    private var forcePending = false
    private var retryAction: (suspend () -> Unit)? = null

    suspend fun load() {
        reload(forceProbe = false)
    }

    suspend fun refresh() {
        reload(forceProbe = true)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun retry() {
        val action = retryAction ?: return
        retryAction = null
        _state.update { it.copy(error = null) }
        scope.launch { action() }
    }

    fun invalidateProbe(name: String) {
        cache.invalidate(name)
    }

    fun clearProbeCache() {
        cache.clear()
    }

    suspend fun setEnabled(name: String, enabled: Boolean): Boolean {
        if (_state.value.busyName != null) return false
        _state.update { it.copy(busyName = name) }
        return try {
            source.setEnabled(name, enabled)
            cache.invalidate(name)
            load()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(R.string.mcp_error_toggle_failed) { setEnabled(name, enabled) }
            false
        } finally {
            _state.update { it.copy(busyName = null) }
        }
    }

    suspend fun save(name: String, config: McpServerConfig): Boolean {
        if (_state.value.busyName != null) return false
        _state.update { it.copy(busyName = name) }
        return try {
            source.save(name, config)
            cache.invalidate(name)
            load()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(R.string.mcp_error_save_failed) { save(name, config) }
            false
        } finally {
            _state.update { it.copy(busyName = null) }
        }
    }

    suspend fun remove(name: String): Boolean {
        if (_state.value.busyName != null) return false
        _state.update { it.copy(busyName = name) }
        return try {
            source.remove(name)
            cache.invalidate(name)
            load()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(R.string.mcp_error_delete_failed) { remove(name) }
            false
        } finally {
            _state.update { it.copy(busyName = null) }
        }
    }

    suspend fun probe(name: String, config: McpServerConfig): McpProbeResult? {
        if (name in _state.value.probingIds) return null
        _state.update { it.copy(probingIds = it.probingIds + name) }
        return try {
            val result = source.probe(name, config)
            cache.store(name, config, result, clock())
            _state.update { it.copy(probes = it.probes + (name to result)) }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(R.string.mcp_status_failed, error.message) { probe(name, config) }
            null
        } finally {
            _state.update { it.copy(probingIds = it.probingIds - name) }
        }
    }

    suspend fun login(
        server: String,
        config: McpServerConfig.Http,
        interaction: McpOAuthInteraction,
        redirectUri: String,
        signal: RunSignal? = null,
    ): Boolean {
        if (_state.value.oauthBusyServer != null) return false
        _state.update { it.copy(oauthBusyServer = server) }
        return try {
            val result = source.signIn(server, config, interaction, redirectUri, signal)
            if (result.isSuccess) {
                _state.update { it.copy(oauthSignedIn = it.oauthSignedIn + (server to true)) }
                true
            } else {
                setError(null, result.exceptionOrNull()?.message) { login(server, config, interaction, redirectUri, signal) }
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(null, error.message) { login(server, config, interaction, redirectUri, signal) }
            false
        } finally {
            _state.update { it.copy(oauthBusyServer = null) }
        }
    }

    suspend fun logout(server: String, config: McpServerConfig.Http): Boolean {
        if (_state.value.oauthBusyServer != null) return false
        _state.update { it.copy(oauthBusyServer = server) }
        return try {
            source.signOut(server, config)
            _state.update { it.copy(oauthSignedIn = it.oauthSignedIn + (server to false)) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(null, error.message) { logout(server, config) }
            false
        } finally {
            _state.update { it.copy(oauthBusyServer = null) }
        }
    }

    private suspend fun reload(forceProbe: Boolean) {
        if (reloadMutex.tryLock()) {
            try {
                var force = forceProbe
                do {
                    reloadPending = false
                    force = force || forcePending
                    forcePending = false
                    doLoad(force)
                    force = false
                } while (reloadPending)
            } finally {
                reloadMutex.unlock()
            }
        } else {
            reloadPending = true
            if (forceProbe) forcePending = true
        }
    }

    private suspend fun doLoad(forceProbe: Boolean) {
        _state.update { it.copy(loading = it.servers.isEmpty()) }
        val snapshot = try {
            source.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setError(R.string.mcp_error_load_failed) { load() }
            _state.update { it.copy(loading = false) }
            return
        }
        val servers = snapshot.servers
            .map { (name, config) -> McpServerEntry(name, config) }
            .sortedBy { it.name.lowercase() }
        val targets = snapshot.servers
            .filter { (_, config) -> config.enabled && config is McpServerConfig.Http }
            .map { (name, config) -> name to config }
        val now = clock()
        cache.retain(snapshot.servers.keys)
        val cached = targets.mapNotNull { (name, config) ->
            cache.lookup(name, config, now)?.let { entry -> name to entry.result }
        }.toMap()
        val stale = if (forceProbe) {
            targets
        } else {
            targets.filter { (name, config) -> cache.lookup(name, config, now)?.fresh != true }
        }
        retryAction = null
        _state.update {
            it.copy(
                servers = servers,
                notices = snapshot.errors,
                probes = cached,
                error = null,
                loading = false,
            )
        }
        if (stale.isEmpty()) return
        _state.update { it.copy(probingIds = stale.map { target -> target.first }.toSet()) }
        try {
            val refreshed = coroutineScope {
                stale.map { (name, config) ->
                    async {
                        name to runCatching { source.probe(name, config) }.getOrElse { error ->
                            McpProbeResult(McpProbeState.FAILED, error = error.message)
                        }
                    }
                }.awaitAll()
            }
            val storedAt = clock()
            refreshed.forEach { (name, result) ->
                targets.firstOrNull { it.first == name }?.let { (_, config) ->
                    cache.store(name, config, result, storedAt)
                }
            }
            _state.update { it.copy(probes = it.probes + refreshed.toMap()) }
        } finally {
            _state.update { it.copy(probingIds = emptySet()) }
        }
    }

    private fun setError(
        @StringRes messageRes: Int?,
        detail: String? = null,
        retry: (suspend () -> Unit)? = null,
    ) {
        retryAction = retry
        _state.update {
            it.copy(
                error = McpError(
                    titleRes = R.string.mcp_error_title,
                    messageRes = messageRes,
                    detail = detail,
                    retryable = retry != null,
                ),
            )
        }
    }
}

internal fun filterMcpServers(entries: List<McpServerEntry>, query: String): List<McpServerEntry> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return entries
    return entries.filter { entry ->
        entry.name.contains(normalized, ignoreCase = true) ||
            entry.config.description?.contains(normalized, ignoreCase = true) == true ||
            (entry.config as? McpServerConfig.Http)?.url?.contains(normalized, ignoreCase = true) == true
    }
}
