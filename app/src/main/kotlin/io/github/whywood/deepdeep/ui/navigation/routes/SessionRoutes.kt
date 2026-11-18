package io.github.whywood.deepdeep.ui.navigation.routes

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.whywood.deepdeep.data.BotAvatarStore
import io.github.whywood.deepdeep.runtime.host.HostAccess
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.sessions.ArchivedSessionsScreen
import io.github.whywood.deepdeep.ui.sessions.SessionSearchScreen
import io.github.whywood.deepdeep.ui.timers.TimerScreen
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceSettingsScreen

@Composable
internal fun ArchivedRoute(
    chatViewModel: ChatViewModel,
    archived: List<ConversationSummary>,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
) {
    ArchivedSessionsScreen(
        archivedSessions = archived,
        onBack = onBack,
        onOpen = { id ->
            chatViewModel.openConversation(id)
            onOpenChat()
        },
        onRename = chatViewModel::renameSession,
        onUnarchive = { id -> chatViewModel.setSessionArchived(id, false) },
        onDelete = chatViewModel::deleteSession,
    )
}

@Composable
internal fun SearchRoute(
    chatViewModel: ChatViewModel,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    SessionSearchScreen(
        onBack = onBack,
        loadSessions = chatViewModel::loadSearchSessions,
        onOpen = { id ->
            chatViewModel.openConversation(id)
            navigator.reset(AppDestination.Chat)
        },
    )
}

@Composable
internal fun TimersRoute(
    dependencies: AppDependencies,
    chatViewModel: ChatViewModel,
    conversationId: String?,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    TimerScreen(
        service = dependencies.timerService,
        runtime = dependencies.host,
        initialConversationId = conversationId,
        onBack = onBack,
        onOpenConversation = { sessionId ->
            chatViewModel.openConversationById(sessionId)
            navigator.reset(AppDestination.Chat)
        },
    )
}

@Composable
internal fun WorkspaceSettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    workspaces: List<RuntimeWorkspace>,
    workspaceId: String,
    onUpdateProfile: (String, String?, String?) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val workspace = workspaces.firstOrNull { it.id == workspaceId }
    val context = LocalContext.current
    val botAvatars = remember { BotAvatarStore(context) }
    val scope = rememberCoroutineScope()
    val hostState by dependencies.hostAccess.state.collectAsStateWithLifecycle()
    val screenToolsReady = hostState.effective == HostAccess.ADB || hostState.effective == HostAccess.ROOT
    if (workspace == null) {
        LaunchedEffect(workspaceId) { navigator.pop() }
        return
    }
    WorkspaceSettingsScreen(
        workspace = workspace,
        skillRepository = dependencies.skillRepository,
        agentDocStore = dependencies.agentDocStore,
        workspaceToolPolicies = dependencies.workspaceToolPolicies,
        pluginManager = dependencies.pluginManager,
        mcpConfigStore = dependencies.mcpConfigStore,
        existingNames = workspaces.filterNot { it.id == workspaceId }.map { it.name },
        screenToolsReady = screenToolsReady,
        onRename = { name -> onRenameWorkspace(workspaceId, name) },
        onAvatarPicked = { uri ->
            scope.launch {
                val previous = workspace.avatar
                val path = if (uri == null) null else botAvatars.save(uri)
                if (uri == null || path != null) {
                    onUpdateProfile(workspaceId, path, workspace.intro)
                    botAvatars.delete(previous)
                }
            }
        },
        onIntroChange = { intro ->
            onUpdateProfile(workspaceId, workspace.avatar, intro.trim().takeIf { it.isNotEmpty() })
        },
        onBack = onBack,
        onOpenSkills = { navigator.push(AppDestination.Skills) },
    )
}
