package io.github.whywood.superpi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.superpi.core.AppDependencies
import io.github.whywood.superpi.data.SharePayload
import io.github.whywood.superpi.design.DdFontStyle
import io.github.whywood.superpi.runtime.AgentRuntimeHost
import io.github.whywood.superpi.ui.navigation.AppDestination
import io.github.whywood.superpi.ui.shell.AppOnboardingGate
import io.github.whywood.superpi.ui.shell.AppShell
import io.github.whywood.superpi.ui.shell.ShellChatLifecycle
import io.github.whywood.superpi.ui.shell.rememberShellChatViewModel
import io.github.whywood.superpi.ui.shell.rememberShellConversationStrings

@Composable
internal fun SuperPIApp(
    graph: AppDependencies,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    pendingDeepDestination: AppDestination? = null,
    onDeepDestinationConsumed: () -> Unit = {},
    pendingShare: SharePayload? = null,
    onShareConsumed: () -> Unit = {},
    pendingPluginImportUri: String? = null,
    onPluginImportConsumed: () -> Unit = {},
    onActiveSessionChanged: (String?) -> Unit = {},
    onFontStyleChange: (DdFontStyle) -> Unit = {},
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
            openSessionId = openSessionId,
            onOpenSessionConsumed = onOpenSessionConsumed,
            pendingDeepDestination = pendingDeepDestination,
            onDeepDestinationConsumed = onDeepDestinationConsumed,
            pendingShare = pendingShare,
            onShareConsumed = onShareConsumed,
            pendingPluginImportUri = pendingPluginImportUri,
            onPluginImportConsumed = onPluginImportConsumed,
            onFontStyleChange = onFontStyleChange,
        )
    }
}
