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
import io.github.whywood.deepdeep.ui.chat.AssistantScreen
import io.github.whywood.deepdeep.ui.chat.AssistantViewModel
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Page { CHAT, SETTINGS }

@Composable
internal fun DeepDeepApp(
    graph: AppGraph,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel: AssistantViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AssistantViewModel(
                    host = graph.host,
                    modelConfigStore = graph.modelConfigStore,
                    prepareRuntime = graph::prepare,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasApiKey by graph.apiKeyStore.hasKey(DeepSeekProvider).collectAsStateWithLifecycle(initialValue = false)
    val modelConfig by graph.modelConfigStore.config.collectAsStateWithLifecycle(initialValue = io.github.whywood.deepdeep.runtime.ModelConfig())
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by graph.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val colors = deepDeepColors()

    var pageName by rememberSaveable { mutableStateOf(Page.CHAT.name) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val page = runCatching { Page.valueOf(pageName) }.getOrDefault(Page.CHAT)

    fun openChat() {
        pageName = Page.CHAT.name
        drawerOpen = false
    }

    fun openSettings() {
        pageName = Page.SETTINGS.name
        drawerOpen = false
    }

    BackHandler(enabled = drawerOpen || page != Page.CHAT) {
        when {
            drawerOpen -> drawerOpen = false
            page == Page.SETTINGS -> openChat()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        when (page) {
            Page.CHAT -> AssistantScreen(
                state = state,
                configured = hasApiKey,
                configuredModel = modelConfig?.modelId,
                userName = "Milous",
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onNewChat = viewModel::newChat,
                onOpenDrawer = { drawerOpen = true },
                onOpenSettings = ::openSettings,
                onModelChange = viewModel::setModelOverride,
                onThinkingLevelChange = viewModel::setThinkingLevel,
                onToggleStep = viewModel::toggleStep,
            )

            Page.SETTINGS -> SettingsScreen(
                themeMode = theme,
                hasApiKey = hasApiKey,
                modelId = modelConfig?.modelId ?: "",
                thinkingLevel = modelConfig?.thinkingLevel ?: state.thinkingLevel,
                linuxState = linuxState,
                linuxSupported = graph.linuxEnvironment.isSupported,
                linuxEnabled = linuxEnabled,
                versionName = BuildConfig.VERSION_NAME,
                onBack = ::openChat,
                onThemeModeChange = onThemeChange,
                onApiKeySave = { key ->
                    scope.launch {
                        graph.apiKeyStore.save(DeepSeekProvider, key)
                        graph.credentials.preload(DeepSeekProvider)
                    }
                },
                onApiKeyClear = {
                    scope.launch {
                        graph.apiKeyStore.save(DeepSeekProvider, null)
                        graph.credentials.invalidate(DeepSeekProvider)
                    }
                },
                onModelIdChange = { modelId ->
                    scope.launch {
                        val current = graph.modelConfigStore.read()
                        graph.host.setModel(modelId, current.thinkingLevel)
                    }
                },
                onThinkingLevelChange = { level ->
                    scope.launch { graph.host.setThinkingLevel(level) }
                },
                onLinuxInstall = {
                    scope.launch { graph.linuxEnvironment.install() }
                },
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
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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
                userName = "Milous",
                onChat = ::openChat,
                onConversation = { id ->
                    viewModel.openConversation(id)
                    openChat()
                },
                onNewChat = {
                    viewModel.newChat()
                    openChat()
                },
                onSettings = ::openSettings,
            )
        }
    }
}

private const val DeepSeekProvider = "deepseek"
