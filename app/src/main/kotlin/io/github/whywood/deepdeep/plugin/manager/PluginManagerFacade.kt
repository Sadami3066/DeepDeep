package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.runtime.PluginController
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface PluginManagerFacade : PluginController {
    val state: StateFlow<PluginsUiState>

    override val setupPluginIds: List<String>

    suspend fun start()

    suspend fun refreshNow()

    fun refresh()

    fun refreshIfStale(minIntervalMillis: Long = 15_000L)

    fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool>

    fun promptBody(): String?

    fun setEnabled(pluginId: String, enabled: Boolean)

    fun setSetting(pluginId: String, key: String, value: String)

    fun install(pluginId: String)

    fun update(pluginId: String)

    fun uninstall(pluginId: String)

    fun connect(pluginId: String)

    fun runAction(pluginId: String, actionId: String)

    fun onAppForeground()

    fun confirmInstall()

    fun cancelInteraction()

    suspend fun inspectExternal(file: File): ExternalInspectionUi?

    suspend fun installExternal(file: File, acceptPublisher: Boolean): ExternalInstallUi

    suspend fun checkExternalUpdates(): List<PluginUpdateUi>
}
