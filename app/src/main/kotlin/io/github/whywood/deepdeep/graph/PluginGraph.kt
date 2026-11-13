package io.github.whywood.deepdeep.graph

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.isSharedStorageGranted
import io.github.whywood.deepdeep.plugin.PluginSkillStore
import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.textBlock
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import io.github.whywood.deepdeep.plugin.host.external.PublisherTrustStore
import io.github.whywood.deepdeep.plugin.host.services.McpModelMapping
import io.github.whywood.deepdeep.plugin.host.services.PluginServiceDependencies
import io.github.whywood.deepdeep.plugin.host.services.PluginUiBridge
import io.github.whywood.deepdeep.plugin.host.declaredOnly
import io.github.whywood.deepdeep.plugin.host.services.buildPluginServices
import io.github.whywood.deepdeep.plugin.manager.ExternalPluginRuntime
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class PluginGraph(private val deps: PluginGraphDeps) {
    private val language: () -> String = { LanguagePreferences(deps.appContext).read().tag }

    val manager: PluginHostManager by lazy {
        val pluginsRoot = File(deps.appContext.filesDir, "plugins")
        val trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        val recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        val installer = PluginInstaller(
            pluginsRoot = pluginsRoot,
            trustStore = trustStore,
            recordStore = recordStore,
            hostVersion = BuildConfig.VERSION_NAME,
        )
        PluginHostManager(
            context = deps.appContext,
            strings = deps.pluginStrings,
            filesDir = deps.appContext.filesDir,
            scope = deps.scope,
            language = language,
            servicesFactory = { pluginId, dataDir, manifest, pluginScope ->
                buildPluginServices(
                    pluginServices(
                        pluginId = pluginId,
                        dataDir = dataDir,
                        pluginScope = pluginScope,
                        firstPageId = manifest.provides.pages.firstOrNull(),
                    ),
                )
                    .declaredOnly(manifest.capabilities)
            },
            skillStore = PluginSkillStore(
                skillsDir = File(deps.agentDir, "skills"),
                repoDir = File(deps.appContext.filesDir, "skills"),
            ),
            external = ExternalPluginRuntime(deps.appContext, installer, BuildConfig.VERSION_NAME),
            onContributionsChanged = {
                deps.scope.launch { deps.host.reconfigure() }
            },
            busySessions = { deps.host.busySessionIds.value },
        )
    }

    init {
        deps.scope.launch {
            var wasBusy = false
            deps.host.busySessionIds.collect { busy ->
                if (busy.isNotEmpty()) {
                    wasBusy = true
                } else if (wasBusy) {
                    wasBusy = false
                    manager.applyPendingUpdates()
                }
            }
        }
    }

    private fun pluginServices(
        pluginId: String,
        dataDir: File,
        pluginScope: CoroutineScope,
        firstPageId: String?,
    ): PluginServiceDependencies =
        PluginServiceDependencies(
            context = deps.uiContext,
            scope = pluginScope,
            pluginId = pluginId,
            pluginFirstPageId = firstPageId,
            pluginDataDir = dataDir,
            workspaceDir = deps.homeDir,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            language = language,
            installationId = { deps.installationId.get() },
            hostEnvironment = deps.hostEnvironment,
            defaultWorkspace = { deps.homeDir.absolutePath },
            bashAvailable = { deps.hostEnvironment.current().capabilities.bashAvailable },
            files = { deps.hostEnvironment.current().files },
            secrets = deps.secretStore,
            network = deps.httpTransport,
            sessions = deps.host,
            sessionCommands = deps.host,
            screen = deps.pluginScreenController(),
            mainScreen = deps.pluginMainScreenController(),
            activeSessionId = deps.activeSessionId,
            jobs = deps.jobRegistry(),
            timers = deps.timerService(),
            goals = deps.goalService(),
            miniappStore = deps.miniappStore,
            miniappAgent = deps.miniappAgentService(),
            miniappMaxTurns = { 16 },
            mcpConfig = deps.mcpConfigStore,
            mcpProbe = deps.mcpServerProbe,
            mcpTools = { server ->
                deps.mcpConfigStore.loadServers()[server]?.let { config ->
                    deps.mcpPluginBridge().listTools(server, config).map(McpModelMapping::tool)
                }.orEmpty()
            },
            mcpCall = call@{ server, tool, arguments ->
                val config = deps.mcpConfigStore.loadServers()[server]
                    ?: return@call McpCallResult(
                        blocks = listOf(textBlock("unknown MCP server: $server")),
                        isError = true,
                    )
                McpModelMapping.result(deps.mcpPluginBridge().callTool(server, config, tool, arguments))
            },
            virtualScreen = deps.virtualScreenFeature(),
            screenshotDir = File(deps.appContext.filesDir, "gui/screenshots"),
            sharedStorageDir = if (isSharedStorageGranted()) File("/storage/emulated/0") else null,
            bus = deps.pluginBus,
            chatSink = { sessionId, customType, payload, text, display ->
                deps.host.postPluginMessage(sessionId, customType, payload.toString(), text, display)
            },
            openUrl = ::openUrl,
            toast = ::toast,
            share = { request -> PluginUiBridge.share(deps.uiContext, request) },
            openPage = { pageId -> PluginUiBridge.openPage(deps.uiContext, pluginId, pageId) },
        )

    private fun openUrl(url: String) {
        runCatching {
            deps.appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun toast(text: String) {
        deps.scope.launch(Dispatchers.Main) {
            Toast.makeText(deps.uiContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
