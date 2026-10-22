package io.github.whywood.deepdeep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.DeepDeepDrawer
import io.github.whywood.deepdeep.ui.chat.ChatScreen
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.sessions.ArchivedSessionsScreen
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Page { CHAT, SETTINGS, PROVIDERS, PROVIDER_DETAIL, ARCHIVED }

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

    var pageName by rememberSaveable { mutableStateOf(Page.CHAT.name) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val page = runCatching { Page.valueOf(pageName) }.getOrDefault(Page.CHAT)
    val activeProvider = graph.providerCatalog.summary(state.providerId)
    val selectedProvider = selectedProviderId?.let(graph.providerCatalog::summary)

    fun openChat() {
        pageName = Page.CHAT.name
        drawerOpen = false
    }

    fun openSettings() {
        pageName = Page.SETTINGS.name
        drawerOpen = false
    }

    fun openProviders() {
        pageName = Page.PROVIDERS.name
    }

    fun openProvider(providerId: String) {
        selectedProviderId = providerId
        pageName = Page.PROVIDER_DETAIL.name
    }

    BackHandler(enabled = drawerOpen || page != Page.CHAT) {
        when {
            drawerOpen -> drawerOpen = false
            page == Page.PROVIDER_DETAIL -> pageName = Page.PROVIDERS.name
            page == Page.PROVIDERS || page == Page.SETTINGS || page == Page.ARCHIVED -> openChat()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        when (page) {
            Page.CHAT -> ChatScreen(
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

            Page.SETTINGS -> SettingsScreen(
                userName = "Milous",
                providerName = activeProvider?.name,
                modelName = state.modelLabel.takeIf { it.isNotBlank() },
                configured = state.providerId in configuredProviders,
                themeMode = theme,
                linuxState = linuxState,
                linuxSupported = graph.linuxEnvironment.isSupported,
                linuxEnabled = linuxEnabled,
                versionName = BuildConfig.VERSION_NAME,
                onBack = ::openChat,
                onThemeModeChange = onThemeChange,
                onOpenProviders = ::openProviders,
                onLinuxInstall = { scope.launch { graph.linuxEnvironment.install() } },
                onLinuxReset = {
                    scope.launch {
                        graph.linuxSettings.setEnabled(false)
                        graph.linuxEnvironment.reset()
                        graph.host.reconfigure()
                    }
                },
                onLinuxEnabledChange = { enabled ->
                    scope.launch {
                        graph.linuxSettings.setEnabled(enabled)
                        graph.host.reconfigure()
                    }
                },
            )

            Page.PROVIDERS -> ProviderListScreen(
                providers = graph.providerCatalog.summaries,
                configuredProviderIds = configuredProviders,
                activeProviderId = state.providerId,
                onBack = ::openSettings,
                onProviderClick = ::openProvider,
            )

            Page.PROVIDER_DETAIL -> {
                val provider = selectedProvider
                if (provider == null) {
                    openProviders()
                } else {
                    ProviderDetailScreen(
                        provider = provider,
                        models = graph.providerCatalog.models(provider.id),
                        configured = provider.id in configuredProviders,
                        active = provider.id == state.providerId,
                        activeModelId = state.modelId.takeIf { provider.id == state.providerId },
                        onBack = ::openProviders,
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

            Page.ARCHIVED -> ArchivedSessionsScreen(
                archivedSessions = state.archivedConversations,
                onBack = ::openChat,
                onOpen = { id ->
                    chatViewModel.openConversation(id)
                    openChat()
                },
                onRename = chatViewModel::renameSession,
                onUnarchive = { id -> chatViewModel.setSessionArchived(id, false) },
                onDelete = chatViewModel::deleteSession,
            )
        }

        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable { drawerOpen = false },
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            DeepDeepDrawer(
                conversations = state.conversations,
                activeConversationId = state.conversationId,
                historyLoaded = state.historyLoaded,
                archivedCount = state.archivedCount,
                userName = "Milous",
                onChat = ::openChat,
                onArchived = {
                    pageName = Page.ARCHIVED.name
                    drawerOpen = false
                },
                onNewChat = {
                    chatViewModel.newChat()
                    openChat()
                },
                onConversation = { id ->
                    chatViewModel.openConversation(id)
                    openChat()
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
