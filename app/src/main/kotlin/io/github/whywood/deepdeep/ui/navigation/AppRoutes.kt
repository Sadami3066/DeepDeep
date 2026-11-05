package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginComposerActionUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ProviderAuthKind
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.assistant.AssistantSettingsScreen
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.files.FileBrowserScreen
import io.github.whywood.deepdeep.ui.mcp.McpScreen
import io.github.whywood.deepdeep.ui.plugins.LocalPluginManager
import io.github.whywood.deepdeep.ui.plugins.PluginDetailScreen
import io.github.whywood.deepdeep.ui.plugins.PluginIcon
import io.github.whywood.deepdeep.ui.plugins.PluginPageSlot
import io.github.whywood.deepdeep.ui.plugins.PluginsScreen
import io.github.whywood.deepdeep.ui.plugins.pluginComposerActions
import io.github.whywood.deepdeep.ui.plugins.rememberPluginTextResolver
import io.github.whywood.deepdeep.ui.timers.TimerScreen
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
import io.github.whywood.deepdeep.ui.settings.ScanJsonScreen
import io.github.whywood.deepdeep.ui.settings.ScanSettingsScreen
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
private const val PLUGIN_PAGE_MARKER = "/page/"
private const val WORKSHOP_DETAIL_PREFIX = "workshop/"

fun parentRouteKey(routeKey: String): String? = when {
    routeKey.startsWith(PLUGIN_DETAIL_PREFIX) && PLUGIN_PAGE_MARKER in routeKey ->
        PLUGIN_DETAIL_PREFIX + routeKey.removePrefix(PLUGIN_DETAIL_PREFIX).substringBefore(PLUGIN_PAGE_MARKER)
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
    nickname: String, avatar: ImageBitmap?, providerName: String?, modelLabel: String?,
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
            avatar = avatar,
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
            onOpenScan = { navigator.push(AppDestination.Scan) },
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
            onLanguageChange = { dependencies.pluginManager.refresh() },
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

        AppDestination.Scan -> ScanSettingsScreen(
            onBack = onBack,
            onOpenJsonEditor = { navigator.push(AppDestination.ScanJson) },
        )

        AppDestination.ScanJson -> ScanJsonScreen(onBack = onBack)

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
            onOpenPluginPage = { pageId ->
                val title = dependencies.pluginManager.pagesOf(destination.id)
                    .firstOrNull { it.id == pageId }
                    ?.title
                    ?.let(dependencies.pluginManager::resolveText)
                    .orEmpty()
                navigator.push(AppDestination.PluginPage(destination.id, pageId, title))
            },
        )

        is AppDestination.PluginPage -> PluginPageRoute(
            destination = destination,
            manager = dependencies.pluginManager,
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

        AppDestination.Timers -> TimerScreen(
            service = dependencies.timerService,
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

@Composable
private fun PluginPageRoute(
    destination: AppDestination.PluginPage,
    manager: PluginManagerFacade,
    onBack: () -> Unit,
) {
    val colors = deepDeepColors()
    val uiState by manager.state.collectAsStateWithLifecycle()
    val title = remember(uiState, destination) {
        destination.title.ifBlank {
            manager.pagesOf(destination.pluginId)
                .firstOrNull { it.id == destination.pageId }
                ?.title
                ?.let(manager::resolveText)
                .orEmpty()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugin_page_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title.ifBlank { stringResource(R.string.plugins_page_title) },
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
            )
        }
        PluginPageSlot(
            pluginId = destination.pluginId,
            pageId = destination.pageId,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onAction = { actionId -> manager.runAction(destination.pluginId, actionId) },
            fallback = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plugins_page_unavailable),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            },
        )
    }
}

@Composable
internal fun PluginComposerActionRow(modifier: Modifier = Modifier) {
    val manager = LocalPluginManager.current ?: return
    val actions = pluginComposerActions()
    if (actions.isEmpty()) return
    val textResolver = rememberPluginTextResolver()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { entry ->
            PluginComposerActionChip(
                entry = entry,
                label = textResolver.resolve(entry.action.label),
                onClick = { manager.runAction(entry.pluginId, entry.action.id) },
            )
        }
    }
}

@Composable
private fun PluginComposerActionChip(
    entry: PluginComposerActionUi,
    label: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.height(32.dp).testTag("plugin_composer_action"),
        shape = RoundedCornerShape(10.dp),
        contentDescription = label,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIcon(
                icon = entry.action.icon,
                assetsDir = null,
                tint = colors.inkSecondary,
                size = 15.dp,
            )
            Text(text = label, style = DdType.label, color = colors.ink, maxLines = 1)
        }
    }
}
