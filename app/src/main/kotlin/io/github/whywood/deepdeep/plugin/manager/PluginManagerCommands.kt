package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PluginManagerCommands(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val strings: PluginStrings,
    private val gate: PluginRuntimeRegistry,
    private val registry: PluginRegistry,
    private val lifecycle: PluginLifecycle,
    private val external: ExternalPluginRuntime?,
    private val externalFlow: PluginExternalFlow,
    private val busy: AtomicBoolean,
    private val mutex: Mutex,
    private val refresh: suspend () -> Unit,
    private val clearStartupCount: suspend (String) -> Boolean,
    private val onContributionsChanged: () -> Unit,
) {
    fun setEnabled(pluginId: String, enabled: Boolean) {
        scope.launch {
            mutex.withLock {
                if (enabled) enablePlugin(pluginId) else disablePlugin(pluginId)
            }
        }
    }

    private suspend fun enablePlugin(pluginId: String) {
        val view = gate.view(pluginId) ?: return
        if (view.enabled) return
        externalFlow.persistEnabled(pluginId, true)
        gate.setEnabled(pluginId, true)
        clearStartupCount(pluginId)
        externalFlow.loadForEnable(pluginId)
        val failure = gate.view(pluginId)?.let { current -> lifecycle.enable(current) }
        externalFlow.recordFailure(pluginId, failure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
    }

    private suspend fun disablePlugin(pluginId: String) {
        val view = gate.view(pluginId) ?: return
        if (!view.enabled) return
        externalFlow.persistEnabled(pluginId, false)
        gate.setEnabled(pluginId, false)
        lifecycle.disable(pluginId)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
    }

    fun uninstall(pluginId: String) {
        scope.launch {
            launchBusy {
                val view = gate.view(pluginId) ?: return@launchBusy
                val skillIds = (view.skillIds + view.contributions.skills.map { it.id }).distinct()
                lifecycle.uninstallPlugin(view)?.let { error -> gate.markError(pluginId, error) }
                lifecycle.removeSkills(pluginId, skillIds)
                external?.uninstall(pluginId)
                gate.release(pluginId)
                registry.unregister(pluginId)
                refresh()
                onContributionsChanged()
            }
        }
    }

    suspend fun retryActivation(pluginId: String): PluginRecoveryResult = mutex.withLock {
        val runtime = external
            ?: return PluginRecoveryResult(false, strings.get(R.string.plugins_error_unknown))
        val record = runtime.record(pluginId)
            ?: return PluginRecoveryResult(false, strings.get(R.string.plugins_error_unknown))
        runtime.completeActivation(pluginId)
        runtime.clearStartupCount(pluginId)
        gate.clearFailure(pluginId)
        if (!record.enabled) {
            runtime.setEnabled(pluginId, true)
            gate.setEnabled(pluginId, true)
        }
        externalFlow.activatePendingVersions()
        if (gate.view(pluginId)?.plugin == null) externalFlow.loadForEnable(pluginId)
        val failure = gate.view(pluginId)?.let { view -> lifecycle.enable(view) }
        externalFlow.recordFailure(pluginId, failure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
        val view = gate.view(pluginId)
        PluginRecoveryResult(
            ok = view?.plugin != null && failure == null,
            error = failure,
        )
    }

    suspend fun rollbackPlugin(pluginId: String): Boolean = mutex.withLock {
        val runtime = external ?: return false
        if (gate.view(pluginId) == null) return false
        lifecycle.deactivate(pluginId)
        val record = runtime.rollbackToPrevious(pluginId)
        if (record == null) {
            refresh()
            return false
        }
        runtime.completeActivation(pluginId)
        runtime.clearStartupCount(pluginId)
        runtime.pruneVersions(pluginId)
        gate.attachExternalRecord(pluginId, record)
        gate.updateRecord(pluginId) {
            it.copy(
                enabled = record.enabled,
                installedVersion = record.version,
                pendingVersion = null,
                previousVersion = null,
                lastError = null,
            )
        }
        gate.clearFailure(pluginId)
        externalFlow.loadForEnable(pluginId)
        val rollbackFailure = gate.view(pluginId)?.let { view -> lifecycle.enable(view) }
        externalFlow.recordFailure(pluginId, rollbackFailure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
        gate.view(pluginId)?.plugin != null
    }

    fun setError(message: String) {
        state.update { it.copy(error = message) }
    }

    private suspend fun launchBusy(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            setError(strings.get(R.string.plugins_error_busy))
            return
        }
        state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setError(error.message ?: error.javaClass.simpleName)
        } finally {
            busy.set(false)
            state.update { it.copy(busy = false) }
            runCatching { refresh() }
            onContributionsChanged()
        }
    }
}
