package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.Provides
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.update.ExternalUpdateSupport
import io.github.whywood.deepdeep.plugin.host.update.PluginRepositorySettings
import io.github.whywood.deepdeep.plugin.host.update.UpdateCheckResultUi
import io.github.whywood.deepdeep.plugin.host.update.UpdateStageResultUi
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PluginExternalFlow(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val gate: PluginRuntimeRegistry,
    private val registry: PluginRegistry,
    private val external: ExternalPluginRuntime?,
    private val externalUpdates: ExternalUpdateSupport?,
    private val strings: PluginStrings,
    private val textResolver: PluginTextResolver,
    private val lifecycle: PluginLifecycle,
    private val refresh: suspend () -> Unit,
    private val onContributionsChanged: () -> Unit,
    private val busySessions: () -> Set<String> = { emptySet() },
) {
    private val activation = PluginExternalActivation(
        gate = gate,
        lifecycle = lifecycle,
        external = external,
    )

    suspend fun registerRecords() {
        val runtime = external ?: return
        runtime.records().forEach { record ->
            if (gate.contains(record.id)) return@forEach
            registerRecord(record)
        }
    }

    suspend fun registerRecord(record: ExternalPluginRecord) {
        if (gate.contains(record.id)) return
        val manifest = external?.manifestFor(record) ?: fallbackManifest(record)
        val pluginRecord = record.toPluginRecord()
        val managed = lifecycle.createRuntime(
            record = pluginRecord,
            manifest = manifest,
            externalRecord = record,
        )
        gate.register(pluginRecord, managed)
    }

    suspend fun activateEnabledRecords(skipPluginIds: Set<String> = emptySet()) {
        val runtime = external ?: return
        gate.views()
            .filter { view -> view.enabled && view.plugin == null && view.id !in skipPluginIds }
            .forEach { view ->
                val record = view.externalRecord ?: runtime.record(view.id) ?: return@forEach
                if (record.pendingVersion != null) return@forEach
                val activated = try {
                    runtime.activate(record)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    persistFailure(view.id, error.message ?: error.javaClass.simpleName)
                    runtime.clearStartupCount(view.id)
                    return@forEach
                } ?: run {
                    runtime.clearStartupCount(view.id)
                    return@forEach
                }
                val handle = runtime.handle(view.id) ?: run {
                    runtime.clearStartupCount(view.id)
                    return@forEach
                }
                gate.attachExternalRecord(view.id, activated)
                gate.updateRecord(view.id) {
                    it.copy(installedVersion = activated.version, enabled = activated.enabled)
                }
                val current = gate.view(view.id) ?: run {
                    runtime.clearStartupCount(view.id)
                    return@forEach
                }
                if (lifecycle.attachHandle(current, handle) == null) {
                    runtime.clearStartupCount(view.id)
                    return@forEach
                }
                runtime.clearStartupCount(view.id)
                clearErrors(view.id)
            }
    }

    suspend fun activatePendingVersions(skipPluginIds: Set<String> = emptySet()) {
        if (external == null) return
        gate.views()
            .filter { view -> view.enabled && view.record.pendingVersion != null && view.id !in skipPluginIds }
            .forEach { view -> activation.activate(view.id) }
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

    suspend fun loadForEnable(pluginId: String): Boolean {
        val runtime = external ?: return false
        val view = gate.view(pluginId) ?: return false
        if (view.plugin != null) return true
        val record = runtime.record(pluginId) ?: return false
        val activated = try {
            runtime.activate(record)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            persistFailure(pluginId, error.message ?: error.javaClass.simpleName)
            return false
        } ?: return false
        val handle = runtime.handle(pluginId) ?: return false
        gate.attachExternalRecord(pluginId, activated)
        gate.updateRecord(pluginId) {
            it.copy(installedVersion = activated.version, enabled = activated.enabled)
        }
        val current = gate.view(pluginId) ?: return false
        if (lifecycle.attachHandle(current, handle) == null) return false
        clearErrors(pluginId)
        return true
    }

    suspend fun persistEnabled(pluginId: String, enabled: Boolean) {
        external?.setEnabled(pluginId, enabled)
        external?.record(pluginId)?.let { record -> gate.attachExternalRecord(pluginId, record) }
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
                capabilities = result.manifest.capabilities.orEmpty(),
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
                capabilities = emptySet(),
                error = result.reason,
            )
        }
    }

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi {
        val runtime = external ?: return ExternalInstallUi(false, strings.get(R.string.plugins_error_unknown))
        return withContext(Dispatchers.IO) {
            try {
                when (val result = runtime.install(file, acceptPublisher)) {
                    is InstallResult.Installed -> {
                        registerRecord(result.record)
                        val error = installFresh(result.record, runtime)
                        refresh()
                        onContributionsChanged()
                        ExternalInstallUi(error == null, error, restartRequired = false)
                    }

                    is InstallResult.Staged -> {
                        registerRecord(result.record)
                        runtime.record(result.record.id)?.let { staged ->
                            gate.attachExternalRecord(staged.id, staged)
                            gate.updateRecord(staged.id) {
                                it.copy(pendingVersion = staged.pendingVersion, enabled = staged.enabled)
                            }
                        }
                        refresh()
                        onContributionsChanged()
                        val activated = activateStagedNow(result.record.id)
                        ExternalInstallUi(true, null, restartRequired = !activated)
                    }

                    is InstallResult.AlreadyInstalled -> ExternalInstallUi(true, null)

                    is InstallResult.NeedsPublisherConfirmation -> ExternalInstallUi(
                        success = false,
                        error = null,
                        requiresConfirmation = true,
                    )

                    is InstallResult.Rejected -> ExternalInstallUi(false, result.reason)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ExternalInstallUi(false, error.message ?: error.javaClass.simpleName)
            }
        }
    }

    suspend fun checkExternalUpdates(): List<PluginUpdateUi> {
        val support = externalUpdates ?: return emptyList()
        val updates = when (val typedResult = support.checkUpdates()) {
            is UpdateCheckResultUi.Ready -> typedResult.candidates.map { candidate ->
                PluginUpdateUi(
                    pluginId = candidate.pluginId,
                    name = gate.view(candidate.pluginId)?.let { view ->
                        textResolver.resolve(view.manifest.name)
                    } ?: candidate.pluginId,
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

    suspend fun pluginRepositorySettings(): PluginRepositorySettings =
        externalUpdates?.loadSettings() ?: PluginRepositorySettings()

    suspend fun savePluginRepositorySettings(settings: PluginRepositorySettings) {
        externalUpdates?.saveSettings(settings)
        scheduleUpdateCheck()
    }

    suspend fun stageExternalUpdate(pluginId: String): String? {
        val support = externalUpdates ?: return "plugin updates are unavailable"
        return when (val result = support.stageUpdate(pluginId)) {
            is UpdateStageResultUi.Staged -> {
                external?.record(pluginId)?.let { record ->
                    gate.updateRecord(pluginId) { it.copy(pendingVersion = record.pendingVersion) }
                    gate.attachExternalRecord(pluginId, record)
                }
                refresh()
                activateStagedNow(pluginId)
                null
            }

            is UpdateStageResultUi.Rejected -> result.reason
        }
    }

    suspend fun hasPendingUpdates(): Boolean =
        gate.views().any { view -> view.enabled && view.record.pendingVersion != null }

    private suspend fun activateStagedNow(pluginId: String): Boolean {
        if (busySessions().isNotEmpty()) return false
        activation.activate(pluginId)
        refresh()
        onContributionsChanged()
        return gate.view(pluginId)?.record?.pendingVersion == null
    }

    private suspend fun installFresh(record: ExternalPluginRecord, runtime: ExternalPluginRuntime): String? {
        val pluginId = record.id
        val activated = try {
            runtime.activate(record)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            persistFailure(pluginId, message)
            return message
        } ?: run {
            val message = runtime.record(pluginId)?.lastError ?: "plugin bundle could not be loaded"
            persistFailure(pluginId, message)
            return message
        }
        val handle = runtime.handle(pluginId) ?: run {
            persistFailure(pluginId, "plugin bundle could not be loaded")
            return "plugin bundle could not be loaded"
        }
        gate.attachExternalRecord(pluginId, activated)
        gate.updateRecord(pluginId) {
            it.copy(installedVersion = activated.version, enabled = activated.enabled, pendingVersion = null)
        }
        val view = gate.view(pluginId) ?: run {
            persistFailure(pluginId, "plugin is not registered")
            return "plugin is not registered"
        }
        val attached = lifecycle.attachHandle(view, handle) ?: run {
            persistFailure(pluginId, "plugin is not registered")
            return "plugin is not registered"
        }
        val enableFailure = if (attached.enabled) lifecycle.enable(attached) else null
        if (enableFailure != null) {
            persistFailure(pluginId, enableFailure)
            return enableFailure
        }
        runtime.clearStartupCount(pluginId)
        refresh()
        lifecycle.syncSkills(pluginId)
        return null
    }

    suspend fun recordFailure(pluginId: String, message: String?) {
        if (message == null) {
            clearErrors(pluginId)
        } else {
            persistFailure(pluginId, message)
        }
    }

    private suspend fun persistFailure(pluginId: String, message: String) {
        external?.markFailure(pluginId, message)
        gate.markFailure(pluginId, message)
        external?.record(pluginId)?.let { record -> gate.attachExternalRecord(pluginId, record) }
    }

    private suspend fun clearErrors(pluginId: String) {
        external?.markFailure(pluginId, null)
        gate.clearFailure(pluginId)
        external?.record(pluginId)?.let { record -> gate.attachExternalRecord(pluginId, record) }
    }

    private fun fallbackManifest(record: ExternalPluginRecord): PluginManifest = PluginManifest(
        id = record.id,
        version = record.version,
        name = LocalizedText.Literal(record.id),
        summary = LocalizedText.Literal(""),
    )

    private fun ExternalPluginRecord.toPluginRecord(): PluginRecord = PluginRecord(
        id = id,
        enabled = enabled,
        installedVersion = version,
        pendingVersion = pendingVersion,
        previousVersion = previousVersion,
        lastError = lastError,
    )
}
