package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.host.PluginHostImpl
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.PluginServices
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

internal class PluginLifecycle(
    private val parentScope: CoroutineScope,
    private val hostFactory: (pluginId: String, manifest: PluginManifest, scope: CoroutineScope) -> PluginHost,
    private val gate: PluginRuntimeRegistry,
    private val skillStore: PluginSkillStore,
    private val external: ExternalPluginRuntime?,
    private val settingsFor: (PluginRuntimeView) -> Map<String, String>,
) {
    private val builtinPlugins = HashMap<String, DeepDeepPlugin>()

    fun registerBuiltin(entry: BuiltinPluginEntry, persisted: BuiltinPluginState?) {
        val pluginId = entry.manifest.id
        builtinPlugins[pluginId] = entry.plugin
        if (gate.contains(pluginId)) return
        val record = PluginRecord(
            id = pluginId,
            enabled = persisted?.enabled ?: entry.manifest.defaultEnabled,
            installedVersion = PluginRules.installedVersion(
                entry.manifest,
                persisted?.installedVersion,
                persisted?.installed,
            ),
        )
        gate.register(
            record,
            createRuntime(record, entry.manifest, builtin = true, source = entry.manifest.source),
        )
    }

    fun createRuntime(
        record: PluginRecord,
        manifest: PluginManifest,
        builtin: Boolean,
        source: PluginSource,
        externalRecord: ExternalPluginRecord? = null,
    ): ManagedPlugin {
        val scope = newScope()
        return ManagedPlugin(
            id = record.id,
            builtin = builtin,
            manifest = manifest,
            source = source,
            plugin = builtinPlugins[record.id],
            host = createHost(record.id, manifest, scope),
            scope = scope,
            externalRecord = externalRecord,
        )
    }

    fun newScope(): CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    fun createHost(pluginId: String, manifest: PluginManifest, scope: CoroutineScope): PluginHost =
        hostFactory(pluginId, manifest, scope)

    fun attachHandle(view: PluginRuntimeView, handle: LoadedPluginHandle): PluginRuntimeView? {
        val scope = newScope()
        val host = createHost(view.id, handle.manifest, scope)
        return gate.markLoaded(
            pluginId = view.id,
            manifest = handle.manifest,
            source = PluginSource.EXTERNAL,
            plugin = handle.plugin,
            host = host,
            scope = scope,
            externalHandle = handle,
        )
    }

    suspend fun applySettings(view: PluginRuntimeView): String? {
        val current = freshScopeView(view)
        val plugin = current.plugin ?: return null
        return try {
            plugin.onSettingsChanged(current.host, settingsFor(current))
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            gate.markFailure(current.id, message)
            message
        }
    }

    suspend fun enable(view: PluginRuntimeView): String? {
        val current = freshScopeView(view)
        if (!current.enabled) return null
        if (current.record.installedVersion == null && current.manifest.installable) return null
        val plugin = current.plugin ?: return current.record.lastError ?: "plugin is not loaded"
        return try {
            plugin.onEnable(current.host)
            gate.clearFailure(current.id)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            gate.markFailure(current.id, message)
            message
        }
    }

    suspend fun activate(view: PluginRuntimeView): String? =
        applySettings(view) ?: enable(view)

    private fun freshScopeView(view: PluginRuntimeView): PluginRuntimeView {
        if (view.scope.coroutineContext[Job]?.isActive == true) return view
        val scope = newScope()
        val host = createHost(view.id, view.manifest, scope)
        return gate.updateHost(view.id, host, scope) ?: view
    }

    suspend fun deactivate(pluginId: String): String? {
        val view = gate.view(pluginId) ?: return null
        view.scope.cancel()
        val error = close(view)
        if (view.builtin) {
            gate.detachHandle(pluginId)
        } else {
            gate.detachPlugin(pluginId)
            external?.forget(pluginId)
        }
        return error
    }

    suspend fun uninstallPlugin(view: PluginRuntimeView): String? {
        var failure: String? = null
        val plugin = view.plugin
        if (plugin != null) {
            try {
                plugin.onDisable(view.host)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failure = error.message ?: error.javaClass.simpleName
            }
            try {
                plugin.uninstall(view.host)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failure = failure ?: error.message ?: error.javaClass.simpleName
            }
        }
        val closeError = deactivate(view.id)
        return closeError ?: failure
    }

    suspend fun syncSkills(pluginId: String) {
        val view = gate.view(pluginId) ?: return
        val active = view.enabled && view.availability is PluginAvailabilityUi.Available
        val skills = view.contributions.skills
        runCatching { skillStore.sync(view.id, skills, view.skillIds, active) }
        val skillIds = skills.map { it.id }
        gate.markSkills(view.id, skillIds)
        if (view.source == PluginSource.EXTERNAL) {
            runCatching { external?.updateSkills(view.id, skillIds) }
        }
    }

    suspend fun removeSkills(pluginId: String, source: PluginSource, skillIds: List<String>) {
        runCatching { skillStore.remove(pluginId, skillIds.distinct()) }
        gate.markSkills(pluginId, emptyList())
        if (source == PluginSource.EXTERNAL) {
            runCatching { external?.updateSkills(pluginId, emptyList()) }
        }
    }

    fun release(pluginId: String) {
        builtinPlugins.remove(pluginId)
    }

    private suspend fun close(view: PluginRuntimeView): String? {
        val handle = view.externalHandle
        val plugin = view.plugin
        if (handle == null && plugin == null) return null
        return try {
            val completed = withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                if (handle != null) handle.close() else plugin?.close()
            }
            if (completed == null) "plugin close timed out" else null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error.message ?: error.javaClass.simpleName
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 5_000L
    }
}

internal class PluginHostFactory(
    private val context: Context,
    private val pluginsRoot: File,
    private val textResolver: PluginTextResolver,
    private val servicesFactory: (pluginId: String, dataDir: File, scope: CoroutineScope) -> PluginServices,
) {
    fun create(pluginId: String, manifest: PluginManifest, scope: CoroutineScope): PluginHost =
        PluginHostImpl(
            manifest = manifest,
            context = context,
            scope = scope,
            services = servicesFactory(pluginId, File(pluginsRoot, "$pluginId/data"), scope),
            textResolver = textResolver,
            pluginsRoot = pluginsRoot,
        )
}
