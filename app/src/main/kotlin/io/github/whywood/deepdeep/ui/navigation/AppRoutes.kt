package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ProviderAuthKind
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.assistant.AssistantSettingsScreen
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.files.FileBrowserScreen
import io.github.whywood.deepdeep.ui.mcp.McpScreen
import io.github.whywood.deepdeep.ui.plugins.PluginDetailScreen
import io.github.whywood.deepdeep.ui.plugins.PluginsScreen
import io.github.whywood.deepdeep.ui.schedules.ScheduleScreen
import io.github.whywood.deepdeep.ui.sessions.ArchivedSessionsScreen
import io.github.whywood.deepdeep.ui.sessions.SessionSearchScreen
import io.github.whywood.deepdeep.ui.settings.ChatSettingsScreen
import io.github.whywood.deepdeep.ui.settings.FloatingWindowScreen
import io.github.whywood.deepdeep.ui.settings.FontScreen
import io.github.whywood.deepdeep.ui.settings.PersonalizationScreen
import io.github.whywood.deepdeep.ui.settings.PermissionScreen
import io.github.whywood.deepdeep.ui.settings.PrivacyScreen
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen
import io.github.whywood.deepdeep.ui.settings.RuntimeSettingsScreen
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import io.github.whywood.deepdeep.ui.settings.UsageHabitsScreen
import io.github.whywood.deepdeep.ui.skills.SkillsScreen
import io.github.whywood.deepdeep.ui.workshop.WorkshopScreen
import io.github.whywood.deepdeep.ui.workshop.WorkshopViewerScreen
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceSettingsScreen
import java.io.File
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
    voiceAutoSpeak: Boolean,
    authKinds: Map<String, ProviderAuthKind>, credentialRevision: Long,
    onOpenFile: (String) -> Unit = {},
    onOpenAssistantSettings: () -> Unit = {},
) {
    val privilegeState by dependencies.privilege.state.collectAsStateWithLifecycle()
    val overlayEntryMode by dependencies.overlayEntryPreferences.mode.collectAsStateWithLifecycle(
        initialValue = OverlayEntryMode.VOICE,
    )
    when (destination) {
        AppDestination.Chat -> Box(Modifier.fillMaxSize())

        is AppDestination.Files -> FileBrowserScreen(
            root = File(destination.rootPath),
            rootLabel = state.workspaces.firstOrNull { it.cwd == destination.rootPath }?.name
                ?: File(destination.rootPath).name.ifBlank { destination.rootPath },
            onOpenFile = onOpenFile,
            onBack = onBack,
        )

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
            overlayEntryMode = overlayEntryMode,
            onOpenUsageHabits = { navigator.push(AppDestination.UsageHabits) },
            onOpenAssistant = onOpenAssistantSettings,
            onOpenProviders = { navigator.push(AppDestination.Providers) },
            onOpenRuntime = { navigator.push(AppDestination.RuntimeSettings) },
            onOpenPermission = { navigator.push(AppDestination.Permission) },
            onOpenFloating = { navigator.push(AppDestination.FloatingWindow) },
            privilegeLevel = privilegeState.effective,
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

        AppDestination.Permission -> {
            val context = LocalContext.current
            PermissionScreen(
                state = privilegeState,
                onSelect = { level -> scope.launch { dependencies.privilege.select(level) } },
                onRefresh = { scope.launch { dependencies.privilege.refresh() } },
                onPair = { dependencies.privilege.startPairing() },
                onPairingCode = { code -> AgentService.applyPairingCode(context, code) },
                onConnect = { scope.launch { dependencies.privilege.connect() } },
                onOpenWirelessDebugging = { dependencies.privilege.openWirelessDebugging(context) },
                onProbeRoot = { scope.launch { dependencies.privilege.probeRoot() } },
                onBack = onBack,
            )
        }

        AppDestination.FloatingWindow -> FloatingWindowScreen(onBack = onBack)

        AppDestination.Personalization -> PersonalizationScreen(
            value = personalization,
            onBack = onBack,
            onSave = { value -> scope.launch { dependencies.personalizationStore.save(value) } },
        )

        AppDestination.Privacy -> PrivacyScreen(onBack = onBack)

        AppDestination.AssistantSettings -> AssistantSettingsScreen(
            onBack = onBack,
        )

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
            onChatFontScaleChange = { value ->
                scope.launch { dependencies.appPreferences.setChatFontScale(value) }
            },
        )

        AppDestination.UsageHabits -> UsageHabitsScreen(
            mode = overlayEntryMode,
            onBack = onBack,
            onModeChange = { mode ->
                scope.launch { dependencies.overlayEntryPreferences.setMode(mode) }
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
            onOpenPermission = { navigator.push(AppDestination.Permission) },
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
