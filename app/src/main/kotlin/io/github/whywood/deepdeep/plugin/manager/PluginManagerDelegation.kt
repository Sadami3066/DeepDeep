package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry
import io.github.whywood.deepdeep.plugin.host.update.PluginSourceSettings
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class PluginManagerDelegation internal constructor() : PluginManagerFacade {
    internal abstract val appScope: CoroutineScope

    internal abstract val setupFlow: PluginSetupFlow

    internal abstract val externalFlow: PluginExternalFlow

    internal abstract val sessionHooks: PluginSessionHooks

    internal abstract val slotRegistry: SlotRegistry

    internal abstract val guard: PluginFailureGuard

    internal abstract val textResolver: PluginTextResolver

    internal abstract val registrySnapshot: PluginRegistrySnapshot

    internal abstract suspend fun prepareConnect(pluginId: String)

    override fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool> = sessionHooks.tools(binding)

    override fun install(pluginId: String) = setupFlow.install(pluginId)

    override fun update(pluginId: String) = setupFlow.install(pluginId)

    override fun connect(pluginId: String) {
        appScope.launch {
            runCatching {
                prepareConnect(pluginId)
                setupFlow.setup(pluginId)
            }
        }
    }

    override fun runAction(pluginId: String, actionId: String) = setupFlow.runAction(pluginId, actionId)

    override fun onAppForeground() = setupFlow.onAppForeground()

    override fun confirmInstall() = setupFlow.confirmInstall()

    override fun cancelInteraction() = setupFlow.cancelInteraction()

    override fun cancelSetup(pluginId: String) = setupFlow.cancelSetup(pluginId)

    override suspend fun setup(pluginId: String): PluginSetupResult = setupFlow.setup(pluginId)

    override suspend fun inspectExternal(file: File): ExternalInspectionUi? = externalFlow.inspectExternal(file)

    override suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        externalFlow.installExternal(file, acceptPublisher)

    override suspend fun checkExternalUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override suspend fun stageExternalUpdate(pluginId: String): String? = externalFlow.stageExternalUpdate(pluginId)

    override suspend fun pluginSourceSettings(): PluginSourceSettings = externalFlow.pluginSourceSettings()

    override suspend fun savePluginSourceSettings(settings: PluginSourceSettings) {
        externalFlow.savePluginSourceSettings(settings)
    }

    override suspend fun refreshAvailableUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override fun slotEntries(slotId: String): List<SlotRenderEntry> = slotRegistry.bindings(slotId)

    override fun pluginSlotEntries(pluginId: String): List<SlotRenderEntry> {
        val slotIds = registrySnapshot.bindings
            .filter { it.pluginId == pluginId }
            .map { it.binding.slotId }
            .distinct()
        return slotIds.flatMap { slotId ->
            slotRegistry.bindings(slotId).filter { it.pluginId == pluginId }
        }
    }

    override fun pagesOf(pluginId: String): List<PluginPage> =
        registrySnapshot.pages.filter { it.pluginId == pluginId }.map { it.page }

    override fun composerActions(): List<PluginComposerActionUi> =
        registrySnapshot.composerActions.map { PluginComposerActionUi(it.pluginId, it.action) }

    override fun slotFailureGuard(): PluginFailureGuard = guard

    override fun resolveText(text: LocalizedText): String = textResolver.resolve(text)

    internal fun bindSessionHooks(binding: SessionBinding, delegate: PluginHooksDelegate) =
        sessionHooks.bind(binding, delegate)

    internal fun onSessionClosed(sessionId: String) = sessionHooks.close(sessionId)

    internal fun onRuntimeEvent(event: SessionRuntimeEvent) = sessionHooks.dispatchRuntimeEvent(event)

    internal fun onSessionSdkEvent(sessionId: String, event: dev.pi.coding.session.SessionEvent) =
        sessionHooks.dispatchSessionEvent(sessionId, event)
}
