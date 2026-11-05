package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginSettingsStore
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.api.normalize
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
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
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallRejection
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val confirmWaiter = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val setupCancelled = AtomicBoolean(false)
    private val setupFinished = AtomicBoolean(false)
    private val setupJob = AtomicReference<Job?>(null)
    private val setupPluginId = AtomicReference<String?>(null)
    private val textResolver = PluginTextResolver(
        resourceString = { id -> strings.get(id) },
        language = language,
    )
    private var pendingImport: File? = null
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
                val installable = entry.manifest.installable
                val installed = if (installable) persisted?.installed ?: false else true
                val enabled = persisted?.enabled ?: entry.manifest.defaultEnabled
                val version = if (installed) persisted?.installedVersion ?: entry.manifest.version else null
                val managed = ManagedPlugin(
                    manifest = entry.manifest,
                    plugin = entry.plugin,
                    builtin = true,
                    source = entry.manifest.source,
                    installedVersion = version,
                    enabled = enabled,
                    phase = if (installed && enabled) PluginPhase.INSTALLED else PluginPhase.DISABLED,
                )
                managed.host = hostFor(managed)
                plugins[managed.manifest.id] = managed
                registry.register(managed.toDescriptor())
            }
            external?.records()?.forEach { record ->
                val managed = ManagedPlugin(
                    manifest = manifestForRecord(record),
                    plugin = null,
                    builtin = false,
                    source = PluginSource.EXTERNAL,
                    installedVersion = record.version,
                    enabled = record.enabled,
                    phase = if (record.enabled) PluginPhase.INSTALLED else PluginPhase.DISABLED,
                )
                managed.host = hostFor(managed)
                managed.externalRecord = record
                plugins[managed.manifest.id] = managed
                registry.register(managed.toDescriptor())
            }
            external?.activateOnStartup()?.forEach { record ->
                plugins[record.id]?.let { managed ->
                    managed.externalRecord = record
                    managed.installedVersion = record.version
                    managed.enabled = record.enabled
                }
                attachExternalHandle(record)
            }
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
            scheduleUpdateCheck()
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }

    private fun scheduleUpdateCheck() {
        val support = externalUpdates ?: return
        scope.launch {
            runCatching {
                val settings = support.loadSettings()
                if (!settings.autoCheck || settings.indexUrl.isNullOrBlank()) return@runCatching
                val updates = checkExternalUpdates()
                _state.update { it.copy(availableUpdates = updates) }
            }
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
                    external?.setEnabled(pluginId, enabled)
                }
                managed.enabled = enabled
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

    override fun install(pluginId: String) {
        scope.launch {
            launchBusy {
                val managed = plugins[pluginId] ?: return@launchBusy
                if (!managed.manifest.installable) return@launchBusy
                installInternal(managed)
                refreshLocked()
                syncSkillsLocked(managed)
                onContributionsChanged()
            }
        }
    }

    override fun update(pluginId: String) {
        install(pluginId)
    }

    override fun uninstall(pluginId: String) {
        scope.launch {
            launchBusy {
                val managed = plugins[pluginId] ?: return@launchBusy
                if (!managed.manifest.installable) return@launchBusy
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
        scope.launch { runCatching { setup(pluginId) } }
    }

    override fun runAction(pluginId: String, actionId: String) {
        scope.launch {
            setupCancelled.set(false)
            launchBusy {
                val managed = plugins[pluginId] ?: return@launchBusy
                if (!managed.enabled) {
                    setError(strings.get(R.string.plugins_error_disabled))
                    return@launchBusy
                }
                val plugin = managed.plugin ?: return@launchBusy
                val call = scope.async { plugin.runAction(managed.host, actionId, setupUi(managed)) }
                setupJob.set(call)
                try {
                    call.await()
                } catch (error: CancellationException) {
                    call.cancel()
                    if (!setupCancelled.get()) throw error
                } finally {
                    setupJob.compareAndSet(call, null)
                }
                refreshLocked()
            }
        }
    }

    override fun onAppForeground() {
        if (!busy.get()) return
        val interaction = _state.value.interaction ?: return
        val managed = plugins[interaction.pluginId] ?: return
        scope.launch {
            val status = runCatching { managed.plugin?.status(managed.host) }.getOrNull()
            if (status is PluginStatus.Ready) {
                completeConfirm(true)
                setupFinished.set(true)
                setupJob.getAndSet(null)?.cancel()
                _state.update { it.copy(interaction = null) }
                onSetupReady(interaction.title)
            }
        }
    }

    override fun confirmInstall() {
        completeConfirm(true)
    }

    override fun cancelInteraction() {
        setupCancelled.set(true)
        setupJob.getAndSet(null)?.cancel()
        completeConfirm(false)
        _state.update { it.copy(interaction = null) }
    }

    override fun cancelSetup(pluginId: String) {
        if (setupPluginId.get() != pluginId) return
        cancelInteraction()
    }

    override suspend fun setup(pluginId: String): PluginSetupResult {
        setupPluginId.set(pluginId)
        setupCancelled.set(false)
        setupFinished.set(false)
        return try {
            setupInternal(pluginId)
        } finally {
            setupPluginId.compareAndSet(pluginId, null)
        }
    }

    private suspend fun setupInternal(pluginId: String): PluginSetupResult {
        val managed = plugins[pluginId]
            ?: return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_unknown))
        val plugin = managed.plugin
            ?: return PluginSetupResult(false, pluginId, false, false, error = managed.error)
        if (!managed.enabled) {
            return PluginSetupResult(false, pluginId, managed.installedVersion != null, false, error = strings.get(R.string.plugins_error_disabled))
        }
        if (!isForeground()) {
            return PluginSetupResult(false, pluginId, managed.installedVersion != null, false, error = strings.get(R.string.plugins_error_foreground))
        }
        if (managed.manifest.installable && managed.installedVersion == null && managed.builtin) {
            if (!beginBusy()) {
                return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_busy))
            }
            try {
                if (!awaitInstall(managed)) {
                    return PluginSetupResult(false, pluginId, false, false, cancelled = setupCancelled.get())
                }
                val installCall = scope.async { installInternal(managed) }
                setupJob.set(installCall)
                try {
                    installCall.await()
                } catch (error: CancellationException) {
                    installCall.cancel()
                    if (!setupCancelled.get()) throw error
                    return PluginSetupResult(false, pluginId, false, false, cancelled = true)
                } finally {
                    setupJob.compareAndSet(installCall, null)
                }
            } finally {
                finishBusy()
            }
        }
        val current = runCatching { plugin.status(managed.host) }.getOrNull()
        if (current is PluginStatus.Ready) {
            refreshNow()
            return PluginSetupResult(true, pluginId, true, true, current.label, current.account)
        }
        if (!beginBusy()) {
            return PluginSetupResult(false, pluginId, true, false, error = strings.get(R.string.plugins_error_busy))
        }
        val call = scope.async { plugin.setup(managed.host, setupUi(managed)) }
        setupJob.set(call)
        var cancelled = false
        return try {
            try {
                call.await()
                if (setupCancelled.get()) cancelled = true
            } catch (error: CancellationException) {
                call.cancel()
                when {
                    setupCancelled.get() -> {
                        cancelled = true
                        if (!currentCoroutineContext().isActive) throw error
                    }

                    setupFinished.get() -> Unit
                    else -> throw error
                }
            }
            refreshNow()
            val status = runCatching { plugin.status(managed.host) }.getOrNull()
            when {
                cancelled -> PluginSetupResult(false, pluginId, true, false, cancelled = true)
                status is PluginStatus.Ready -> PluginSetupResult(true, pluginId, true, true, status.label, status.account)
                status is PluginStatus.NotReady -> PluginSetupResult(false, pluginId, true, false, status.label)
                status is PluginStatus.Failed -> PluginSetupResult(false, pluginId, true, false, status.label, error = status.label)
                else -> PluginSetupResult(false, pluginId, true, false, error = strings.get(R.string.plugins_error_status))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PluginSetupResult(false, pluginId, true, false, error = error.message)
        } finally {
            setupJob.compareAndSet(call, null)
            setupFinished.set(false)
            finishBusy()
        }
    }

    override suspend fun inspectExternal(file: File): ExternalInspectionUi? {
        val runtime = external ?: return null
        return when (val result = runtime.inspect(file)) {
            is InspectionResult.Ready -> ExternalInspectionUi(
                file = file,
                pluginId = result.manifest.id,
                name = textResolver.resolve(result.manifest.name),
                version = result.manifest.version,
                size = result.digests.size,
                sha256 = result.digests.bundleSha256,
                publisherName = result.publisher.name.orEmpty(),
                publisherKeyId = result.publisher.keyId,
                trusted = result.trusted != null,
                requiresPrivilege = result.manifest.requiresPrivilege,
                provides = result.manifest.provides,
                error = null,
            )

            is InspectionResult.Rejected -> ExternalInspectionUi(
                file = file,
                pluginId = "",
                name = file.name,
                version = "",
                size = file.length(),
                sha256 = "",
                publisherName = "",
                publisherKeyId = "",
                trusted = false,
                requiresPrivilege = false,
                provides = io.github.whywood.deepdeep.plugin.api.Provides(),
                error = result.reason,
            )
        }
    }

    override suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi {
        val runtime = external ?: return ExternalInstallUi(false, strings.get(R.string.plugins_error_unknown))
        return withContext(Dispatchers.IO) {
            try {
                when (val result = runtime.install(file, acceptPublisher)) {
                    is InstallResult.Installed -> {
                        runtime.activate(result.record)
                        activateRecordLocked(result.record)
                        refreshNow()
                        plugins[result.record.id]?.let { managed ->
                            runCatching { managed.plugin?.onEnable(managed.host) }
                            syncSkillsLocked(managed)
                        }
                        onContributionsChanged()
                        ExternalInstallUi(true, null, restartRequired = false)
                    }

                    is InstallResult.Staged -> {
                        plugins[result.record.id]?.let { managed ->
                            managed.phase = PluginPhase.PENDING_RESTART
                            registry.updatePhase(result.record.id, PluginPhase.PENDING_RESTART)
                        }
                        refreshNow()
                        ExternalInstallUi(true, null, restartRequired = true)
                    }

                    is InstallResult.AlreadyInstalled -> ExternalInstallUi(true, null)

                    is InstallResult.NeedsPublisherConfirmation -> ExternalInstallUi(
                        success = false,
                        error = null,
                        requiresConfirmation = true,
                    )

                    is InstallResult.Rejected -> ExternalInstallUi(false, rejectionText(result.reason))
                }
            } catch (error: Exception) {
                ExternalInstallUi(false, error.message)
            }
        }
    }

    override suspend fun checkExternalUpdates(): List<PluginUpdateUi> {
        val support = externalUpdates ?: return emptyList()
        val result = support.checkUpdates()
        val updates = when (val typedResult = result) {
            is io.github.whywood.deepdeep.plugin.host.update.UpdateCheckResultUi.Ready -> typedResult.candidates.map { candidate ->
                PluginUpdateUi(
                    pluginId = candidate.pluginId,
                    name = plugins[candidate.pluginId]?.let { textResolver.resolve(it.manifest.name) } ?: candidate.pluginId,
                    installedVersion = candidate.installedVersion,
                    availableVersion = candidate.availableVersion,
                    bundleSha256 = candidate.sha256,
                    size = candidate.size,
                    publisherKeyId = candidate.publisherKeyId,
                )
            }

            else -> emptyList()
        }
        _state.update { it.copy(availableUpdates = updates) }
        return updates
    }

    override suspend fun pluginSourceSettings(): io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings =
        externalUpdates?.loadSettings() ?: io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings()

    override suspend fun savePluginSourceSettings(settings: io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings) {
        externalUpdates?.saveSettings(settings)
        scheduleUpdateCheck()
    }

    override suspend fun refreshAvailableUpdates(): List<PluginUpdateUi> = checkExternalUpdates()

    override suspend fun stageExternalUpdate(pluginId: String): String? {
        val support = externalUpdates ?: return "plugin updates are unavailable"
        return when (val result = support.stageUpdate(pluginId)) {
            is io.github.whywood.deepdeep.plugin.host.update.UpdateStageResultUi.Staged -> {
                refreshNow()
                null
            }

            is io.github.whywood.deepdeep.plugin.host.update.UpdateStageResultUi.Rejected -> result.reason
        }
    }

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

    internal fun slotRegistry(pluginsRoot: File): io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry =
        io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry(
            snapshotProvider = { registrySnapshot },
            pluginsRoot = pluginsRoot,
        )

    private suspend fun installInternal(managed: ManagedPlugin) {
        val plugin = managed.plugin ?: throw IllegalStateException(managed.error ?: "plugin unavailable")
        val label = textResolver.resolve(managed.manifest.name)
        val firstInstall = managed.installedVersion == null
        setOperation(managed.manifest.id, label, strings.get(R.string.plugins_stage_preparing))
        notifyInstall(label, strings.get(R.string.plugins_stage_preparing), null)
        try {
            plugin.install(managed.host, progressUi(managed, label))
            managed.installedVersion = managed.manifest.version
            managed.phase = PluginPhase.INSTALLED
            managed.error = null
            if (managed.builtin) {
                builtinStateStore.upsert(
                    managed.manifest.id,
                    BuiltinPluginState(true, managed.enabled, managed.manifest.version),
                )
            }
            notifyInstallFinished(label, true, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            managed.error = error.message
            if (firstInstall) runCatching { plugin.uninstall(managed.host) }
            notifyInstallFinished(label, false, error.message)
            throw error
        } finally {
            _state.update { it.copy(operation = null) }
        }
    }

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
        managed.phase = when {
            managed.availability is PluginAvailabilityUi.Unavailable -> PluginPhase.DEGRADED
            managed.status is PluginStatusUi.Failed -> PluginPhase.FAILED
            else -> PluginPhase.ACTIVE
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

    private fun attachExternalHandle(record: ExternalPluginRecord) {
        val managed = plugins[record.id] ?: return
        val handle = external?.handle(record.id) ?: return
        managed.plugin = handle.plugin
        managed.manifest = handle.manifest
        managed.externalRecord = record
        managed.installedVersion = record.version
        managed.enabled = record.enabled
        managed.host = hostFor(managed)
        registry.update(record.id) { descriptor ->
            descriptor.copy(
                manifest = handle.manifest,
                plugin = handle.plugin,
                source = PluginSource.EXTERNAL,
                phase = if (record.enabled) PluginPhase.INSTALLED else PluginPhase.DISABLED,
            )
        }
    }

    private suspend fun activateRecordLocked(record: ExternalPluginRecord) {
        if (!plugins.containsKey(record.id)) {
            val managed = ManagedPlugin(
                manifest = manifestForRecord(record),
                plugin = null,
                builtin = false,
                source = PluginSource.EXTERNAL,
                installedVersion = record.version,
                enabled = record.enabled,
                phase = PluginPhase.INSTALLED,
            )
            managed.host = hostFor(managed)
            plugins[record.id] = managed
            registry.register(managed.toDescriptor())
        }
        attachExternalHandle(record)
    }

    private suspend fun manifestForRecord(record: ExternalPluginRecord): PluginManifest {
        val bundleFile = runCatching {
            when (val active = external?.activeBundle(record.id)) {
                is BundleState.Ready -> active.bundleFile
                else -> null
            }
        }.getOrNull()
        if (bundleFile != null) {
            runCatching {
                val pkg = io.github.whywood.deepdeep.plugin.host.external.DdpPackage.read(bundleFile)
                io.github.whywood.deepdeep.plugin.api.PluginManifestCodec.parse(pkg.manifestJson())
            }.getOrNull()?.let { return it }
        }
        return PluginManifest(
            id = record.id,
            version = record.version,
            name = LocalizedText.Literal(record.id),
            summary = LocalizedText.Literal(""),
            source = PluginSource.EXTERNAL,
            installable = true,
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
        enabled &&
            installedVersion != null &&
            availability is PluginAvailabilityUi.Available &&
            phase.contributes

    private fun ManagedPlugin.updateAvailable(): Boolean {
        val record = externalRecord ?: return false
        return record.pendingVersion != null && record.pendingVersion != installedVersion
    }

    private fun ManagedPlugin.toDescriptor(): PluginDescriptor = PluginDescriptor(
        manifest = manifest,
        plugin = plugin ?: PlaceholderPlugin,
        source = source,
        phase = phase,
        error = error,
        contributions = contributions,
    )

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

    private fun setupUi(managed: ManagedPlugin): PluginSetupUi = object : PluginSetupUi {
        override fun stage(
            title: LocalizedText,
            message: LocalizedText?,
            url: String?,
            imagePath: String?,
            progress: Int?,
        ) {
            val titleText = textResolver.resolve(title)
            _state.update {
                it.copy(
                    interaction = PluginInteraction(
                        pluginId = managed.manifest.id,
                        title = textResolver.resolve(managed.manifest.name),
                        stageTitle = titleText,
                        stageMessage = message?.let(textResolver::resolve),
                        url = url,
                        imagePath = imagePath,
                        progress = progress,
                    ),
                )
            }
        }

        override fun progress(percent: Int?, label: LocalizedText) {
            _state.update {
                it.copy(
                    interaction = it.interaction?.copy(
                        progress = percent,
                        stageMessage = textResolver.resolve(label),
                    ),
                )
            }
        }

        override fun cancelled(): Boolean = setupCancelled.get()
    }

    private fun progressUi(managed: ManagedPlugin, label: String): io.github.whywood.deepdeep.plugin.api.PluginProgress =
        object : io.github.whywood.deepdeep.plugin.api.PluginProgress {
            override fun report(percent: Int?, label: LocalizedText) {
                val stage = textResolver.resolve(label)
                _state.update { state ->
                    state.copy(
                        operation = PluginOperation(managed.manifest.id, label(managed), stage, percent),
                        interaction = state.interaction?.let { interaction ->
                            if (interaction.pluginId == managed.manifest.id) {
                                interaction.copy(stageTitle = stage, progress = percent)
                            } else {
                                interaction
                            }
                        },
                    )
                }
                notifyInstall(label(managed), stage, percent)
            }
        }

    private fun label(managed: ManagedPlugin): String = textResolver.resolve(managed.manifest.name)

    private suspend fun awaitInstall(managed: ManagedPlugin): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmWaiter.set(deferred)
        _state.update {
            it.copy(
                interaction = PluginInteraction(
                    pluginId = managed.manifest.id,
                    title = textResolver.resolve(managed.manifest.name),
                    stageTitle = strings.get(R.string.plugins_stage_confirm),
                    confirmInstall = true,
                ),
                busy = true,
            )
        }
        val confirmed = try {
            deferred.await()
        } finally {
            confirmWaiter.set(null)
        }
        _state.update { state ->
            state.copy(
                interaction = if (confirmed) {
                    state.interaction?.copy(
                        confirmInstall = false,
                        stageTitle = strings.get(R.string.plugins_stage_installing),
                        stageMessage = null,
                    )
                } else {
                    null
                },
            )
        }
        return confirmed
    }

    private fun completeConfirm(value: Boolean) {
        confirmWaiter.getAndSet(null)?.complete(value)
    }

    private suspend fun beginBusy(): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        _state.update { it.copy(busy = true, error = null) }
        return true
    }

    private fun finishBusy() {
        busy.set(false)
        _state.update { it.copy(busy = false, operation = null, interaction = null) }
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

    private fun rejectionText(reason: String): String = reason

    private data class ManagedPlugin(
        var manifest: PluginManifest,
        var plugin: DeepDeepPlugin?,
        val builtin: Boolean,
        var source: PluginSource,
        var installedVersion: String?,
        var enabled: Boolean,
        var phase: PluginPhase,
        var error: String? = null,
        var availability: PluginAvailabilityUi = PluginAvailabilityUi.Available,
        var status: PluginStatusUi? = null,
        var contributions: PluginContributions = PluginContributions.Empty,
        var externalRecord: ExternalPluginRecord? = null,
        var skillIds: List<String> = emptyList(),
    ) {
        lateinit var host: PluginHost
    }

    private object PlaceholderPlugin : DeepDeepPlugin {
        override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Unavailable("")
        override suspend fun status(host: PluginHost): PluginStatus = PluginStatus.Failed("")
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
