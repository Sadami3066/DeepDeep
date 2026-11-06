package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.external.BundleState
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel

internal class PluginExternalActivation(
    private val gate: PluginRuntimeRegistry,
    private val lifecycle: PluginLifecycle,
    private val external: ExternalPluginRuntime?,
) {
    private data class PendingActivation(
        val handle: LoadedPluginHandle,
        val runtime: ManagedPlugin,
        val record: PluginRecord,
    )

    private val protocol = PluginActivationProtocol(
        prepare = ::prepare,
        probe = ::probe,
        commit = ::commit,
        swap = ::swap,
        discard = ::discard,
        abort = ::abort,
    )

    suspend fun activate(pluginId: String) {
        val runtime = external ?: return
        val pendingVersion = runtime.record(pluginId)?.pendingVersion ?: return
        runtime.beginActivation(pluginId, pendingVersion)
        try {
            if (protocol.activate(pluginId) is ActivationOutcome.Activated) {
                runtime.clearStartupCount(pluginId)
            }
        } finally {
            runtime.completeActivation(pluginId)
        }
    }

    private suspend fun prepare(pluginId: String): ActivationCandidate? {
        val runtime = external ?: return null
        val record = runtime.record(pluginId) ?: return null
        if (!record.enabled || record.pendingVersion == null) return null
        val handle = when (val bundle = runtime.pendingBundle(pluginId)) {
            is BundleState.Ready -> try {
                runtime.loadCandidate(record, bundle.bundleFile, bundle.bundleSha256)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                discardPrepared(pluginId, error.message ?: error.javaClass.simpleName)
                return null
            }

            else -> {
                discardPrepared(pluginId, (bundle as? BundleState.Missing)?.reason ?: "pending bundle is unavailable")
                return null
            }
        }
        val candidateRecord = PluginRecord(
            id = pluginId,
            enabled = true,
            installedVersion = record.pendingVersion,
            previousVersion = record.version,
        )
        val candidateRuntime = lifecycle.createRuntime(
            record = candidateRecord,
            manifest = handle.manifest,
            externalRecord = record,
        )
        candidateRuntime.plugin = handle.plugin
        return ActivationCandidate(
            pluginId = pluginId,
            version = handle.version,
            fromVersion = record.version,
            payload = PendingActivation(handle, candidateRuntime, candidateRecord),
        )
    }

    private suspend fun probe(candidate: ActivationCandidate): String? {
        val pending = candidate.payload as? PendingActivation ?: return "activation candidate is missing"
        val view = pending.runtime.view(pending.record)
        val plugin = view.plugin ?: return "candidate plugin is not loaded"
        return try {
            candidate.fromVersion?.let { fromVersion -> plugin.onUpgrade(view.host, fromVersion) }
            plugin.availability(view.host)
            plugin.contributions(view.host)
            plugin.skills(view.host)
            plugin.ui(view.host)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error.message ?: error.javaClass.simpleName
        }
    }

    private suspend fun commit(candidate: ActivationCandidate): Boolean {
        val runtime = external ?: return false
        val committed = runtime.commitPending(candidate.pluginId) ?: return false
        gate.attachExternalRecord(candidate.pluginId, committed)
        gate.updateRecord(candidate.pluginId) {
            it.copy(
                installedVersion = committed.version,
                pendingVersion = null,
                previousVersion = candidate.fromVersion,
                lastError = null,
            )
        }
        gate.clearFailure(candidate.pluginId)
        return true
    }

    private suspend fun swap(candidate: ActivationCandidate): String? {
        val runtime = external ?: return "external runtime is unavailable"
        val pending = candidate.payload as? PendingActivation ?: return "activation candidate is missing"
        val previous = gate.view(candidate.pluginId) ?: return "plugin is not registered"
        previous.scope.cancel()
        runCatching { previous.externalHandle?.close() }
        runtime.adopt(candidate.pluginId, pending.handle)
        val attached = gate.markLoaded(
            pluginId = candidate.pluginId,
            manifest = pending.handle.manifest,
            plugin = pending.handle.plugin,
            host = pending.runtime.host,
            scope = pending.runtime.scope,
            externalHandle = pending.handle,
        )
        return if (attached == null) "plugin is not registered" else null
    }

    private suspend fun discard(candidate: ActivationCandidate, reason: String?) {
        discardCandidate(candidate)
        val runtime = external ?: return
        runtime.discardPending(candidate.pluginId, reason ?: "activation failed")
        runtime.record(candidate.pluginId)?.let { record ->
            gate.attachExternalRecord(candidate.pluginId, record)
            gate.updateRecord(candidate.pluginId) { it.copy(pendingVersion = null) }
            reason?.let { message -> gate.markError(candidate.pluginId, message) }
        }
    }

    private suspend fun abort(candidate: ActivationCandidate, reason: String?) {
        discardCandidate(candidate)
        persistError(candidate.pluginId, reason)
    }

    private suspend fun discardPrepared(pluginId: String, reason: String) {
        val runtime = external ?: return
        runtime.discardPending(pluginId, reason)
        runtime.record(pluginId)?.let { record ->
            gate.attachExternalRecord(pluginId, record)
            gate.updateRecord(pluginId) { it.copy(pendingVersion = null) }
            gate.markError(pluginId, reason)
        }
    }

    private suspend fun persistError(pluginId: String, reason: String?) {
        if (reason == null) return
        val runtime = external ?: return
        runtime.markFailure(pluginId, reason)
        gate.markError(pluginId, reason)
        runtime.record(pluginId)?.let { gate.attachExternalRecord(pluginId, it) }
    }

    private suspend fun discardCandidate(candidate: ActivationCandidate) {
        val pending = candidate.payload as? PendingActivation ?: return
        pending.runtime.scope.cancel()
        runCatching { pending.handle.close() }
    }
}
