package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginServices
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry
import io.github.whywood.deepdeep.plugin.host.update.ExternalUpdateSupport
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PluginHostManager internal constructor(
    private val context: Context,
    private val strings: PluginStrings,
    private val filesDir: File,
    private val scope: CoroutineScope,
    private val language: () -> String,
    private val servicesFactory: (pluginId: String, dataDir: File, manifest: PluginManifest, scope: CoroutineScope) -> PluginServices,
    private val skillStore: PluginSkillStore,
    private val external: ExternalPluginRuntime?,
    private val externalUpdates: ExternalUpdateSupport? = null,
    private val onContributionsChanged: () -> Unit,
    private val busySessions: () -> Set<String> = { emptySet() },
) : PluginManagerDelegation() {
    private val _state = MutableStateFlow(PluginsUiState())
    override val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    private val registry = PluginRegistry()
    private val gate = PluginRuntimeRegistry()
    private val pluginsRoot = File(filesDir, "plugins")
    private val mutex = Mutex()
    private val busy = AtomicBoolean(false)
    private var started = false
    private var lastRefreshAt = 0L

    override val textResolver: PluginTextResolver = PluginTextResolver(
        resourceString = { id -> strings.get(id) },
        language = language,
    )
    private val hostFactory = PluginHostFactory(
        context = context,
        pluginsRoot = pluginsRoot,
        textResolver = textResolver,
        servicesFactory = servicesFactory,
        onRefreshRequest = {
            scope.launch {
                refreshNow()
                onContributionsChanged()
            }
        },
    )
    private val lifecycle = PluginLifecycle(
        parentScope = scope,
        hostFactory = hostFactory::create,
        gate = gate,
        skillStore = skillStore,
        external = external,
    )
    private val materializer = PluginContributionMaterializer(gate)
    private val uiProjection = PluginUiProjection(textResolver)
    override val sessionHooks: PluginSessionHooks = PluginSessionHooks(scope, gate, registry)
    override val externalFlow: PluginExternalFlow = PluginExternalFlow(
        scope = scope,
        state = _state,
        gate = gate,
        registry = registry,
        external = external,
        externalUpdates = externalUpdates,
        strings = strings,
        textResolver = textResolver,
        lifecycle = lifecycle,
        refresh = { refreshNow() },
        onContributionsChanged = onContributionsChanged,
        busySessions = busySessions,
    )
    private val commands: PluginManagerCommands = PluginManagerCommands(
        scope = scope,
        state = _state,
        strings = strings,
        gate = gate,
        registry = registry,
        lifecycle = lifecycle,
        external = external,
        externalFlow = externalFlow,
        busy = busy,
        mutex = mutex,
        refresh = { refreshLocked() },
        clearStartupCount = { pluginId -> external?.clearStartupCount(pluginId) ?: false },
        onContributionsChanged = onContributionsChanged,
    )

    override val appScope: CoroutineScope get() = scope

    override val slotRegistry: SlotRegistry by lazy { SlotRegistry({ registrySnapshot }, pluginsRoot) }

    override val registrySnapshot: PluginRegistrySnapshot get() = registry.snapshot()

    override suspend fun start() = mutex.withLock {
        if (started) {
            externalFlow.activatePendingVersions()
            refreshLocked()
            gate.ids().forEach { pluginId -> lifecycle.syncSkills(pluginId) }
            return
        }
        _state.update { it.copy(loading = true) }
        try {
            externalFlow.registerRecords()
            gate.publishTo(registry)
            val startupBlocked = guardStartup()
            externalFlow.activatePendingVersions(startupBlocked)
            externalFlow.activateEnabledRecords(startupBlocked)
            refreshLocked()
            gate.views().filter { view -> view.enabled }.forEach { view ->
                val failure = lifecycle.enable(view)
                if (failure == null) external?.clearStartupCount(view.id)
                externalFlow.recordFailure(view.id, failure)
            }
            gate.ids().forEach { pluginId -> lifecycle.syncSkills(pluginId) }
            externalFlow.scheduleUpdateCheck()
            started = true
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }

    override fun refresh() {
        scope.launch { refreshNow() }
    }

    override fun refreshAndSyncSkills() {
        scope.launch {
            refreshNow()
            mutex.withLock { gate.ids().forEach { pluginId -> lifecycle.syncSkills(pluginId) } }
        }
    }

    override fun refreshIfStale(minIntervalMillis: Long) {
        if (System.currentTimeMillis() - lastRefreshAt < minIntervalMillis) return
        refresh()
    }

    override suspend fun refreshNow() = mutex.withLock { refreshLocked() }

    override fun promptBody(): String? {
        val sections = registrySnapshot.promptSections.map { it.section }.sortedBy { it.priority }
        if (sections.isEmpty()) return null
        return sections.joinToString("\n\n") { it.body }
    }

    override fun setEnabled(pluginId: String, enabled: Boolean) = commands.setEnabled(pluginId, enabled)

    override fun uninstall(pluginId: String) = commands.uninstall(pluginId)

    override suspend fun retryActivation(pluginId: String) = commands.retryActivation(pluginId)

    override suspend fun rollbackPlugin(pluginId: String) = commands.rollbackPlugin(pluginId)

    private suspend fun refreshLocked() {
        lastRefreshAt = System.currentTimeMillis()
        materializer.refreshAll()
        gate.publishTo(registry)
        sessionHooks.refreshSnapshots()
        val snapshot = registry.snapshot()
        _state.update { it.copy(plugins = uiProjection.snapshots(gate.views(), snapshot), loading = false) }
    }

    private suspend fun guardStartup(): Set<String> {
        val runtime = external ?: return emptySet()
        val blocked = linkedSetOf<String>()
        runtime.records().filter { it.enabled }.forEach { record ->
            val interrupted = runtime.interruptedActivation(record.id)
            if (interrupted != null) {
                blocked += record.id
                when (PluginRecoveryPolicy.afterInterruptedActivation(interrupted.attempts)) {
                    is RecoveryDecision.AutoDisable -> autoDisable(record.id, R.string.plugins_recovery_crash_disabled)
                    else -> gate.markFailure(
                        record.id,
                        strings.get(R.string.plugins_recovery_activation_interrupted),
                    )
                }
                return@forEach
            }
            if (record.pendingVersion != null) return@forEach
            val attempts = runtime.noteStartup(record.id, record.version)
            when (PluginRecoveryPolicy.afterStartup(attempts)) {
                is RecoveryDecision.AutoDisable -> {
                    blocked += record.id
                    autoDisable(record.id, R.string.plugins_recovery_crash_disabled)
                }

                else -> Unit
            }
        }
        return blocked
    }

    private suspend fun autoDisable(pluginId: String, message: Int) {
        external?.setEnabled(pluginId, false)
        gate.setEnabled(pluginId, false)
        gate.markFailure(pluginId, strings.get(message))
    }
}
