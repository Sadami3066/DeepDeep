package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.*
import io.github.whywood.deepdeep.design.*
import io.github.whywood.deepdeep.runtime.*
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.ui.chat.*
import io.github.whywood.deepdeep.ui.mcp.McpScreen
import io.github.whywood.deepdeep.ui.plugins.*
import io.github.whywood.deepdeep.ui.schedules.ScheduleScreen
import io.github.whywood.deepdeep.ui.sessions.*
import io.github.whywood.deepdeep.ui.settings.*
import io.github.whywood.deepdeep.ui.skills.SkillsScreen
import io.github.whywood.deepdeep.ui.workshop.*
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceSettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class RouteSpec(
    val routeKey: String,
    val depth: Int,
    val parentRouteKey: String?,
)

private const val PLUGIN_DETAIL_PREFIX = "plugins/"
private const val WORKSHOP_DETAIL_PREFIX = "workshop/"

fun parentRouteKey(routeKey: String): String? = when {
    routeKey.startsWith(PLUGIN_DETAIL_PREFIX) -> "plugins"
    routeKey.startsWith(WORKSHOP_DETAIL_PREFIX) -> "workshop"
    else -> null
}

fun routeSpec(destination: AppDestination): RouteSpec =
    RouteSpec(destination.routeKey, destination.depth, parentRouteKey(destination.routeKey))

fun routeSpec(routeKey: String): RouteSpec? = AppDestination.fromRouteKey(routeKey)?.let(::routeSpec)

fun deepLinkStack(destination: AppDestination): List<AppDestination> {
    val parent = parentRouteKey(destination.routeKey)?.let(AppDestination::fromRouteKey)
    return if (parent == null) listOf(destination) else listOf(parent, destination)
}

fun deepLinkStack(routeKey: String): List<AppDestination> =
    AppDestination.fromRouteKey(routeKey)?.let(::deepLinkStack).orEmpty()

@Composable
internal fun AppRouteContent(
    destination: AppDestination, navigator: AppNavigator,
    onBack: () -> Unit, onOpenChat: () -> Unit, onOpenWorkshopCreate: () -> Unit,
    state: ChatUiState, chatViewModel: ChatViewModel,
    dependencies: AppDependencies, scope: CoroutineScope,
    theme: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    fontStyle: DdFontStyle, onFontStyleChange: (DdFontStyle) -> Unit,
    nickname: String, providerName: String?, modelLabel: String?,
    modelConfig: ModelConfig, configuredProviderIds: Set<String>,
    linuxState: LinuxInstallState, linuxEnabled: Boolean, sharedStorageEnabled: Boolean,
    devPackages: DevPackagesState, personalization: PersonalizationUi, uiPreferences: UiPreferences,
    voiceAutoSpeak: Boolean, voiceSendAfterRecognition: Boolean,
    authKinds: Map<String, ProviderAuthKind>, credentialRevision: Long,
) {
    when (destination) {
        AppDestination.Chat -> Box(Modifier.fillMaxSize())

        AppDestination.Settings -> SettingsScreen(
            nickname = nickname,
            providerName = providerName,
            modelName = modelLabel,
            configured = modelConfig.providerId in configuredProviderIds,
            themeMode = theme,
            linuxState = linuxState,
            versionName = BuildConfig.VERSION_NAME,
            fontLabel = fontStyle.displayName,
            onBack = onBack,
            onThemeModeChange = onThemeChange,
            onOpenChatSettings = { navigator.push(AppDestination.ChatSettings) },
            onOpenProviders = { navigator.push(AppDestination.Providers) },
            onOpenRuntime = { navigator.push(AppDestination.RuntimeSettings) },
            onOpenPersonalization = { navigator.push(AppDestination.Personalization) },
            onOpenFonts = { navigator.push(AppDestination.Fonts) },
            onOpenPrivacy = { navigator.push(AppDestination.Privacy) },
            archivedCount = state.archivedCount,
            onOpenArchived = { navigator.push(AppDestination.Archived) },
        )

        AppDestination.Providers -> ProviderListScreen(
            providers = dependencies.providerCatalog.summaries,
            configuredProviderIds = configuredProviderIds,
            onBack = onBack,
            onProviderClick = { id -> navigator.push(AppDestination.ProviderDetail(id)) },
        )

        AppDestination.RuntimeSettings -> {
            LaunchedEffect(Unit) { dependencies.linuxEnvironment.refreshState() }
            LaunchedEffect(linuxState) { dependencies.devPackageManager.refresh() }
            RuntimeSettingsScreen(
                linuxState = linuxState,
                linuxSupported = dependencies.linuxEnvironment.isSupported,
                linuxEnabled = linuxEnabled,
                sharedStorageEnabled = sharedStorageEnabled,
                devPackages = devPackages,
                onBack = onBack,
                onInstall = { scope.launch { dependencies.linuxEnvironment.install() } },
                onReset = {
                    scope.launch {
                        dependencies.linuxSettings.setEnabled(false)
                        dependencies.linuxEnvironment.reset()
                        dependencies.host.reconfigure()
                    }
                },
                onEnabledChange = { enabled ->
                    scope.launch {
                        dependencies.linuxSettings.setEnabled(enabled)
                        dependencies.host.reconfigure()
                    }
                },
                onSharedStorageChange = { enabled ->
                    scope.launch { dependencies.linuxSettings.setSharedStorageEnabled(enabled) }
                },
                onInstallBundle = dependencies.devPackageManager::installBundle,
                onRefreshPackages = dependencies.devPackageManager::refresh,
            )
        }

        AppDestination.Personalization -> PersonalizationScreen(
            value = personalization,
            onBack = onBack,
            onSave = { value -> scope.launch { dependencies.personalizationStore.save(value) } },
        )

        AppDestination.Privacy -> PrivacyScreen(onBack = onBack)

        AppDestination.Fonts -> FontScreen(
            current = fontStyle,
            onBack = onBack,
            onSelect = onFontStyleChange,
        )

        AppDestination.ChatSettings -> ChatSettingsScreen(
            preferences = uiPreferences,
            onBack = onBack,
            onToggle = { toggle, enabled ->
                scope.launch { dependencies.appPreferences.set(toggle, enabled) }
            },
            voiceAutoSpeak = voiceAutoSpeak,
            onVoiceAutoSpeakChange = { enabled ->
                scope.launch { dependencies.voicePreferences.setAutoSpeak(enabled) }
            },
            voiceSendAfterRecognition = voiceSendAfterRecognition,
            onVoiceSendAfterRecognitionChange = { enabled ->
                scope.launch { dependencies.voicePreferences.setSendAfterRecognition(enabled) }
            },
        )

        is AppDestination.ProviderDetail -> {
            val provider = dependencies.providerCatalog.summary(destination.id)
            if (provider == null) {
                LaunchedEffect(destination.id) {
                    navigator.pop()
                }
            } else {
                val authKind = authKinds[provider.id]
                val savedApiKey = remember(provider.id, credentialRevision) {
                    dependencies.credentials.cachedApiKey(provider.id)
                }
                ProviderDetailScreen(
                    provider = provider,
                    configured = provider.id in configuredProviderIds,
                    savedApiKey = savedApiKey,
                    authKind = authKind,
                    oauthService = dependencies.providerOAuthService,
                    onAuthChanged = {},
                    onBack = onBack,
                    onSaveApiKey = { key ->
                        runCatching {
                            dependencies.credentials.writeApiKey(provider.id, key, null)
                            dependencies.credentials.awaitCommit(provider.id)
                        }
                    },
                    onClearApiKey = {
                        runCatching {
                            dependencies.credentials.delete(provider.id)
                            dependencies.credentials.awaitCommit(provider.id)
                        }
                    },
                )
            }
        }

        AppDestination.Mcp -> McpScreen(
            store = dependencies.mcpConfigStore,
            probe = dependencies.mcpServerProbe,
            oauth = dependencies.mcpOAuthService,
            onBack = onBack,
            onReloadSessions = { dependencies.host.reloadMcp() },
            onReconnect = { server -> dependencies.host.reconnectMcp(server) },
        )

        AppDestination.Plugins -> PluginsScreen(
            manager = dependencies.pluginManager,
            onBack = onBack,
            onOpenPlugin = { pluginId -> navigator.push(AppDestination.PluginDetail(pluginId)) },
        )

        is AppDestination.PluginDetail -> PluginDetailScreen(
            manager = dependencies.pluginManager,
            pluginId = destination.id,
            onBack = onBack,
        )

        AppDestination.Archived -> ArchivedSessionsScreen(
            archivedSessions = state.archivedConversations,
            onBack = onBack,
            onOpen = { id ->
                chatViewModel.openConversation(id)
                onOpenChat()
            },
            onRename = chatViewModel::renameSession,
            onUnarchive = { id -> chatViewModel.setSessionArchived(id, false) },
            onDelete = chatViewModel::deleteSession,
        )

        AppDestination.Search -> SessionSearchScreen(
            onBack = onBack,
            loadSessions = chatViewModel::loadSearchSessions,
            onOpen = { id ->
                chatViewModel.openConversation(id)
                navigator.reset(AppDestination.Chat)
            },
        )

        AppDestination.Skills -> SkillsScreen(
            repository = dependencies.skillRepository,
            onBack = onBack,
        )

        AppDestination.Schedules -> ScheduleScreen(
            service = dependencies.scheduleService,
            runtime = dependencies.host,
            initialConversationId = state.conversationId,
            onBack = onBack,
            onOpenConversation = { sessionId ->
                chatViewModel.openConversationById(sessionId)
                navigator.reset(AppDestination.Chat)
            },
        )

        is AppDestination.WorkspaceSettings -> {
            val workspace = state.workspaces.firstOrNull { it.id == destination.workspaceId }
            if (workspace == null) {
                LaunchedEffect(destination.workspaceId) { navigator.pop() }
            } else {
                WorkspaceSettingsScreen(
                    workspace = workspace,
                    skillRepository = dependencies.skillRepository,
                    agentDocStore = dependencies.agentDocStore,
                    onBack = onBack,
                    onOpenSkills = { navigator.push(AppDestination.Skills) },
                )
            }
        }

        AppDestination.Workshop -> WorkshopScreen(
            store = dependencies.workshopStore,
            onBack = onBack,
            onOpen = { id -> navigator.push(AppDestination.WorkshopDetail(id)) },
            onCreate = onOpenWorkshopCreate,
        )

        is AppDestination.WorkshopDetail -> WorkshopViewerScreen(
            store = dependencies.workshopStore,
            creationId = destination.id,
            agentGateway = dependencies.workshopAgentService,
            onBack = onBack,
        )
    }
}
