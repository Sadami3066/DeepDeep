package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard
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
    private val builtins: List<BuiltinPluginEntry>,
    private val servicesFactory: (pluginId: String, dataDir: File, scope: CoroutineScope) -> PluginServices,
    private val builtinStateStore: BuiltinPluginStateStore,
    private val settingsStore: PluginSettingsStore,
    private val skillStore: PluginSkillStore,
    private val external: ExternalPluginRuntime?,
    private val externalUpdates: ExternalUpdateSupport? = null,
    private val isForeground: () -> Boolean,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit,
    private val notifyInstallFinished: (label: String, success: Boolean, error: String?) -> Unit,
    private val onContributionsChanged: () -> Unit,
    private val onSetupReady: (label: String) -> Unit,
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
    private val hostFactory = PluginHostFactory(context, pluginsRoot, textResolver, servicesFactory)
    private val lifecycle = PluginLifecycle(
        parentScope = scope,
        hostFactory = hostFactory::create,
        gate = gate,
        skillStore = skillStore,
        external = external,
        settingsFor = { view -> commands.settingsFor(view) },
    )
    private val materializer = PluginContributionMaterializer(gate)
    private val uiProjection = PluginUiProjection(textResolver, settingsStore)
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
        settingsStore = settingsStore,
        refresh = { refreshNow() },
        onContributionsChanged = onContributionsChanged,
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
        settingsStore = settingsStore,
        builtinStateStore = builtinStateStore,
        busy = busy,
        mutex = mutex,
        refresh = { refreshLocked() },
        clearStartupCount = { pluginId -> external?.clearStartupCount(pluginId) ?: false },
        onContributionsChanged = onContributionsChanged,
    )
    override val setupFlow: PluginSetupFlow = PluginSetupFlow(
        scope = scope,
        state = _state,
        strings = strings,
        textResolver = textResolver,
        busy = busy,
        gate = gate,
        isForeground = isForeground,
        builtinStateStore = builtinStateStore,
        notifyInstall = notifyInstall,
        notifyInstallFinished = notifyInstallFinished,
        refresh = { refreshNow() },
        syncSkills = { pluginId -> lifecycle.syncSkills(pluginId) },
        activate = { view -> lifecycle.activate(view) },
        onContributionsChanged = onContributionsChanged,
        onSetupReady = onSetupReady,
        onError = commands::setError,
        onOperation = commands::setOperation,
    )

    override val setupPluginIds: List<String>
        get() = gate.views()
            .filter { it.manifest.source == PluginSource.EXTERNAL }
            .map { it.id }
            .sorted()

    override val appScope: CoroutineScope get() = scope

    override val slotRegistry: SlotRegistry by lazy { SlotRegistry({ registrySnapshot }, pluginsRoot) }

    override val guard: PluginFailureGuard by lazy { PluginFailureGuard(File(pluginsRoot, "slots")) }

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
            val states = builtinStateStore.load()
            builtins.forEach { entry -> lifecycle.registerBuiltin(entry, states[entry.manifest.id]) }
            externalFlow.registerRecords()
            gate.publishTo(registry)
            val startupBlocked = guardStartup()
            externalFlow.activatePendingVersions(startupBlocked)
            externalFlow.activateEnabledRecords(startupBlocked)
            settingsStore.warm(gate.ids())
            refreshLocked()
            activatableViews().forEach { view -> lifecycle.applySettings(view) }
            refreshLocked()
            activatableViews().forEach { view ->
                val failure = lifecycle.enable(view)
                if (view.source == PluginSource.EXTERNAL) {
                    if (failure == null) external?.clearStartupCount(view.id)
                    externalFlow.recordFailure(view.id, failure)
                }
                lifecycle.syncSkills(view.id)
            }
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

    override fun setSetting(pluginId: String, key: String, value: String) =
        commands.setSetting(pluginId, key, value)

    override fun uninstall(pluginId: String) = commands.uninstall(pluginId)

    override suspend fun retryActivation(pluginId: String) = commands.retryActivation(pluginId)

    override suspend fun rollbackPlugin(pluginId: String) = commands.rollbackPlugin(pluginId)

    override suspend fun prepareConnect(pluginId: String) = commands.prepareConnect(pluginId)

    private suspend fun refreshLocked() {
        lastRefreshAt = System.currentTimeMillis()
        materializer.refreshAll()
        gate.publishTo(registry)
        sessionHooks.refreshSnapshots()
        val snapshot = registry.snapshot()
        _state.update { it.copy(plugins = uiProjection.snapshots(gate.views(), snapshot), loading = false) }
    }

    private fun activatableViews(): List<PluginRuntimeView> = gate.views().filter { view ->
        view.enabled && (view.record.installedVersion != null || !view.manifest.installable)
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

internal fun List<SettingSpec>.settingLeaves(): List<SettingSpec> = flatMap { spec ->
    if (spec is SettingSpec.Group) spec.items.settingLeaves() else listOf(spec)
}
