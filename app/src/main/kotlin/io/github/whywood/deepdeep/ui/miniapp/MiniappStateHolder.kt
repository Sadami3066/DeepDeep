package io.github.whywood.deepdeep.ui.miniapp

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.MiniappCreation
import io.github.whywood.deepdeep.runtime.MiniappInvalid
import io.github.whywood.deepdeep.runtime.MiniappScan
import io.github.whywood.deepdeep.runtime.MiniappStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MiniappError(
    @StringRes val messageRes: Int,
    val retryable: Boolean = false,
)

internal data class MiniappUiState(
    val loading: Boolean = true,
    val creations: List<MiniappCreation> = emptyList(),
    val invalid: List<MiniappInvalid> = emptyList(),
    val query: String = "",
    val error: MiniappError? = null,
    val busyId: String? = null,
) {
    val filtered: MiniappFilterResult get() = filterMiniapp(creations, invalid, query)

    val isEmpty: Boolean get() = creations.isEmpty() && invalid.isEmpty()
}

internal interface MiniappDataSource {
    suspend fun scan(): MiniappScan

    suspend fun delete(id: String): Boolean

    suspend fun rename(id: String, name: String): MiniappCreation?
}

internal class MiniappStoreSource(
    private val store: MiniappStore,
) : MiniappDataSource {
    override suspend fun scan(): MiniappScan = store.scan()

    override suspend fun delete(id: String): Boolean = store.delete(id)

    override suspend fun rename(id: String, name: String): MiniappCreation? = store.rename(id, name)
}

internal class MiniappStateHolder internal constructor(
    private val source: MiniappDataSource,
    private val scope: CoroutineScope,
) {
    constructor(store: MiniappStore, scope: CoroutineScope) : this(MiniappStoreSource(store), scope)

    private val _state = MutableStateFlow(MiniappUiState())
    val state: StateFlow<MiniappUiState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshPending = false
    private var retryAction: (suspend () -> Unit)? = null

    fun start() {
        scope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(loading = true) }
        try {
            refresh()
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }

    suspend fun refresh() {
        if (refreshMutex.tryLock()) {
            try {
                do {
                    refreshPending = false
                    scan()
                } while (refreshPending)
            } finally {
                refreshMutex.unlock()
            }
        } else {
            refreshPending = true
            refreshMutex.withLock { }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun retry() {
        val action = retryAction ?: return
        scope.launch { action() }
    }

    suspend fun delete(id: String) {
        _state.update { it.copy(busyId = id, error = null) }
        try {
            if (source.delete(id)) {
                refresh()
            } else {
                fail(MiniappError(R.string.miniapp_error_delete_failed))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fail(MiniappError(R.string.miniapp_error_delete_failed))
        } finally {
            _state.update { it.copy(busyId = null) }
        }
    }

    suspend fun rename(id: String, name: String) {
        _state.update { it.copy(busyId = id, error = null) }
        try {
            if (source.rename(id, name) != null) {
                refresh()
            } else {
                fail(MiniappError(R.string.chat_error_request_failed))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fail(MiniappError(R.string.chat_error_request_failed))
        } finally {
            _state.update { it.copy(busyId = null) }
        }
    }

    private suspend fun scan() {
        try {
            val result = source.scan()
            retryAction = null
            _state.update { it.copy(creations = result.creations, invalid = result.invalid, error = null) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fail(MiniappError(R.string.miniapp_error_scan_failed, retryable = true)) {
                refresh()
            }
        }
    }

    private fun fail(error: MiniappError, retry: suspend () -> Unit = {}) {
        retryAction = if (error.retryable) retry else null
        _state.update { it.copy(error = error) }
    }
}
