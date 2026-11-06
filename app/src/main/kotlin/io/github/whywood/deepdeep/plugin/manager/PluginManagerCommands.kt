package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.normalize
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.runtime.PluginSetupResult
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
    private val settingsStore: PluginSettingsStore,
    private val builtinStateStore: BuiltinPluginStateStore,
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
        persistEnabled(view, true)
        gate.setEnabled(pluginId, true)
        clearStartupCount(pluginId)
        if (!view.builtin) externalFlow.loadForEnable(pluginId)
        val failure = gate.view(pluginId)?.let { current -> lifecycle.activate(current) }
        if (!view.builtin) externalFlow.recordFailure(pluginId, failure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
    }

    private suspend fun disablePlugin(pluginId: String) {
        val view = gate.view(pluginId) ?: return
        if (!view.enabled) return
        persistEnabled(view, false)
        gate.setEnabled(pluginId, false)
        lifecycle.deactivate(pluginId)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
    }

    fun setSetting(pluginId: String, key: String, value: String) {
        scope.launch {
            mutex.withLock {
                val view = gate.view(pluginId) ?: return@withLock
                val spec = view.contributions.settings.settingLeaves().firstOrNull { it.key == key } ?: return@withLock
                val normalized = spec.normalize(value) ?: return@withLock
                settingsStore.set(pluginId, key, normalized)
                val current = gate.view(pluginId) ?: view
                current.plugin?.let { plugin ->
                    runCatching { plugin.onSettingsChanged(current.host, settingsFor(current)) }
                }
                refresh()
                lifecycle.syncSkills(pluginId)
                onContributionsChanged()
            }
        }
    }

    fun uninstall(pluginId: String) {
        scope.launch {
            launchBusy {
                val view = gate.view(pluginId) ?: return@launchBusy
                if (!PluginRules.canUninstall(view.manifest)) return@launchBusy
                val skillIds = (view.skillIds + view.contributions.skills.map { it.id }).distinct()
                lifecycle.uninstallPlugin(view)?.let { error -> gate.markError(pluginId, error) }
                lifecycle.removeSkills(pluginId, view.source, skillIds)
                if (view.builtin) {
                    builtinStateStore.remove(pluginId)
                    gate.updateRecord(pluginId) {
                        it.copy(enabled = false, installedVersion = null, pendingVersion = null, previousVersion = null)
                    }
                } else {
                    external?.uninstall(pluginId)
                    gate.release(pluginId)
                    lifecycle.release(pluginId)
                    registry.unregister(pluginId)
                }
                refresh()
                onContributionsChanged()
            }
        }
    }

    suspend fun retryActivation(pluginId: String): PluginSetupResult = mutex.withLock {
        val runtime = external
            ?: return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_unknown))
        val record = runtime.record(pluginId)
            ?: return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_unknown))
        runtime.completeActivation(pluginId)
        runtime.clearStartupCount(pluginId)
        gate.clearFailure(pluginId)
        if (!record.enabled) {
            runtime.setEnabled(pluginId, true)
            gate.setEnabled(pluginId, true)
        }
        externalFlow.activatePendingVersions()
        if (gate.view(pluginId)?.plugin == null) externalFlow.loadForEnable(pluginId)
        val failure = gate.view(pluginId)?.let { view -> lifecycle.activate(view) }
        externalFlow.recordFailure(pluginId, failure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
        val view = gate.view(pluginId)
        val status = view?.status
        PluginSetupResult(
            ok = view?.plugin != null && failure == null,
            pluginId = pluginId,
            installed = view?.installed ?: false,
            ready = status is PluginStatusUi.Ready,
            status = status?.label,
            account = (status as? PluginStatusUi.Ready)?.account,
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
        val rollbackFailure = gate.view(pluginId)?.let { view -> lifecycle.activate(view) }
        externalFlow.recordFailure(pluginId, rollbackFailure)
        refresh()
        lifecycle.syncSkills(pluginId)
        onContributionsChanged()
        gate.view(pluginId)?.plugin != null
    }

    suspend fun prepareConnect(pluginId: String) {
        val view = gate.view(pluginId) ?: return
        if (view.enabled) return
        enablePlugin(pluginId)
    }

    fun setOperation(pluginId: String, label: String, stage: String) {
        state.update { it.copy(operation = PluginOperation(pluginId, label, stage)) }
    }

    fun setError(message: String) {
        state.update { it.copy(error = message) }
    }

    fun settingsFor(view: PluginRuntimeView): Map<String, String> {
        val specs = view.contributions.settings
        if (specs.isEmpty()) return emptyMap()
        return specs.normalize(settingsStore.cached(view.id))
    }

    private suspend fun persistEnabled(view: PluginRuntimeView, enabled: Boolean) {
        if (view.builtin) {
            builtinStateStore.upsert(
                view.id,
                BuiltinPluginState(
                    installed = view.installed,
                    enabled = enabled,
                    installedVersion = view.record.installedVersion,
                ),
            )
        } else {
            externalFlow.persistEnabled(view.id, enabled)
        }
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
            state.update { it.copy(busy = false, operation = null, interaction = null) }
            runCatching { refresh() }
            onContributionsChanged()
        }
    }
}
