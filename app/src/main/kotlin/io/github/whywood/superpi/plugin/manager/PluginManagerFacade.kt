package io.github.whywood.superpi.plugin.manager

import io.github.whywood.superpi.plugin.api.ComposerAction
import io.github.whywood.superpi.plugin.api.PluginExperimentalApi
import io.github.whywood.superpi.plugin.api.LocalizedText
import io.github.whywood.superpi.plugin.api.PluginBackgroundKind
import io.github.whywood.superpi.plugin.api.PluginNotificationRecord
import io.github.whywood.superpi.plugin.api.PluginPage
import io.github.whywood.superpi.plugin.api.ToolResultSource
import io.github.whywood.superpi.plugin.host.ui.SlotRenderEntry
import io.github.whywood.superpi.plugin.host.ui.ToolRendererEntry
import io.github.whywood.superpi.runtime.PluginPromptOverrides
import io.github.whywood.superpi.runtime.SessionBinding
import io.github.whywood.superpi.theme.PluginThemeSource
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

data class PluginComposerActionUi(
    val pluginId: String,
    val action: ComposerAction,
    val pageId: String,
)

interface PluginManagerFacade {
    val state: StateFlow<PluginsUiState>

    suspend fun start()

    suspend fun refreshNow()

    fun refresh()

    fun refreshAndSyncSkills()

    fun refreshIfStale(minIntervalMillis: Long = 120_000L)

    fun tools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<io.github.whywood.superpi.sdk.coding.tools.CodingTool>

    fun deferredTools(binding: SessionBinding, excludedPlugins: Set<String> = emptySet()): List<io.github.whywood.superpi.runtime.DeferredTool>

    fun promptBody(excludedPlugins: Set<String> = emptySet()): String?

    @OptIn(PluginExperimentalApi::class)
    fun promptOverrides(excludedPlugins: Set<String> = emptySet()): PluginPromptOverrides

    fun skillIds(pluginId: String): List<String>

    fun setEnabled(pluginId: String, enabled: Boolean)

    fun uninstall(pluginId: String)

    suspend fun inspectExternal(file: File): ExternalInspectionUi?

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi

    suspend fun applyPendingUpdate(pluginId: String): Boolean

    suspend fun retryActivation(pluginId: String): PluginRecoveryResult

    suspend fun rollbackPlugin(pluginId: String): Boolean

    fun slotEntries(slotId: String): List<SlotRenderEntry>

    fun toolRenderer(source: ToolResultSource, server: String?, tool: String): ToolRendererEntry?

    fun isPluginTool(toolName: String): Boolean

    fun pagesOf(pluginId: String): List<PluginPage>

    fun composerActions(): List<PluginComposerActionUi>

    fun installedThemes(): List<PluginThemeSource>

    fun resolveText(text: LocalizedText): String

    fun onNotificationReceived(record: PluginNotificationRecord, removed: Boolean)

    fun onNotificationAction(pluginId: String, notificationId: String, actionId: String, payload: JsonObject)

    fun onBackgroundTask(pluginId: String, taskId: String, kind: PluginBackgroundKind)

    fun onShareReceived(text: String?, title: String?, streams: List<PluginSharedStream>)

    fun onDeepLinkReceived(pluginId: String, path: String, arguments: JsonObject)

    fun onAppLifecycle(foreground: Boolean)
}
