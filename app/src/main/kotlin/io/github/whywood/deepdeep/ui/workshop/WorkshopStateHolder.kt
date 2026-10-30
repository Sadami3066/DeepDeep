package io.github.whywood.deepdeep.ui.workshop

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.WorkshopCreation
import io.github.whywood.deepdeep.runtime.WorkshopInvalid
import io.github.whywood.deepdeep.runtime.WorkshopScan
import io.github.whywood.deepdeep.runtime.WorkshopStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class WorkshopError(
    @StringRes val messageRes: Int,
    val retryable: Boolean = false,
)

internal data class WorkshopUiState(
    val loading: Boolean = true,
    val creations: List<WorkshopCreation> = emptyList(),
    val invalid: List<WorkshopInvalid> = emptyList(),
    val query: String = "",
    val error: WorkshopError? = null,
    val busyId: String? = null,
) {
    val filtered: WorkshopFilterResult get() = filterWorkshop(creations, invalid, query)

    val isEmpty: Boolean get() = creations.isEmpty() && invalid.isEmpty()
}

internal interface WorkshopDataSource {
    suspend fun scan(): WorkshopScan

    suspend fun delete(id: String): Boolean

    suspend fun rename(id: String, name: String): WorkshopCreation?
}

internal class WorkshopStoreSource(
    private val store: WorkshopStore,
) : WorkshopDataSource {
    override suspend fun scan(): WorkshopScan = store.scan()

    override suspend fun delete(id: String): Boolean = store.delete(id)

    override suspend fun rename(id: String, name: String): WorkshopCreation? = store.rename(id, name)
}

internal class WorkshopStateHolder internal constructor(
    private val source: WorkshopDataSource,
    private val scope: CoroutineScope,
) {
    constructor(store: WorkshopStore, scope: CoroutineScope) : this(WorkshopStoreSource(store), scope)

    private val _state = MutableStateFlow(WorkshopUiState())
    val state: StateFlow<WorkshopUiState> = _state.asStateFlow()

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
                fail(WorkshopError(R.string.workshop_error_delete_failed))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fail(WorkshopError(R.string.workshop_error_delete_failed))
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
                fail(WorkshopError(R.string.chat_error_request_failed))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fail(WorkshopError(R.string.chat_error_request_failed))
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
            fail(WorkshopError(R.string.workshop_error_scan_failed, retryable = true)) {
                refresh()
            }
        }
    }

    private fun fail(error: WorkshopError, retry: suspend () -> Unit = {}) {
        retryAction = if (error.retryable) retry else null
        _state.update { it.copy(error = error) }
    }
}
