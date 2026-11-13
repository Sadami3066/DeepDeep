package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import kotlinx.coroutines.CoroutineScope

internal class PluginRuntimeRegistry {
    private val lock = Any()
    private val records = LinkedHashMap<String, PluginRecord>()
    private val runtimes = LinkedHashMap<String, ManagedPlugin>()

    fun register(record: PluginRecord, runtime: ManagedPlugin): Boolean = synchronized(lock) {
        if (records.containsKey(record.id)) return false
        records[record.id] = record
        runtimes[record.id] = runtime
        true
    }

    fun release(pluginId: String): PluginRuntimeView? = synchronized(lock) {
        val record = records.remove(pluginId)
        val runtime = runtimes.remove(pluginId)
        if (record != null && runtime != null) runtime.view(record) else null
    }

    fun contains(pluginId: String): Boolean = synchronized(lock) { records.containsKey(pluginId) }

    fun ids(): List<String> = synchronized(lock) { records.keys.toList() }

    fun record(pluginId: String): PluginRecord? = synchronized(lock) { records[pluginId] }

    fun runtime(pluginId: String): ManagedPlugin? = synchronized(lock) { runtimes[pluginId] }

    fun view(pluginId: String): PluginRuntimeView? = synchronized(lock) { viewLocked(pluginId) }

    fun views(): List<PluginRuntimeView> = synchronized(lock) {
        records.keys.mapNotNull(::viewLocked)
    }

    fun updateRecord(pluginId: String, transform: (PluginRecord) -> PluginRecord): PluginRecord? =
        synchronized(lock) {
            val current = records[pluginId] ?: return null
            val updated = transform(current)
            require(updated.id == pluginId) { "plugin id must not change" }
            records[pluginId] = updated
            updated
        }

    fun setEnabled(pluginId: String, enabled: Boolean): PluginRecord? =
        updateRecord(pluginId) { it.copy(enabled = enabled) }

    fun markLoaded(
        pluginId: String,
        manifest: PluginManifest,
        plugin: DeepDeepPlugin,
        host: PluginHost,
        scope: CoroutineScope,
        externalHandle: LoadedPluginHandle? = null,
        failure: String? = null,
    ): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.manifest = manifest
        runtime.plugin = plugin
        runtime.host = host
        runtime.scope = scope
        runtime.externalHandle = externalHandle
        runtime.failure = failure
        viewLocked(pluginId)
    }

    fun updateHost(pluginId: String, host: PluginHost, scope: CoroutineScope): PluginRuntimeView? =
        synchronized(lock) {
            val runtime = runtimes[pluginId] ?: return null
            runtime.host = host
            runtime.scope = scope
            viewLocked(pluginId)
        }

    fun detachHandle(pluginId: String): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.externalHandle = null
        viewLocked(pluginId)
    }

    fun detachPlugin(pluginId: String): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.plugin = null
        runtime.externalHandle = null
        runtime.contributions = PluginContributions.Empty
        runtime.skillIds = emptyList()
        runtime.materialized = false
        runtime.themeSource = null
        viewLocked(pluginId)
    }

    fun attachExternalRecord(pluginId: String, record: ExternalPluginRecord): PluginRuntimeView? =
        synchronized(lock) {
            val runtime = runtimes[pluginId] ?: return null
            runtime.externalRecord = record
            viewLocked(pluginId)
        }

    fun applyMaterialized(pluginId: String, result: MaterializedPlugin): PluginDescriptor? =
        synchronized(lock) {
            val runtime = runtimes[pluginId] ?: return null
            runtime.contributions = result.contributions
            runtime.availability = result.availability
            runtime.materializationFailure = result.failure
            runtime.themeSource = result.themeSource
            if (runtime.plugin != null) {
                runtime.materialized = result.failure == null
            }
            if (result.failure != null) {
                records[pluginId] = records.getValue(pluginId).copy(lastError = result.failure)
            }
            descriptorLocked(pluginId)
        }

    fun markSkills(pluginId: String, skillIds: List<String>): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.skillIds = skillIds
        viewLocked(pluginId)
    }

    fun markError(pluginId: String, error: String): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        records[pluginId] = records.getValue(pluginId).copy(lastError = error)
        viewLocked(pluginId)
    }

    fun markFailure(pluginId: String, error: String): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.failure = error
        records[pluginId] = records.getValue(pluginId).copy(lastError = error)
        viewLocked(pluginId)
    }

    fun clearFailure(pluginId: String): PluginRuntimeView? = synchronized(lock) {
        val runtime = runtimes[pluginId] ?: return null
        runtime.failure = null
        records[pluginId] = records.getValue(pluginId).copy(lastError = null)
        viewLocked(pluginId)
    }

    fun descriptor(pluginId: String): PluginDescriptor? = synchronized(lock) { descriptorLocked(pluginId) }

    fun descriptors(): List<PluginDescriptor> = synchronized(lock) {
        records.keys.mapNotNull { pluginId -> descriptorLocked(pluginId) }
    }

    fun publishTo(registry: PluginRegistry) {
        descriptors().forEach { descriptor ->
            if (!registry.update(descriptor.id) { descriptor }) registry.register(descriptor)
        }
    }

    private fun viewLocked(pluginId: String): PluginRuntimeView? {
        val record = records[pluginId] ?: return null
        val runtime = runtimes[pluginId] ?: return null
        return runtime.view(record)
    }

    private fun descriptorLocked(pluginId: String): PluginDescriptor? =
        viewLocked(pluginId)?.toDescriptor()
}
