package io.github.whywood.deepdeep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.SharePayload
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.ThemePreset
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.shell.AppOnboardingGate
import io.github.whywood.deepdeep.ui.shell.AppShell
import io.github.whywood.deepdeep.ui.shell.ShellChatLifecycle
import io.github.whywood.deepdeep.ui.shell.rememberShellChatViewModel
import io.github.whywood.deepdeep.ui.shell.rememberShellConversationStrings

@Composable
internal fun DeepDeepApp(
    graph: AppDependencies,
    theme: ThemeMode,
    themePreset: ThemePreset,
    fontStyle: DdFontStyle = DdFontStyle.FREDOKA,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    pendingDeepDestination: AppDestination? = null,
    onDeepDestinationConsumed: () -> Unit = {},
    pendingShare: SharePayload? = null,
    onShareConsumed: () -> Unit = {},
    onActiveSessionChanged: (String?) -> Unit = {},
    onFontStyleChange: (DdFontStyle) -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit,
    onThemePresetChange: (ThemePreset) -> Unit,
) {
    val chatUiStrings = rememberShellConversationStrings()
    val chatViewModel = rememberShellChatViewModel(graph, chatUiStrings)
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    val authKinds by graph.credentials.authKinds.collectAsStateWithLifecycle(initialValue = emptyMap())
    ShellChatLifecycle(chatViewModel)
    LaunchedEffect(state.conversationId) {
        onActiveSessionChanged(state.conversationId)
    }
    AppOnboardingGate(
        graph = graph,
        runtimeReady = state.ready,
        hasCredentials = authKinds.isNotEmpty(),
        hasSessions = state.conversations.isNotEmpty(),
        onFinished = { chatViewModel.newSession(AgentRuntimeHost.HOME_ID) },
    ) {
        AppShell(
            graph = graph,
            chatViewModel = chatViewModel,
            state = state,
            theme = theme,
            themePreset = themePreset,
            fontStyle = fontStyle,
            openSessionId = openSessionId,
            onOpenSessionConsumed = onOpenSessionConsumed,
            pendingDeepDestination = pendingDeepDestination,
            onDeepDestinationConsumed = onDeepDestinationConsumed,
            pendingShare = pendingShare,
            onShareConsumed = onShareConsumed,
            onFontStyleChange = onFontStyleChange,
            onThemeChange = onThemeChange,
            onThemePresetChange = onThemePresetChange,
        )
    }
}
