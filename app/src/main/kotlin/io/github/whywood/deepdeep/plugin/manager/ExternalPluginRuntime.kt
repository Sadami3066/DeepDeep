package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginManifestCodec
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.host.external.ActivationMarker
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.DdpPackage
import io.github.whywood.deepdeep.plugin.host.external.CommitResult
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecovery
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import java.io.File
import kotlinx.coroutines.CancellationException

internal class ExternalPluginRuntime(
    private val context: Context,
    private val installer: PluginInstaller,
    private val hostVersion: String?,
    private val hostApiLevel: Int = PLUGIN_API_LEVEL,
) {
    private val handles = linkedMapOf<String, LoadedPluginHandle>()

    private val recovery = ExternalPluginRecovery(installer.pluginsRoot, installer.recordStore)

    fun handle(pluginId: String): LoadedPluginHandle? = handles[pluginId]

    suspend fun beginActivation(pluginId: String, targetVersion: String): Int? =
        recovery.beginActivation(pluginId, targetVersion)

    suspend fun completeActivation(pluginId: String): Boolean = recovery.completeActivation(pluginId)

    suspend fun interruptedActivation(pluginId: String): ActivationMarker? =
        recovery.interruptedActivation(pluginId)

    suspend fun noteStartup(pluginId: String, version: String): Int = recovery.noteStartup(pluginId, version)

    suspend fun clearStartupCount(pluginId: String): Boolean = recovery.clearStartupCount(pluginId)

    suspend fun rollbackToPrevious(pluginId: String): ExternalPluginRecord? =
        recovery.rollbackToPrevious(pluginId)

    suspend fun pruneVersions(pluginId: String): Boolean = recovery.pruneVersions(pluginId)

    suspend fun records(): List<ExternalPluginRecord> = installer.records()

    suspend fun record(pluginId: String): ExternalPluginRecord? = installer.record(pluginId)

    suspend fun inspect(file: File): InspectionResult = installer.inspect(file)

    suspend fun activeBundle(pluginId: String): BundleState = installer.activeBundle(pluginId)

    suspend fun pendingBundle(pluginId: String): BundleState = installer.pendingBundle(pluginId)

    suspend fun install(file: File, acceptPublisher: Boolean): InstallResult =
        installer.install(file, acceptPublisher)

    suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean = installer.setEnabled(pluginId, enabled)

    suspend fun updateSkills(pluginId: String, skillIds: List<String>): Boolean =
        installer.updateSkills(pluginId, skillIds)

    suspend fun markFailure(pluginId: String, error: String?): Boolean =
        installer.markFailure(pluginId, error)

    suspend fun activate(record: ExternalPluginRecord): ExternalPluginRecord? {
        handles[record.id]?.let { existing ->
            if (!existing.isClosed && existing.matches(record.version, record.activeBundleSha256)) return record
            runCatching { existing.close() }
            handles.remove(record.id)
        }
        val pending = installer.pendingBundle(record.id)
        if (pending is BundleState.Ready) {
            val staged = runCatching { load(record, pending.bundleFile, pending.bundleSha256) }.getOrNull()
            if (staged != null) {
                when (val committed = installer.commitPending(record.id)) {
                    is io.github.whywood.deepdeep.plugin.host.external.CommitResult.Committed -> {
                        handles[record.id] = staged
                        installer.markFailure(record.id, null)
                        return committed.record
                    }

                    is io.github.whywood.deepdeep.plugin.host.external.CommitResult.Rejected -> {
                        runCatching { staged.close() }
                        installer.discardPending(record.id, committed.reason)
                    }
                }
            } else {
                installer.discardPending(record.id, "pending plugin bundle failed to load")
            }
        }
        val active = installer.activeBundle(record.id)
        if (active !is BundleState.Ready) {
            installer.markFailure(record.id, (active as? BundleState.Missing)?.reason)
            return null
        }
        val loaded = try {
            load(record, active.bundleFile, active.bundleSha256)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            installer.markFailure(record.id, error.message ?: error.javaClass.simpleName)
            return null
        }
        handles[record.id] = loaded
        installer.markFailure(record.id, null)
        return installer.record(record.id) ?: record
    }

    suspend fun unload(pluginId: String) {
        handles.remove(pluginId)?.let { runCatching { it.close() } }
    }

    fun forget(pluginId: String) {
        handles.remove(pluginId)
    }

    fun loadCandidate(record: ExternalPluginRecord, bundle: File, sha256: String): LoadedPluginHandle =
        load(record, bundle, sha256)

    suspend fun adopt(pluginId: String, handle: LoadedPluginHandle) {
        handles.put(pluginId, handle)?.let { previous ->
            if (previous !== handle) runCatching { previous.close() }
        }
    }

    suspend fun commitPending(pluginId: String): ExternalPluginRecord? =
        when (val result = installer.commitPending(pluginId)) {
            is CommitResult.Committed -> result.record
            is CommitResult.Rejected -> null
        }

    suspend fun discardPending(pluginId: String, reason: String?) {
        installer.discardPending(pluginId, reason ?: "activation failed")
    }

    suspend fun manifestFor(record: ExternalPluginRecord): PluginManifest {
        val bundleFile = runCatching {
            when (val active = installer.activeBundle(record.id)) {
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

    suspend fun uninstall(pluginId: String): Boolean {
        unload(pluginId)
        return installer.uninstall(pluginId)
    }

    suspend fun close() {
        handles.values.forEach { handle -> runCatching { handle.close() } }
        handles.clear()
    }

    private fun load(
        record: ExternalPluginRecord,
        bundle: File,
        sha256: String,
    ): LoadedPluginHandle = LoadedPluginHandle.load(
        context = context,
        bundleFile = bundle,
        expectedSha256 = sha256,
        expectedPublisherKeyId = record.publisherKeyId,
        hostApiLevel = hostApiLevel,
        hostVersion = hostVersion,
    )
}
