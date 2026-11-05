package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.api.normalize
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginHostImpl
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginServices
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksAdapter
import io.github.whywood.deepdeep.plugin.host.events.PluginRuntimeHooksFactory
import io.github.whywood.deepdeep.plugin.host.events.RuntimeEventMapper
import io.github.whywood.deepdeep.plugin.host.events.registryHookSource
import io.github.whywood.deepdeep.plugin.host.PluginToolAdapter
import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
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
    private val servicesFactory: (pluginId: String, dataDir: File) -> PluginServices,
    private val builtinStateStore: BuiltinPluginStateStore,
    private val settingsStore: PluginSettingsStore,
    private val skillStore: PluginSkillStore,
    private val external: ExternalPluginRuntime?,
    private val externalUpdates: io.github.whywood.deepdeep.plugin.host.update.ExternalUpdateSupport? = null,
    private val isForeground: () -> Boolean,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit,
    private val notifyInstallFinished: (label: String, success: Boolean, error: String?) -> Unit,
    private val onContributionsChanged: () -> Unit,
    private val onSetupReady: (label: String) -> Unit,
) : PluginManagerFacade {
    private val _state = MutableStateFlow(PluginsUiState())
    override val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    private val registry = PluginRegistry()
    private val pluginsRootDir = File(filesDir, "plugins")
    private val slotRegistry by lazy { io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry({ registrySnapshot }, pluginsRootDir) }
    private val guard by lazy { io.github.whywood.deepdeep.plugin.host.PluginFailureGuard(File(pluginsRootDir, "slots")) }
    private val hooksFactory = PluginRuntimeHooksFactory(registryHookSource(registry))
    private val sessionHooks = linkedMapOf<String, PluginRuntimeHooksAdapter>()
    private val plugins = linkedMapOf<String, ManagedPlugin>()
    private val mutex = Mutex()
    private val busy = AtomicBoolean(false)
    private val textResolver = PluginTextResolver(
        resourceString = { id -> strings.get(id) },
        language = language,
    )
    private val setupFlow = PluginSetupFlow(
        scope = scope,
        state = _state,
        strings = strings,
        textResolver = textResolver,
        busy = busy,
        managedPlugin = { pluginId -> plugins[pluginId] },
        isForeground = isForeground,
        builtinStateStore = builtinStateStore,
        notifyInstall = notifyInstall,
        notifyInstallFinished = notifyInstallFinished,
        refresh = { refreshNow() },
        syncSkills = { managed -> syncSkillsLocked(managed) },
        onContributionsChanged = onContributionsChanged,
        onSetupReady = onSetupReady,
        onError = { message -> setError(message) },
        onOperation = { pluginId, label, stage -> setOperation(pluginId, label, stage) },
    )
    private val externalFlow = PluginExternalFlow(
        scope = scope,
        state = _state,
        plugins = plugins,
        registry = registry,
        external = external,
        externalUpdates = externalUpdates,
        strings = strings,
        textResolver = textResolver,
        hostFor = { managed -> hostFor(managed) },
        refresh = { refreshNow() },
        syncSkills = { managed -> syncSkillsLocked(managed) },
        onContributionsChanged = onContributionsChanged,
    )
    private var lastRefreshAt = 0L

    internal val registrySnapshot: PluginRegistrySnapshot get() = registry.snapshot()

    override val setupPluginIds: List<String>
        get() = plugins.values
            .filter { it.manifest.source == PluginSource.EXTERNAL && it.builtin }
            .map { it.manifest.id }
            .sorted()

    override suspend fun start() = mutex.withLock {
        _state.update { it.copy(loading = true) }
        try {
            val states = builtinStateStore.load()
            builtins.forEach { entry ->
                if (plugins.containsKey(entry.manifest.id)) return@forEach
                val persisted = states[entry.manifest.id]
                val enabled = persisted?.enabled ?: entry.manifest.defaultEnabled
                val version = PluginRules.installedVersion(
                    entry.manifest,
                    persisted?.installedVersion,
                    persisted?.installed,
                )
                val managed = ManagedPlugin(
                    manifest = entry.manifest,
                    plugin = entry.plugin,
                    builtin = true,
                    source = entry.manifest.source,
                    installedVersion = version,
                    enabled = enabled,
                    phase = if (version != null && enabled) PluginPhase.INSTALLED else PluginPhase.DISABLED,
                )
                managed.host = hostFor(managed)
                plugins[managed.manifest.id] = managed
                registry.register(managed.toDescriptor())
            }
            externalFlow.registerRecords()
            externalFlow.activateEnabled()
            settingsStore.warm(plugins.keys)
            plugins.values.forEach { managed ->
                if (managed.enabled && (managed.installedVersion != null || !managed.installable())) {
                    runCatching { managed.plugin?.onSettingsChanged(managed.host, settingsFor(managed)) }
                }
            }
            refreshLocked()
            plugins.values.forEach { managed ->
                if (managed.enabled && (managed.installedVersion != null || !managed.installable())) {
                    runCatching { managed.plugin?.onEnable(managed.host) }
                    syncSkillsLocked(managed)
                }
            }
            externalFlow.scheduleUpdateCheck()
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
            mutex.withLock {
                plugins.values.forEach { managed -> syncSkillsLocked(managed) }
            }
        }
    }

    override fun refreshIfStale(minIntervalMillis: Long) {
        if (System.currentTimeMillis() - lastRefreshAt < minIntervalMillis) return
        refresh()
    }

    override suspend fun refreshNow() = mutex.withLock { refreshLocked() }

    override fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool> {
        val snapshot = registrySnapshot
        val session = PluginSessionInfo(binding.supportsChatDelivery) {
            binding.current()?.let { bound ->
                io.github.whywood.deepdeep.plugin.api.PluginSessionSnapshot(
                    sessionId = bound.sessionId,
                    file = bound.sessionFile,
                    cwd = bound.cwd,
                )
            }
        }
        val sessionId = { binding.current()?.sessionId.orEmpty() }
        val activeIds = snapshot.plugins
            .filter { descriptor -> plugins[descriptor.id]?.isActive() == true }
            .map { it.id }
        return activeIds.flatMap { pluginId ->
            val managed = plugins[pluginId] ?: return@flatMap emptyList()
            val plugin = managed.plugin ?: return@flatMap emptyList()
            val contributions = runCatching { plugin.contributionsFor(managed.host, session) }
                .getOrElse { error ->
                    managed.error = error.message
                    Contributions()
                }
            PluginToolAdapter.toCodingTools(contributions.tools, sessionId)
        }.distinctBy { it.name }
    }

    override fun promptBody(): String? {
        val bodies = plugins.values
            .filter { it.isActive() && it.installedVersion != null }
            .mapNotNull { managed ->
                managed.plugin?.let { plugin ->
                    runCatching { plugin.promptSections(managed.host) }.getOrNull()
                        ?.sortedBy { it.priority }
                        ?.joinToString("\n\n") { it.body }
                        ?.takeIf { it.isNotBlank() }
                }
            }
        return bodies.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    override fun setEnabled(pluginId: String, enabled: Boolean) {
        scope.launch {
            mutex.withLock {
                val managed = plugins[pluginId] ?: return@withLock
                if (managed.builtin) {
                    builtinStateStore.upsert(
                        pluginId,
                        BuiltinPluginState(
                            installed = managed.installedVersion != null,
                            enabled = enabled,
                            installedVersion = managed.installedVersion,
                        ),
                    )
                } else {
                    externalFlow.setExternalEnabled(pluginId, enabled)
                }
                managed.enabled = enabled
                guard.clearFor(pluginId)
                if (enabled) {
                    runCatching { managed.plugin?.onEnable(managed.host) }
                } else {
                    runCatching { managed.plugin?.onDisable(managed.host) }
                }
                refreshLocked()
                syncSkillsLocked(managed)
                onContributionsChanged()
            }
        }
    }

    override fun setSetting(pluginId: String, key: String, value: String) {
        scope.launch {
            mutex.withLock {
                val managed = plugins[pluginId] ?: return@withLock
                val spec = managed.manifestSettings().firstOrNull { it.key == key } ?: return@withLock
                val normalized = spec.normalize(value) ?: return@withLock
                settingsStore.set(pluginId, key, normalized)
                managed.plugin?.let { plugin ->
                    runCatching { plugin.onSettingsChanged(managed.host, settingsFor(managed)) }
                }
                refreshLocked()
                syncSkillsLocked(managed)
                onContributionsChanged()
            }
        }
    }

    override fun install(pluginId: String) = setupFlow.install(pluginId)

    override fun update(pluginId: String) = setupFlow.install(pluginId)

    override fun uninstall(pluginId: String) {
        scope.launch {
            launchBusy {
                val managed = plugins[pluginId] ?: return@launchBusy
                if (!PluginRules.canUninstall(managed.manifest)) return@launchBusy
                plugins.remove(pluginId)
                if (managed.builtin) {
                    runCatching { managed.plugin?.uninstall(managed.host) }
                    builtinStateStore.remove(pluginId)
                } else {
                    external?.uninstall(pluginId)
                }
                val skillIds = managed.manifestSkills().map { it.id }
                runCatching { skillStore.remove(pluginId, skillIds) }
                registry.unregister(pluginId)
                refreshLocked()
                onContributionsChanged()
            }
        }
    }

    override fun connect(pluginId: String) {
        scope.launch { runCatching { setupFlow.setup(pluginId) } }
    }

    override fun runAction(pluginId: String, actionId: String) = setupFlow.runAction(pluginId, actionId)

    override fun onAppForeground() = setupFlow.onAppForeground()

    override fun confirmInstall() = setupFlow.confirmInstall()

    override fun cancelInteraction() = setupFlow.cancelInteraction()

    override fun cancelSetup(pluginId: String) = setupFlow.cancelSetup(pluginId)

    override suspend fun setup(pluginId: String): PluginSetupResult = setupFlow.setup(pluginId)

    override suspend fun inspectExternal(file: File): ExternalInspectionUi? =
        externalFlow.inspectExternal(file)

    override suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        externalFlow.installExternal(file, acceptPublisher)

    override suspend fun checkExternalUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override suspend fun pluginSourceSettings(): PluginSourceSettings = externalFlow.pluginSourceSettings()

    override suspend fun savePluginSourceSettings(settings: PluginSourceSettings) {
        externalFlow.savePluginSourceSettings(settings)
    }

    override suspend fun refreshAvailableUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override suspend fun stageExternalUpdate(pluginId: String): String? =
        externalFlow.stageExternalUpdate(pluginId)

    internal fun bindSessionHooks(session: BoundSession, delegate: PluginHooksDelegate) {
        val adapter = hooksFactory.create(session.sessionId, session.cwd, session.sessionFile)
        synchronized(sessionHooks) { sessionHooks[session.sessionId] = adapter }
        delegate.bind(adapter)
        scope.launch {
            runCatching {
                adapter.emit(PluginEvent.SessionStarted(session.sessionId, null, session.cwd))
            }
        }
    }

    internal fun onSessionClosed(sessionId: String) {
        val adapter = synchronized(sessionHooks) { sessionHooks.remove(sessionId) } ?: return
        scope.launch { runCatching { adapter.emit(PluginEvent.SessionClosed(sessionId)) } }
    }

    internal fun onRuntimeEvent(event: SessionRuntimeEvent) {
        val adapter = synchronized(sessionHooks) { sessionHooks[event.sessionId] } ?: return
        scope.launch {
            runCatching {
                RuntimeEventMapper(adapter, includeRuntimeFallbacks = false).dispatch(event.sessionId, event)
            }
        }
    }

    internal fun onSessionSdkEvent(sessionId: String, event: dev.pi.coding.session.SessionEvent) {
        val adapter = synchronized(sessionHooks) { sessionHooks[sessionId] } ?: return
        scope.launch { runCatching { RuntimeEventMapper(adapter).dispatchSessionEvent(sessionId, event) } }
    }

    override fun slotEntries(slotId: String): List<io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry> =
        slotRegistry.bindings(slotId)

    override fun pagesOf(pluginId: String): List<io.github.whywood.deepdeep.plugin.api.PluginPage> =
        registrySnapshot.pages.filter { it.pluginId == pluginId }.map { it.page }

    override fun composerActions(): List<PluginComposerActionUi> =
        registrySnapshot.composerActions.map { PluginComposerActionUi(it.pluginId, it.action) }

    override fun slotFailureGuard(): io.github.whywood.deepdeep.plugin.host.PluginFailureGuard = guard

    override fun resolveText(text: LocalizedText): String = textResolver.resolve(text)

    private suspend fun refreshLocked() {
        lastRefreshAt = System.currentTimeMillis()
        plugins.values.forEach { managed ->
            refreshManaged(managed)
        }
        _state.update { current -> current.copy(plugins = snapshotList(), loading = false) }
    }

    private suspend fun refreshManaged(managed: ManagedPlugin) {
        val plugin = managed.plugin
        if (plugin == null) {
            managed.phase = PluginPhase.FAILED
            registry.updatePhase(managed.manifest.id, PluginPhase.FAILED, managed.error)
            return
        }
        val installed = managed.installedVersion != null
        val availability = runCatching { plugin.availability(managed.host) }
            .getOrElse { error -> PluginAvailability.Unavailable(error.message ?: error.javaClass.simpleName) }
        managed.availability = when (availability) {
            is PluginAvailability.Available -> PluginAvailabilityUi.Available
            is PluginAvailability.Unavailable -> PluginAvailabilityUi.Unavailable(availability.reason)
        }
        if (!managed.enabled) {
            managed.phase = PluginPhase.DISABLED
            managed.status = null
            registry.updatePhase(managed.manifest.id, PluginPhase.DISABLED)
            registry.updateContributions(managed.manifest.id, PluginContributions.Empty)
            return
        }
        if (!installed && managed.manifest.installable) {
            managed.phase = PluginPhase.INSTALLED
            managed.status = null
            registry.updatePhase(managed.manifest.id, PluginPhase.INSTALLED)
            registry.updateContributions(managed.manifest.id, PluginContributions.Empty)
            return
        }
        managed.status = if (managed.availability is PluginAvailabilityUi.Available) {
            runCatching { plugin.status(managed.host) }
                .getOrElse { error -> PluginStatus.Failed(error.message ?: error.javaClass.simpleName) }
                .toUi()
        } else {
            null
        }
        val settings = settingsFor(managed)
        val contributions = runCatching {
            val statusApi = managed.status?.toApi() ?: PluginStatus.NotReady("")
            val base = plugin.contributions(managed.host)
            PluginContributions(
                tools = base.tools,
                skills = plugin.skills(managed.host),
                promptSections = plugin.promptSections(managed.host),
                settings = managed.manifestSettings(),
                actions = if (managed.status != null) plugin.actions(managed.host, statusApi) else emptyList(),
                detailRows = plugin.detailRows(managed.host, statusApi),
                ui = plugin.ui(managed.host),
                hooks = base.hooks,
            )
        }.getOrElse { error ->
            managed.error = error.message
            PluginContributions.Empty
        }
        managed.contributions = contributions
        managed.phase = if (managed.externalRecord?.pendingVersion != null) {
            PluginPhase.PENDING_RESTART
        } else {
            PluginRules.phase(
                enabled = managed.enabled,
                installedVersion = managed.installedVersion,
                installable = managed.manifest.installable,
                availability = managed.availability,
                status = managed.status,
            )
        }
        registry.updatePhase(managed.manifest.id, managed.phase, managed.error)
        registry.updateContributions(managed.manifest.id, contributions)
    }

    private suspend fun syncSkillsLocked(managed: ManagedPlugin) {
        val plugin = managed.plugin ?: return
        val skills = runCatching { plugin.skills(managed.host) }.getOrDefault(emptyList())
            .map { skill ->
                skill.copy(
                    files = skill.files.map { file ->
                        val assetPath = file.assetPath
                        if (file.content == null && assetPath != null) {
                            file.copy(content = readAsset(managed, assetPath))
                        } else {
                            file
                        }
                    },
                )
            }
        runCatching {
            skillStore.sync(
                managed.manifest.id,
                skills,
                managed.skillIds,
                managed.enabled && managed.availability is PluginAvailabilityUi.Available,
            )
        }
        managed.skillIds = skills.map { it.id }
        if (managed.source == PluginSource.EXTERNAL) {
            external?.updateSkills(managed.manifest.id, managed.skillIds)
        }
    }

    private fun readAsset(managed: ManagedPlugin, path: String): String? = runCatching {
        File(managed.host.assetsDir, path).readText()
    }.getOrNull()

    private fun snapshotList(): List<PluginSnapshot> = registrySnapshot.plugins.mapNotNull { descriptor ->
        val managed = plugins[descriptor.id] ?: return@mapNotNull null
        val host = managed.host
        PluginSnapshot(
            id = descriptor.id,
            name = textResolver.resolve(descriptor.manifest.name),
            summary = textResolver.resolve(descriptor.manifest.summary),
            description = descriptor.manifest.description?.let(textResolver::resolve),
            version = descriptor.manifest.version,
            installedVersion = managed.installedVersion,
            sizeLabel = descriptor.manifest.sizeLabel?.let(textResolver::resolve),
            icon = descriptor.manifest.icon,
            source = descriptor.manifest.source,
            installable = descriptor.manifest.installable,
            requiresPrivilege = descriptor.manifest.requiresPrivilege,
            enabled = managed.enabled,
            phase = descriptor.phase.toUi(),
            availability = managed.availability,
            status = managed.status,
            updateAvailable = managed.updateAvailable(),
            settingsValues = settingsFor(managed),
            settings = managed.contributions.settings.ifEmpty { managed.manifestSettings() },
            actions = managed.contributions.actions,
            detailRows = managed.contributions.detailRows,
            skills = managed.contributions.skills.map { it.id },
            installedSizeBytes = managed.externalRecord?.size,
            lastError = managed.error,
            publisher = managed.externalRecord?.let { record ->
                io.github.whywood.deepdeep.plugin.api.PublisherInfo("", record.publisherKeyId)
            },
        )
    }

    private fun hostFor(managed: ManagedPlugin): PluginHost = PluginHostImpl(
        manifest = managed.manifest,
        context = context,
        scope = scope,
        services = servicesFactory(managed.manifest.id, File(filesDir, "plugins/${managed.manifest.id}/data")),
        textResolver = textResolver,
        pluginsRoot = File(filesDir, "plugins"),
    )

    private fun settingsFor(managed: ManagedPlugin): Map<String, String> {
        val specs = managed.manifestSettings()
        if (specs.isEmpty()) return emptyMap()
        return specs.normalize(settingsStore.cached(managed.manifest.id))
    }

    private fun ManagedPlugin.manifestSettings(): List<SettingSpec> =
        plugin?.settings().orEmpty()

    private fun ManagedPlugin.manifestSkills() = contributions.skills

    private fun ManagedPlugin.installable(): Boolean = manifest.installable

    private fun ManagedPlugin.isActive(): Boolean =
        PluginRules.isActive(enabled, installedVersion, availability, phase)

    private fun ManagedPlugin.updateAvailable(): Boolean =
        PluginRules.updateAvailable(manifest.version, installedVersion, externalRecord?.pendingVersion)

    private fun PluginPhase.toUi(): PluginPhaseUi = when (this) {
        PluginPhase.IMPORTED -> PluginPhaseUi.IMPORTED
        PluginPhase.INSTALLED -> PluginPhaseUi.INSTALLED
        PluginPhase.ACTIVE -> PluginPhaseUi.ACTIVE
        PluginPhase.DEGRADED -> PluginPhaseUi.DEGRADED
        PluginPhase.FAILED -> PluginPhaseUi.FAILED
        PluginPhase.DISABLED -> PluginPhaseUi.DISABLED
        PluginPhase.PENDING_RESTART -> PluginPhaseUi.PENDING_RESTART
        PluginPhase.UNLOADED -> PluginPhaseUi.UNLOADED
    }

    private fun PluginStatusUi.toApi(): PluginStatus = when (this) {
        is PluginStatusUi.Ready -> PluginStatus.Ready(label, account)
        is PluginStatusUi.NotReady -> PluginStatus.NotReady(label)
        is PluginStatusUi.Failed -> PluginStatus.Failed(label)
    }

    private suspend fun launchBusy(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            setError(strings.get(R.string.plugins_error_busy))
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setError(error.message ?: error.javaClass.simpleName)
        } finally {
            busy.set(false)
            _state.update { it.copy(busy = false, operation = null, interaction = null) }
            runCatching { refreshNow() }
            onContributionsChanged()
        }
    }

    private fun setOperation(pluginId: String, label: String, stage: String, progress: Int? = null) {
        _state.update { it.copy(operation = PluginOperation(pluginId, label, stage, progress)) }
    }

    private fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }


}

data class ExternalInspectionUi(
    val file: File,
    val pluginId: String,
    val name: String,
    val version: String,
    val size: Long,
    val sha256: String,
    val publisherName: String,
    val publisherKeyId: String,
    val trusted: Boolean,
    val requiresPrivilege: Boolean,
    val provides: io.github.whywood.deepdeep.plugin.api.Provides,
    val error: String?,
)

data class ExternalInstallUi(
    val success: Boolean,
    val error: String?,
    val requiresConfirmation: Boolean = false,
    val restartRequired: Boolean = false,
)

data class PluginUpdateUi(
    val pluginId: String,
    val name: String,
    val installedVersion: String,
    val availableVersion: String,
    val bundleSha256: String,
    val size: Long,
    val publisherKeyId: String,
)
