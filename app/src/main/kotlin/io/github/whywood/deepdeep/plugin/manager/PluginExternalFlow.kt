package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginManifestCodec
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.Provides
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.DdpPackage
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.update.ExternalUpdateSupport
import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.plugin.host.update.UpdateCheckResultUi
import io.github.whywood.deepdeep.plugin.host.update.UpdateStageResultUi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PluginExternalFlow(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val plugins: MutableMap<String, ManagedPlugin>,
    private val registry: PluginRegistry,
    private val external: ExternalPluginRuntime?,
    private val externalUpdates: ExternalUpdateSupport?,
    private val strings: PluginStrings,
    private val textResolver: PluginTextResolver,
    private val hostFor: (ManagedPlugin) -> PluginHost,
    private val refresh: suspend () -> Unit,
    private val syncSkills: suspend (ManagedPlugin) -> Unit,
    private val onContributionsChanged: () -> Unit,
) {
    suspend fun registerRecords() {
        val runtime = external ?: return
        runtime.records().forEach { record ->
            if (plugins.containsKey(record.id)) return@forEach
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
    }

    suspend fun activateEnabled() {
        val runtime = external ?: return
        runtime.records().filter { it.enabled }.forEach { record ->
            val previousVersion = plugins[record.id]?.externalRecord?.version
            val activated = runtime.activate(record) ?: return@forEach
            plugins[record.id]?.let { managed ->
                managed.externalRecord = activated
                managed.installedVersion = activated.version
                managed.enabled = activated.enabled
            }
            attach(activated)
            if (previousVersion != null && previousVersion != activated.version) {
                plugins[record.id]?.let { managed ->
                    managed.plugin?.let { plugin ->
                        runCatching { plugin.onUpgrade(managed.host, previousVersion) }
                    }
                }
            }
        }
    }

    fun scheduleUpdateCheck() {
        val support = externalUpdates ?: return
        scope.launch {
            runCatching {
                val settings = support.loadSettings()
                if (!settings.autoCheck || settings.indexUrl.isNullOrBlank()) return@runCatching
                val updates = checkExternalUpdates()
                state.update { it.copy(availableUpdates = updates) }
            }
        }
    }

    suspend fun inspectExternal(file: File): ExternalInspectionUi? {
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
                provides = Provides(),
                error = result.reason,
            )
        }
    }

    suspend fun activateExisting(pluginId: String) {
        val record = external?.record(pluginId) ?: return
        external?.activate(record)?.let(::attach)
    }

    suspend fun setExternalEnabled(pluginId: String, enabled: Boolean) {
        external?.setEnabled(pluginId, enabled)
        if (enabled) activateExisting(pluginId)
    }

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi {
        val runtime = external ?: return ExternalInstallUi(false, strings.get(R.string.plugins_error_unknown))
        return withContext(Dispatchers.IO) {
            try {
                when (val result = runtime.install(file, acceptPublisher)) {
                    is InstallResult.Installed -> {
                        runtime.activate(result.record)
                        activateRecord(result.record)
                        refresh()
                        plugins[result.record.id]?.let { managed ->
                            runCatching { managed.plugin?.onEnable(managed.host) }
                            syncSkills(managed)
                        }
                        onContributionsChanged()
                        ExternalInstallUi(true, null, restartRequired = false)
                    }

                    is InstallResult.Staged -> {
                        plugins[result.record.id]?.let { managed ->
                            managed.phase = PluginPhase.PENDING_RESTART
                            registry.updatePhase(result.record.id, PluginPhase.PENDING_RESTART)
                        }
                        refresh()
                        ExternalInstallUi(true, null, restartRequired = true)
                    }

                    is InstallResult.AlreadyInstalled -> ExternalInstallUi(true, null)

                    is InstallResult.NeedsPublisherConfirmation -> ExternalInstallUi(
                        success = false,
                        error = null,
                        requiresConfirmation = true,
                    )

                    is InstallResult.Rejected -> ExternalInstallUi(false, result.reason)
                }
            } catch (error: Exception) {
                ExternalInstallUi(false, error.message)
            }
        }
    }

    suspend fun checkExternalUpdates(): List<PluginUpdateUi> {
        val support = externalUpdates ?: return emptyList()
        val updates = when (val typedResult = support.checkUpdates()) {
            is UpdateCheckResultUi.Ready -> typedResult.candidates.map { candidate ->
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
        state.update { it.copy(availableUpdates = updates) }
        return updates
    }

    suspend fun pluginSourceSettings(): PluginSourceSettings =
        externalUpdates?.loadSettings() ?: PluginSourceSettings()

    suspend fun savePluginSourceSettings(settings: PluginSourceSettings) {
        externalUpdates?.saveSettings(settings)
        scheduleUpdateCheck()
    }

    suspend fun stageExternalUpdate(pluginId: String): String? {
        val support = externalUpdates ?: return "plugin updates are unavailable"
        return when (val result = support.stageUpdate(pluginId)) {
            is UpdateStageResultUi.Staged -> {
                external?.record(pluginId)?.let { record ->
                    plugins[pluginId]?.let { managed ->
                        managed.externalRecord = record
                        managed.phase = PluginPhase.PENDING_RESTART
                        registry.updatePhase(pluginId, PluginPhase.PENDING_RESTART)
                    }
                }
                refresh()
                null
            }

            is UpdateStageResultUi.Rejected -> result.reason
        }
    }

    private fun attach(record: ExternalPluginRecord) {
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

    private suspend fun activateRecord(record: ExternalPluginRecord) {
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
        attach(record)
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
                val pkg = DdpPackage.read(bundleFile)
                PluginManifestCodec.parse(pkg.manifestJson())
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
}
