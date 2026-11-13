package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.api.PluginNotificationRecord
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.events.PluginHooksDelegate
import io.github.whywood.deepdeep.plugin.host.ui.SlotRegistry
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry
import io.github.whywood.deepdeep.runtime.SessionBinding
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

abstract class PluginManagerDelegation internal constructor() : PluginManagerFacade {
    internal abstract val appScope: CoroutineScope

    internal abstract val externalFlow: PluginExternalFlow

    internal abstract val sessionHooks: PluginSessionHooks

    internal abstract val slotRegistry: SlotRegistry

    internal abstract val textResolver: PluginTextResolver

    internal abstract val registrySnapshot: PluginRegistrySnapshot

    override fun tools(binding: SessionBinding, excludedPlugins: Set<String>): List<io.github.whywood.deepdeep.sdk.coding.tools.CodingTool> =
        sessionHooks.tools(binding, excludedPlugins)

    override fun deferredTools(binding: SessionBinding, excludedPlugins: Set<String>): List<io.github.whywood.deepdeep.runtime.DeferredTool> =
        sessionHooks.deferredTools(binding, excludedPlugins)

    override suspend fun inspectExternal(file: File): ExternalInspectionUi? = externalFlow.inspectExternal(file)

    override suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi =
        externalFlow.installExternal(file, acceptPublisher)

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

    private val systemEvents: PluginSystemEvents by lazy {
        PluginSystemEvents(appScope) { registrySnapshot }
    }

    override fun onNotificationReceived(record: PluginNotificationRecord, removed: Boolean) =
        systemEvents.notificationReceived(record, removed)

    override fun onNotificationAction(pluginId: String, notificationId: String, actionId: String, payload: JsonObject) =
        systemEvents.notificationAction(pluginId, notificationId, actionId, payload)

    override fun onBackgroundTask(pluginId: String, taskId: String, kind: PluginBackgroundKind) =
        systemEvents.backgroundTask(pluginId, taskId, kind)

    override fun onShareReceived(text: String?, title: String?, streams: List<PluginSharedStream>) =
        systemEvents.shareReceived(text, title, streams)

    override fun onDeepLinkReceived(pluginId: String, path: String, arguments: JsonObject) =
        systemEvents.deepLinkReceived(pluginId, path, arguments)

    override fun onAppLifecycle(foreground: Boolean) = systemEvents.appLifecycle(foreground)

    internal fun bindSessionHooks(
        binding: SessionBinding,
        delegate: PluginHooksDelegate,
        excludedPlugins: Set<String> = emptySet(),
    ) = sessionHooks.bind(binding, delegate, excludedPlugins)

    internal fun onSessionClosed(sessionId: String) = sessionHooks.close(sessionId)

    internal fun onRuntimeEvent(event: SessionRuntimeEvent) = sessionHooks.dispatchRuntimeEvent(event)

    internal fun onSessionSdkEvent(sessionId: String, event: io.github.whywood.deepdeep.sdk.coding.session.SessionEvent) =
        sessionHooks.dispatchSessionEvent(sessionId, event)
}
