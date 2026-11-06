package io.github.whywood.deepdeep.service

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProcessState {
    IDLE,
    STARTING,
    STARTED,
    FAILED,
}

class ProcessRuntime(
    private val scope: CoroutineScope,
    private val prepare: suspend () -> Unit,
    private val installBundledSkills: suspend () -> Unit,
    private val startPlugins: suspend () -> Unit,
    private val refreshPluginsIfStale: (Long) -> Unit,
    private val reconfigureHost: suspend () -> Unit,
    private val ensureHostStarted: suspend () -> Unit,
    private val syncSkills: suspend () -> Unit,
    private val diagnosticsDir: File,
) {
    private val mutex = Mutex()
    private var startGate: CompletableDeferred<Unit>? = null
    private val _state = MutableStateFlow(ProcessState.IDLE)
    val state: StateFlow<ProcessState> = _state.asStateFlow()

    fun startAsync() {
        scope.launch { runCatching { start() } }
    }

    suspend fun start() {
        var owner = false
        val gate = mutex.withLock {
            if (_state.value == ProcessState.STARTED) return
            startGate ?: CompletableDeferred<Unit>().also {
                startGate = it
                owner = true
            }
        }
        if (!owner) {
            gate.await()
            return
        }
        try {
            runStart()
            gate.complete(Unit)
        } catch (error: Throwable) {
            gate.completeExceptionally(error)
            throw error
        } finally {
            mutex.withLock { if (startGate === gate) startGate = null }
        }
    }

    fun onForeground() {
        refreshPluginsIfStale(PLUGIN_REFRESH_MIN_INTERVAL_MS)
        scope.launch {
            runCatching { syncSkills() }.onFailure {
                Diagnostics.log(diagnosticsDir, "process: foreground skill sync failed", it)
            }
        }
    }

    private suspend fun runStart() {
        _state.value = ProcessState.STARTING
        try {
            runCatching { installBundledSkills() }.onFailure {
                Diagnostics.log(diagnosticsDir, "process: bundled skill sync failed", it)
            }
            runCatching { startPlugins() }.onFailure { Diagnostics.log(diagnosticsDir, "process: plugin start failed", it) }
            prepare()
            reconfigureHost()
            ensureHostStarted()
            _state.value = ProcessState.STARTED
        } catch (error: Throwable) {
            Diagnostics.log(diagnosticsDir, "process: start failed", error)
            _state.value = ProcessState.FAILED
            throw error
        }
    }

    companion object {
        const val PLUGIN_REFRESH_MIN_INTERVAL_MS = 120_000L
    }
}
