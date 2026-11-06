package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry
import io.github.whywood.deepdeep.plugin.host.update.PluginRepositorySettings
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope

abstract class PluginManagerDelegation internal constructor() : PluginManagerFacade {
    internal abstract val appScope: CoroutineScope

    internal abstract val externalFlow: PluginExternalFlow

    internal abstract val sessionHooks: PluginSessionHooks

    internal abstract val slotRegistry: SlotRegistry

    internal abstract val textResolver: PluginTextResolver

    internal abstract val registrySnapshot: PluginRegistrySnapshot

    override fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool> = sessionHooks.tools(binding)

    override suspend fun inspectExternal(file: File): ExternalInspectionUi? = externalFlow.inspectExternal(file)

    override suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        externalFlow.installExternal(file, acceptPublisher)

    override suspend fun checkExternalUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override suspend fun stageExternalUpdate(pluginId: String): String? = externalFlow.stageExternalUpdate(pluginId)

    override suspend fun pluginRepositorySettings(): PluginRepositorySettings = externalFlow.pluginRepositorySettings()

    override suspend fun savePluginRepositorySettings(settings: PluginRepositorySettings) {
        externalFlow.savePluginRepositorySettings(settings)
    }

    override suspend fun refreshAvailableUpdates(): List<PluginUpdateUi> = externalFlow.checkExternalUpdates()

    override fun slotEntries(slotId: String): List<SlotRenderEntry> = slotRegistry.bindings(slotId)

    override fun pagesOf(pluginId: String): List<PluginPage> =
        if (!slotRegistry.uiCompatible(pluginId)) {
            emptyList()
        } else {
            registrySnapshot.pages.filter { it.pluginId == pluginId }.map { it.page }
        }

    override fun composerActions(): List<PluginComposerActionUi> =
        registrySnapshot.composerActions.mapNotNull { entry ->
            if (!slotRegistry.uiCompatible(entry.pluginId)) return@mapNotNull null
            val pages = registrySnapshot.pages.filter { it.pluginId == entry.pluginId }.map { it.page }
            val page = pages.firstOrNull { it.id == entry.action.id } ?: pages.firstOrNull() ?: return@mapNotNull null
            PluginComposerActionUi(entry.pluginId, entry.action, page.id)
        }

    override fun resolveText(text: LocalizedText): String = textResolver.resolve(text)

    internal fun bindSessionHooks(binding: SessionBinding, delegate: PluginHooksDelegate) =
        sessionHooks.bind(binding, delegate)

    internal fun onSessionClosed(sessionId: String) = sessionHooks.close(sessionId)

    internal fun onRuntimeEvent(event: SessionRuntimeEvent) = sessionHooks.dispatchRuntimeEvent(event)

    internal fun onSessionSdkEvent(sessionId: String, event: dev.pi.coding.session.SessionEvent) =
        sessionHooks.dispatchSessionEvent(sessionId, event)
}
