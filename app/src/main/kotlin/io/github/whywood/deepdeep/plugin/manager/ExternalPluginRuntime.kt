package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.InspectionResult
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import java.io.File

internal class ExternalPluginRuntime(
    private val context: Context,
    private val installer: PluginInstaller,
    private val hostVersion: String?,
    private val hostApiLevel: Int = PLUGIN_API_LEVEL,
) {
    private val handles = linkedMapOf<String, LoadedPluginHandle>()

    fun handle(pluginId: String): LoadedPluginHandle? = handles[pluginId]

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

    suspend fun markPhase(pluginId: String, phase: PluginPhase, error: String? = null): Boolean =
        installer.markPhase(pluginId, phase, error)

    suspend fun activateOnStartup(): List<ExternalPluginRecord> {
        val loaded = mutableListOf<ExternalPluginRecord>()
        installer.records().forEach { record ->
            val activated = activate(record)
            if (activated != null) loaded += activated
        }
        return loaded
    }

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
                        installer.markPhase(record.id, PluginPhase.ACTIVE)
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
            installer.markPhase(record.id, PluginPhase.FAILED, (active as? BundleState.Missing)?.reason)
            return null
        }
        val loaded = try {
            load(record, active.bundleFile, active.bundleSha256)
        } catch (error: Exception) {
            installer.markPhase(record.id, PluginPhase.FAILED, error.message ?: error.javaClass.simpleName)
            return null
        }
        handles[record.id] = loaded
        installer.markPhase(record.id, PluginPhase.ACTIVE)
        return installer.record(record.id) ?: record
    }

    suspend fun unload(pluginId: String) {
        handles.remove(pluginId)?.let { runCatching { it.close() } }
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
