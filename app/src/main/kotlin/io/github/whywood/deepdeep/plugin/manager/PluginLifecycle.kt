package io.github.whywood.deepdeep.plugin.manager

import android.content.Context
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
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
) {
    fun createRuntime(
        record: PluginRecord,
        manifest: PluginManifest,
        externalRecord: ExternalPluginRecord? = null,
    ): ManagedPlugin {
        val scope = newScope()
        return ManagedPlugin(
            id = record.id,
            manifest = manifest,
            plugin = null,
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
            plugin = handle.plugin,
            host = host,
            scope = scope,
            externalHandle = handle,
        )
    }

    suspend fun enable(view: PluginRuntimeView): String? {
        val current = freshScopeView(view)
        if (!current.enabled) return null
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

    suspend fun disable(pluginId: String): String? {
        val view = gate.view(pluginId) ?: return null
        var failure: String? = null
        view.plugin?.let { plugin ->
            try {
                plugin.onDisable(view.host)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error.message ?: error.javaClass.simpleName
            }
        }
        return deactivate(pluginId) ?: failure
    }

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
        gate.detachPlugin(pluginId)
        external?.forget(pluginId)
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
        if (active && !view.materialized && view.skillIds.isNotEmpty()) return
        runCatching { skillStore.sync(view.id, skills, view.skillIds, active) }
        val skillIds = skills.map { it.id }
        gate.markSkills(view.id, skillIds)
        runCatching { external?.updateSkills(view.id, skillIds) }
    }

    suspend fun removeSkills(pluginId: String, skillIds: List<String>) {
        runCatching { skillStore.remove(pluginId, skillIds.distinct()) }
        gate.markSkills(pluginId, emptyList())
        runCatching { external?.updateSkills(pluginId, emptyList()) }
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
    private val servicesFactory: (pluginId: String, dataDir: File, manifest: PluginManifest, scope: CoroutineScope) -> PluginServices,
    private val onRefreshRequest: () -> Unit,
) {
    fun create(pluginId: String, manifest: PluginManifest, scope: CoroutineScope): PluginHost =
        PluginHostImpl(
            manifest = manifest,
            context = context,
            scope = scope,
            services = servicesFactory(pluginId, File(pluginsRoot, "$pluginId/data"), manifest, scope),
            textResolver = textResolver,
            pluginsRoot = pluginsRoot,
            onRefreshRequest = onRefreshRequest,
        )
}
