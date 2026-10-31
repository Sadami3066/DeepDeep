package io.github.whywood.deepdeep.ui.shell

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.SharePayload
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.chat.displayToolName
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

internal const val SESSION_LOOKUP_GRACE_MS = 1_500L
internal const val SHARE_IMPORT_TIMEOUT_MS = 8_000L

data class ShellIntakeChatState(
    val ready: Boolean, val historyLoaded: Boolean, val sending: Boolean,
    val switchingConversation: Boolean, val conversationId: String?,
    val conversationCwd: String, val conversations: List<ConversationSummary>,
    val archivedConversations: List<ConversationSummary>, val archivedCount: Int,
)

data class ShellIntakeActions(
    val onOpenSession: (String) -> Unit, val onSessionMissing: () -> Unit,
    val onRefreshSessions: () -> Unit, val onApplyDeepLink: (AppDestination) -> Unit,
    val onShareStaged: suspend (SharePayload) -> Unit, val onShareTimeout: () -> Unit,
)

sealed interface SessionIntake {
    data object Idle : SessionIntake

    data object WaitingForReady : SessionIntake

    data class Open(val sessionId: String) : SessionIntake

    data class LookupFailed(val sessionId: String) : SessionIntake
}

sealed interface ShareIntake {
    data object Idle : ShareIntake

    data object WaitingForReady : ShareIntake

    data object WaitingForSession : ShareIntake

    data object WaitingForChat : ShareIntake

    data object Ready : ShareIntake
}

sealed interface DeepLinkIntake {
    data object Idle : DeepLinkIntake

    data object WaitingForReady : DeepLinkIntake

    data class Apply(val destination: AppDestination) : DeepLinkIntake
}

fun resolveSessionIntake(requestedId: String?, chat: ShellIntakeChatState): SessionIntake {
    if (requestedId == null) return SessionIntake.Idle
    if (!chat.ready) return SessionIntake.WaitingForReady
    val found = chat.conversations.any { it.id == requestedId } ||
        chat.archivedConversations.any { it.id == requestedId }
    return if (found) SessionIntake.Open(requestedId) else SessionIntake.LookupFailed(requestedId)
}
fun needsSecondSessionGrace(archivedCount: Int, archivedConversations: List<ConversationSummary>): Boolean =
    archivedCount > 0 && archivedConversations.isEmpty()
fun resolveShareIntake(hasPayload: Boolean, chat: ShellIntakeChatState): ShareIntake = when {
    !hasPayload -> ShareIntake.Idle
    !chat.ready -> ShareIntake.WaitingForReady
    chat.conversationId == null -> ShareIntake.WaitingForSession
    !chat.historyLoaded || chat.sending || chat.switchingConversation -> ShareIntake.WaitingForChat
    chat.conversationCwd.isBlank() -> ShareIntake.WaitingForChat
    else -> ShareIntake.Ready
}

fun shareWaitRemainingMs(startedAt: Long, now: Long, timeoutMs: Long): Long =
    (startedAt + timeoutMs - now).coerceAtLeast(0L)

fun resolveDeepLinkIntake(destination: AppDestination?, ready: Boolean): DeepLinkIntake = when {
    destination == null -> DeepLinkIntake.Idle
    !ready -> DeepLinkIntake.WaitingForReady
    else -> DeepLinkIntake.Apply(destination)
}

@Composable
internal fun ShellIntakeHost(
    openSessionId: String?,
    pendingShare: SharePayload?,
    pendingDeepDestination: AppDestination?,
    chat: ShellIntakeChatState,
    chatSnapshot: () -> ShellIntakeChatState,
    actions: ShellIntakeActions,
) {
    var shareWaitStartedAt by remember { mutableStateOf(0L) }

    LaunchedEffect(
        pendingShare,
        chat.ready,
        chat.historyLoaded,
        chat.sending,
        chat.switchingConversation,
        chat.conversationCwd,
        chat.conversationId,
    ) {
        when (val intake = resolveShareIntake(pendingShare != null, chat)) {
            ShareIntake.Idle, ShareIntake.WaitingForChat -> shareWaitStartedAt = 0L
            ShareIntake.WaitingForReady -> Unit
            ShareIntake.WaitingForSession -> {
                if (shareWaitStartedAt == 0L) shareWaitStartedAt = System.currentTimeMillis()
                val remaining = shareWaitRemainingMs(shareWaitStartedAt, System.currentTimeMillis(), SHARE_IMPORT_TIMEOUT_MS)
                if (remaining > 0) delay(remaining)
                if (chatSnapshot().conversationId == null) actions.onShareTimeout()
                shareWaitStartedAt = 0L
            }

            ShareIntake.Ready -> {
                shareWaitStartedAt = 0L
                pendingShare?.let { payload -> actions.onShareStaged(payload) }
            }
        }
    }

    LaunchedEffect(openSessionId, chat.ready, chat.conversations, chat.archivedConversations) {
        when (val intake = resolveSessionIntake(openSessionId, chat)) {
            SessionIntake.Idle, SessionIntake.WaitingForReady -> Unit
            is SessionIntake.Open -> actions.onOpenSession(intake.sessionId)
            is SessionIntake.LookupFailed -> {
                actions.onRefreshSessions()
                delay(SESSION_LOOKUP_GRACE_MS)
                var latest = chatSnapshot()
                if (needsSecondSessionGrace(latest.archivedCount, latest.archivedConversations)) {
                    delay(SESSION_LOOKUP_GRACE_MS)
                    latest = chatSnapshot()
                }
                val appearsLater = latest.conversations.any { it.id == intake.sessionId } ||
                    latest.archivedConversations.any { it.id == intake.sessionId }
                if (!appearsLater) actions.onSessionMissing()
            }
        }
    }

    LaunchedEffect(pendingDeepDestination, chat.ready) {
        when (val intake = resolveDeepLinkIntake(pendingDeepDestination, chat.ready)) {
            DeepLinkIntake.Idle, DeepLinkIntake.WaitingForReady -> Unit
            is DeepLinkIntake.Apply -> actions.onApplyDeepLink(intake.destination)
        }
    }
}

@Composable
internal fun rememberShellChatUiStrings(): ChatUiStrings {
    val retryTemplate = stringResource(R.string.chat_status_retrying_format)
    val runningToolTemplate = stringResource(R.string.chat_status_running_tool_format)
    val autoRetryTemplate = stringResource(R.string.chat_status_auto_retry)
    val errorCodeTemplate = stringResource(R.string.chat_error_with_code)
    val skillMissingTemplate = stringResource(R.string.chat_error_skill_missing)
    return ChatUiStrings(
        thinking = stringResource(R.string.chat_status_thinking),
        retrying = { attempt -> retryTemplate.replace("%1\$d", attempt.toString()) },
        compacting = stringResource(R.string.chat_status_compacting),
        compactionFailed = stringResource(R.string.chat_status_compaction_failed),
        runningTool = { name -> runningToolTemplate.replace("%1\$s", displayToolName(name)) },
        requestFailed = stringResource(R.string.chat_error_request_failed),
        runtimeStartFailed = stringResource(R.string.chat_error_runtime_start),
        preparingSession = stringResource(R.string.chat_error_preparing),
        maxAttachments = stringResource(R.string.chat_error_max_attachments),
        attachmentTooLarge = stringResource(R.string.chat_error_attachment_too_large),
        attachmentFailed = stringResource(R.string.chat_error_attachment_failed),
        restoringSession = stringResource(R.string.chat_error_restoring),
        sessionNotReady = stringResource(R.string.chat_error_session_not_ready),
        createWorkspaceFailed = stringResource(R.string.chat_error_create_workspace),
        switchModelFailed = stringResource(R.string.chat_error_switch_model),
        switchThinkingFailed = stringResource(R.string.chat_error_switch_thinking),
        openConversationFailed = stringResource(R.string.chat_error_open_conversation),
        reloadBusy = stringResource(R.string.chat_reload_busy),
        reloadFailed = stringResource(R.string.chat_reload_failed),
        newChatFailed = stringResource(R.string.chat_error_new_chat),
        newChatTitle = stringResource(R.string.chat_title_new),
        invalidApiKey = stringResource(R.string.chat_error_invalid_api_key),
        rateLimited = stringResource(R.string.chat_error_rate_limited),
        timeout = stringResource(R.string.chat_error_timeout),
        networkError = stringResource(R.string.chat_error_network),
        invalidToolArgs = stringResource(R.string.chat_error_invalid_tool_args),
        serviceUnavailable = stringResource(R.string.chat_error_service_unavailable),
        insufficientBalance = stringResource(R.string.chat_error_insufficient_balance),
        modelUnavailable = stringResource(R.string.chat_error_model_unavailable),
        retry = stringResource(R.string.assistant_retry),
        autoRetrying = { attempt, max ->
            autoRetryTemplate.replace("%1\$d", attempt.toString()).replace("%2\$d", max.toString())
        },
        withCode = { base, code ->
            errorCodeTemplate.replace("%1\$s", base).replace("%2\$s", code)
        },
        nothingToCompact = stringResource(R.string.chat_compact_none),
        compactionBusy = stringResource(R.string.chat_compact_busy),
        compactionUnavailable = stringResource(R.string.chat_compact_unavailable),
        skillMissing = { name -> skillMissingTemplate.replace("%1\$s", name) },
        stopBeforeCommand = stringResource(R.string.chat_error_stop_before_command),
        insufficientSpace = stringResource(R.string.chat_error_insufficient_space),
        unsupportedImage = stringResource(R.string.chat_error_unsupported_image),
        imageFailed = stringResource(R.string.chat_error_image_failed),
    )
}

@Composable
internal fun rememberShellChatViewModel(dependencies: AppDependencies, uiStrings: ChatUiStrings): ChatViewModel {
    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    dependencies.host, dependencies.providerCatalog, dependencies.modelConfigStore,
                    dependencies.attachmentStore, dependencies::prepare,
                    autoRetry = dependencies.appPreferences.preferences.map { it.autoRetry },
                    uiStrings = uiStrings,
                    skillExpander = dependencies.skillCatalog,
                    interruptedQuestions = { id ->
                        dependencies.questionCenter.interrupted.value.filter { it.sessionId == id }
                    },
                    dismissInterruptedQuestions = dependencies.questionCenter::dismissInterrupted,
                    interruptedRuns = { id ->
                        dependencies.host.interruptedRuns.value.filter { it.sessionId == id }
                    },
                    dismissInterruptedRun = dependencies.host::dismissInterruptedRun,
                )
            }
        },
    )
    val locale = LocalConfiguration.current
    LaunchedEffect(locale) {
        chatViewModel.updateUiStrings(uiStrings)
    }
    return chatViewModel
}

@Composable
internal fun ShellChatLifecycle(chatViewModel: ChatViewModel) {
    val context = LocalContext.current
    DisposableEffect(context, chatViewModel) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                chatViewModel.onTrimMemory(level)
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = Unit
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose { context.applicationContext.unregisterComponentCallbacks(callbacks) }
    }
    LifecycleResumeEffect(Unit) {
        chatViewModel.onAppForeground()
        GuiAccessibilityState.refresh(context)
        onPauseOrDispose { }
    }
}

@Composable
internal fun ShellNavigationBackHandler(navigator: AppNavigator, drawerOpen: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = drawerOpen || navigator.canPop, onBack = onBack)
}

@Composable
internal fun ShellNavigationChrome(navigator: AppNavigator) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(navigator.current) {
        if (navigator.current != AppDestination.Chat) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}
