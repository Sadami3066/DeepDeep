package io.github.whywood.deepdeep.graph

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.whywood.deepdeep.AppGraph
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.builtinPlugins
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import io.github.whywood.deepdeep.plugin.host.external.PublisherTrustStore
import io.github.whywood.deepdeep.plugin.host.services.McpModelMapping
import io.github.whywood.deepdeep.plugin.host.services.PluginServiceDependencies
import io.github.whywood.deepdeep.plugin.host.services.PluginUiBridge
import io.github.whywood.deepdeep.plugin.host.services.buildPluginServices
import io.github.whywood.deepdeep.plugin.host.update.ExternalUpdateSupport
import io.github.whywood.deepdeep.plugin.manager.BuiltinPluginStateStore
import io.github.whywood.deepdeep.plugin.manager.ExternalPluginRuntime
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.service.AppVisibility
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class PluginGraph(private val graph: AppGraph) {
    private val language: () -> String = { LanguagePreferences(graph.appContext).read().tag }

    val manager: PluginHostManager by lazy {
        val pluginsRoot = File(graph.appContext.filesDir, "plugins")
        val trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        val recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        val installer = PluginInstaller(
            pluginsRoot = pluginsRoot,
            trustStore = trustStore,
            recordStore = recordStore,
            hostVersion = BuildConfig.VERSION_NAME,
            reservedPluginIds = { builtinPlugins(graph.builtinWiring).map { it.manifest.id }.toSet() },
            reservedToolNames = { builtinPlugins(graph.builtinWiring).flatMap { it.manifest.provides.tools }.toSet() },
        )
        PluginHostManager(
            context = graph.appContext,
            strings = graph.pluginStrings,
            filesDir = graph.appContext.filesDir,
            scope = graph.scope,
            language = language,
            builtins = builtinPlugins(graph.builtinWiring),
            servicesFactory = { pluginId, dataDir, pluginScope ->
                buildPluginServices(pluginServices(pluginId, dataDir, pluginScope))
            },
            builtinStateStore = BuiltinPluginStateStore(File(pluginsRoot, "builtin_state.json")),
            settingsStore = graph.pluginSettingsStore,
            skillStore = io.github.whywood.deepdeep.plugin.PluginSkillStore(
                skillsDir = File(graph.agentDir, "skills"),
                repoDir = File(graph.appContext.filesDir, "skills"),
            ),
            external = ExternalPluginRuntime(graph.appContext, installer, BuildConfig.VERSION_NAME),
            externalUpdates = ExternalUpdateSupport(
                filesDir = graph.appContext.filesDir,
                hostVersion = BuildConfig.VERSION_NAME,
                installer = installer,
                trustStore = trustStore,
                recordStore = recordStore,
            ),
            isForeground = { AppVisibility.foreground },
            notifyInstall = { label, stage, progress ->
                graph.notificationCenter.notifyInstall(label, stage, progress)
            },
            notifyInstallFinished = { label, success, error ->
                graph.notificationCenter.notifyInstallFinished(label, success, error)
            },
            onContributionsChanged = {
                graph.scope.launch { graph.host.reconfigure() }
            },
            onSetupReady = { _ ->
                if (!AppVisibility.foreground) graph.scope.launch { AppVisibility.bringToFront(graph.appContext) }
            },
        )
    }

    private fun pluginServices(pluginId: String, dataDir: File, pluginScope: CoroutineScope): PluginServiceDependencies =
        PluginServiceDependencies(
            context = graph.uiContext,
            scope = pluginScope,
            pluginId = pluginId,
            pluginDataDir = dataDir,
            workspaceDir = graph.homeDir,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            language = language,
            installationId = { graph.installationId.get() },
            linux = graph.linuxEnvironment,
            privilege = graph.privilege,
            rootShell = graph.rootShell,
            adbBridge = graph.adbBridge,
            secrets = graph.secretStore,
            network = graph.httpTransport,
            sessions = graph.host,
            sessionCommands = graph.host,
            screen = graph.pluginScreenController,
            mainScreen = graph.pluginMainScreenController,
            activeSessionId = graph.activeSessionId,
            jobs = graph.jobRegistry,
            timers = graph.timerService,
            goals = graph.goalService,
            workshopStore = graph.workshopStore,
            workshopAgent = graph.workshopAgentService,
            workshopMaxTurns = { graph.pluginSettingsStore.cachedInt("workshop", "max_turns", 16) },
            mcpConfig = graph.mcpConfigStore,
            mcpProbe = graph.mcpServerProbe,
            mcpTools = { server ->
                graph.mcpConfigStore.loadServers()[server]?.let { config ->
                    graph.mcpPluginBridge.listTools(server, config).map(McpModelMapping::tool)
                }.orEmpty()
            },
            mcpCall = call@{ server, tool, arguments ->
                val config = graph.mcpConfigStore.loadServers()[server]
                    ?: return@call McpCallResult(
                        blocks = listOf(ToolBlock.Text("unknown MCP server: $server")),
                        isError = true,
                    )
                McpModelMapping.result(graph.mcpPluginBridge.callTool(server, config, tool, arguments))
            },
            virtualScreen = graph.virtualScreenFeature,
            screenshotDir = File(graph.appContext.filesDir, "gui/screenshots"),
            sharedStorageDir = if (isSharedStorageGranted()) File("/storage/emulated/0") else null,
            bus = graph.pluginBus,
            chatSink = { sessionId, customType, payload, text, display ->
                graph.host.postPluginMessage(sessionId, customType, payload.toString(), text, display)
            },
            openUrl = { url -> openUrl(url) },
            toast = { text -> toast(text) },
            share = { request -> PluginUiBridge.share(graph.uiContext, request) },
            openPage = { pageId -> PluginUiBridge.openPage(graph.uiContext, pluginId, pageId) },
        )

    private fun openUrl(url: String) {
        runCatching {
            graph.appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun toast(text: String) {
        graph.scope.launch(Dispatchers.Main) {
            Toast.makeText(graph.uiContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
