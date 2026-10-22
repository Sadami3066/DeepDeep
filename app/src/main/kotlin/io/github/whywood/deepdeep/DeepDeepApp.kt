package io.github.whywood.deepdeep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.DeepDeepDrawer
import io.github.whywood.deepdeep.ui.chat.ChatScreen
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.navigation.AnimatedScreenHost
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.rememberAppNavigator
import io.github.whywood.deepdeep.ui.sessions.ArchivedSessionsScreen
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen
import io.github.whywood.deepdeep.ui.settings.RuntimeSettingsScreen
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
internal fun DeepDeepApp(
    graph: AppGraph,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    runtime = graph.host,
                    providers = graph.providerCatalog,
                    modelConfigStore = graph.modelConfigStore,
                    prepareRuntime = graph::prepare,
                )
            }
        },
    )
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    val configuredProviders by graph.apiKeyStore.providerIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by graph.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val colors = deepDeepColors()
    val navigator = rememberAppNavigator()
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { 304.dp.toPx() }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val drawerProgress by animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "drawer-progress",
    )
    val activeProvider = graph.providerCatalog.summary(state.providerId)

    fun closeDrawer() {
        drawerOpen = false
    }

    fun openChat() {
        drawerOpen = false
        navigator.reset(AppDestination.Chat)
    }

    fun openSettings() {
        drawerOpen = false
        navigator.push(AppDestination.Settings)
    }

    fun back() {
        if (drawerOpen) {
            drawerOpen = false
        } else {
            navigator.pop()
        }
    }

    BackHandler(enabled = drawerOpen || navigator.canPop) {
        back()
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        AnimatedScreenHost(
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
        ) { destination ->
            when (destination) {
                AppDestination.Chat -> ChatScreen(
                    state = state.copy(configured = state.providerId in configuredProviders),
                    onInputChange = chatViewModel::onInputChange,
                    onSend = chatViewModel::send,
                    onStop = chatViewModel::stop,
                    onNewChat = chatViewModel::newChat,
                    onOpenDrawer = { drawerOpen = true },
                    onOpenSettings = ::openSettings,
                    onModelChange = chatViewModel::setModel,
                    onThinkingLevelChange = chatViewModel::setThinkingLevel,
                    onToggleStep = chatViewModel::toggleStep,
                )

                AppDestination.Settings -> SettingsScreen(
                    userName = "Milous",
                    providerName = activeProvider?.name,
                    modelName = state.modelLabel.takeIf { it.isNotBlank() },
                    configured = state.providerId in configuredProviders,
                    themeMode = theme,
                    linuxState = linuxState,
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = ::back,
                    onThemeModeChange = onThemeChange,
                    onOpenProviders = { navigator.push(AppDestination.Providers) },
                    onOpenRuntime = { navigator.push(AppDestination.RuntimeSettings) },
                )

                AppDestination.Providers -> ProviderListScreen(
                    providers = graph.providerCatalog.summaries,
                    configuredProviderIds = configuredProviders,
                    activeProviderId = state.providerId,
                    onBack = ::back,
                    onProviderClick = { id -> navigator.push(AppDestination.ProviderDetail(id)) },
                )

                AppDestination.RuntimeSettings -> RuntimeSettingsScreen(
                    linuxState = linuxState,
                    linuxSupported = graph.linuxEnvironment.isSupported,
                    linuxEnabled = linuxEnabled,
                    onBack = ::back,
                    onInstall = { scope.launch { graph.linuxEnvironment.install() } },
                    onReset = {
                        scope.launch {
                            graph.linuxSettings.setEnabled(false)
                            graph.linuxEnvironment.reset()
                            graph.host.reconfigure()
                        }
                    },
                    onEnabledChange = { enabled ->
                        scope.launch {
                            graph.linuxSettings.setEnabled(enabled)
                            graph.host.reconfigure()
                        }
                    },
                )

                is AppDestination.ProviderDetail -> {
                    val provider = graph.providerCatalog.summary(destination.id)
                    if (provider == null) {
                        LaunchedEffect(destination.id) {
                            navigator.pop()
                        }
                    } else {
                        ProviderDetailScreen(
                            provider = provider,
                            models = graph.providerCatalog.models(provider.id),
                            configured = provider.id in configuredProviders,
                            active = provider.id == state.providerId,
                            activeModelId = state.modelId.takeIf { provider.id == state.providerId },
                            onBack = ::back,
                            onSaveApiKey = { key ->
                                scope.launch {
                                    graph.apiKeyStore.save(provider.id, key)
                                    graph.credentials.preload(provider.id)
                                }
                            },
                            onClearApiKey = {
                                scope.launch {
                                    graph.apiKeyStore.save(provider.id, null)
                                    graph.credentials.invalidate(provider.id)
                                }
                            },
                            onActivate = { modelId ->
                                scope.launch {
                                    graph.host.setModel(provider.id, modelId, state.thinkingLevel)
                                    openChat()
                                }
                            },
                        )
                    }
                }

                AppDestination.Archived -> ArchivedSessionsScreen(
                    archivedSessions = state.archivedConversations,
                    onBack = ::back,
                    onOpen = { id ->
                        chatViewModel.openConversation(id)
                        openChat()
                    },
                    onRename = chatViewModel::renameSession,
                    onUnarchive = { id -> chatViewModel.setSessionArchived(id, false) },
                    onDelete = chatViewModel::deleteSession,
                )
            }
        }

        if (drawerOpen || drawerProgress > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = drawerProgress }
                    .background(colors.scrim.copy(alpha = 0.32f * drawerProgress))
                    .then(
                        if (drawerOpen) {
                            Modifier.clickable { drawerOpen = false }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (drawerOpen || drawerProgress > 0.01f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(304.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationX = -drawerWidthPx * (1f - drawerProgress) },
            ) {
                DeepDeepDrawer(
                conversations = state.conversations,
                activeConversationId = state.conversationId,
                historyLoaded = state.historyLoaded,
                archivedCount = state.archivedCount,
                userName = "Milous",
                onChat = ::openChat,
                onArchived = {
                    drawerOpen = false
                    navigator.push(AppDestination.Archived)
                },
                onNewChat = {
                    drawerOpen = false
                    chatViewModel.newChat()
                },
                onConversation = { id ->
                    drawerOpen = false
                    chatViewModel.openConversation(id)
                },
                onRename = chatViewModel::renameSession,
                onSetPinned = chatViewModel::setSessionPinned,
                onSetArchived = chatViewModel::setSessionArchived,
                onDelete = chatViewModel::deleteSession,
                    onSettings = ::openSettings,
                )
            }
        }
    }
}
