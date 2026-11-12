package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.HostLocalizedText
import io.github.whywood.deepdeep.plugin.host.external.HostPluginManifest
import io.github.whywood.deepdeep.plugin.host.external.HostProvides
import io.github.whywood.deepdeep.plugin.host.external.PluginApiLevelRejectedException
import io.github.whywood.deepdeep.plugin.host.external.hostLocalizedText
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PluginExternalFlow(
    private val gate: PluginRuntimeRegistry,
    private val external: ExternalPluginRuntime?,
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
                    persistFailure(view.id, errorMessage(error))
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
            persistFailure(pluginId, errorMessage(error))
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
                provides = result.manifest.provides,
                capabilities = result.manifest.capabilities.map { it.wire }.toSet(),
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
                provides = HostProvides(),
                capabilities = emptySet(),
                error = rejectionMessage(result.code, result.reason, result.declaredApiLevel),
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

                    is InstallResult.Rejected -> ExternalInstallUi(
                        false,
                        rejectionMessage(result.code, result.reason, result.declaredApiLevel),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ExternalInstallUi(false, error.message ?: error.javaClass.simpleName)
            }
        }
    }

    suspend fun hasPendingUpdates(): Boolean =
        gate.views().any { view -> view.enabled && view.record.pendingVersion != null }

    internal suspend fun activateStagedNow(pluginId: String): Boolean {
        if (busySessions().isNotEmpty()) return false
        activation.activate(pluginId)
        refresh()
        lifecycle.syncSkills(pluginId)
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
            val message = errorMessage(error)
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

    private fun fallbackManifest(record: ExternalPluginRecord): PluginManifest = HostPluginManifest(
        id = record.id,
        version = record.version,
        name = hostLocalizedText(record.id),
        summary = HostLocalizedText(emptyMap()),
    )

    private fun rejectionMessage(
        code: io.github.whywood.deepdeep.plugin.host.external.InstallRejection,
        reason: String,
        declaredApiLevel: Int?,
    ): String = when (code) {
        io.github.whywood.deepdeep.plugin.host.external.InstallRejection.API_LEVEL_UNSUPPORTED ->
            strings.get(R.string.plugins_api_level_retired, declaredApiLevel ?: 0)

        else -> reason
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is PluginApiLevelRejectedException -> strings.get(R.string.plugins_api_level_retired, error.declaredApiLevel)
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun ExternalPluginRecord.toPluginRecord(): PluginRecord = PluginRecord(
        id = id,
        enabled = enabled,
        installedVersion = version,
        pendingVersion = pendingVersion,
        previousVersion = previousVersion,
        lastError = lastError,
    )
}
