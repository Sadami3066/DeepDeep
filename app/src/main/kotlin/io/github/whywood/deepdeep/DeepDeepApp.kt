package io.github.whywood.deepdeep

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.UiToggle
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.DeepDeepDrawer
import io.github.whywood.deepdeep.ui.chat.AttachmentPreviewDialog
import io.github.whywood.deepdeep.ui.chat.ChatCommand
import io.github.whywood.deepdeep.ui.chat.ChatScreen
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.chat.CommandItem
import io.github.whywood.deepdeep.ui.chat.ParsedCommand
import io.github.whywood.deepdeep.ui.chat.UsageSheet
import io.github.whywood.deepdeep.ui.chat.findCommandTrigger
import io.github.whywood.deepdeep.ui.chat.parseChatCommand
import io.github.whywood.deepdeep.ui.goal.GoalSheet
import io.github.whywood.deepdeep.ui.files.FileBrowserSheet
import io.github.whywood.deepdeep.ui.jobs.JobsSheet
import io.github.whywood.deepdeep.ui.schedules.ScheduleScreen
import io.github.whywood.deepdeep.ui.skills.SkillsScreen
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceSettingsScreen
import io.github.whywood.deepdeep.ui.navigation.AnimatedScreenHost
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.rememberAppNavigator
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import io.github.whywood.deepdeep.ui.sessions.ArchivedSessionsScreen
import io.github.whywood.deepdeep.ui.sessions.SessionSearchScreen
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen
import io.github.whywood.deepdeep.ui.settings.ChatSettingsScreen
import io.github.whywood.deepdeep.ui.settings.FontScreen
import io.github.whywood.deepdeep.ui.settings.PersonalizationScreen
import io.github.whywood.deepdeep.ui.settings.PrivacyScreen
import io.github.whywood.deepdeep.ui.settings.RuntimeSettingsScreen
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import io.github.whywood.deepdeep.ui.workspaces.DeleteWorkspaceDialog
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceNameDialog
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceNameMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun DeepDeepApp(
    graph: AppGraph,
    theme: ThemeMode,
    fontStyle: DdFontStyle = DdFontStyle.NEWSREADER,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    onFontStyleChange: (DdFontStyle) -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reloadDoneMessage = stringResource(R.string.chat_reload_done)
    val retryTemplate = stringResource(R.string.chat_status_retrying_format)
    val runningToolTemplate = stringResource(R.string.chat_status_running_tool_format)
    val autoRetryTemplate = stringResource(R.string.chat_status_auto_retry)
    val errorCodeTemplate = stringResource(R.string.chat_error_with_code)
    val chatUiStrings = ChatUiStrings(
        thinking = stringResource(R.string.chat_status_thinking),
        retrying = { attempt -> retryTemplate.replace("%1\$d", attempt.toString()) },
        compacting = stringResource(R.string.chat_status_compacting),
        compactionFailed = stringResource(R.string.chat_status_compaction_failed),
        runningTool = { name -> runningToolTemplate.replace("%1\$s", name) },
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
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    runtime = graph.host,
                    providers = graph.providerCatalog,
                    modelConfigStore = graph.modelConfigStore,
                    attachmentStore = graph.attachmentStore,
                    prepareRuntime = graph::prepare,
                    autoRetry = graph.appPreferences.preferences.map { preferences -> preferences.autoRetry },
                    uiStrings = chatUiStrings,
                )
            }
        },
    )
    val state by chatViewModel.state.collectAsStateWithLifecycle()
    val attachmentRoot = remember(state.conversationCwd) {
        state.conversationCwd.takeIf { it.isNotBlank() }?.let(::File) ?: graph.homeDir
    }
    var previewRoot by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.assistant_attach_share)
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(chatViewModel::attach)
    }
    var previewAttachment by remember { mutableStateOf<AttachmentRef?>(null) }
    val configuredProviders by graph.apiKeyStore.providerIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val schedules by graph.scheduleService.schedules.collectAsStateWithLifecycle()
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by graph.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val sharedStorageEnabled by graph.linuxSettings.sharedStorageEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val devPackages by graph.devPackageManager.state.collectAsStateWithLifecycle()
    val colors = deepDeepColors()
    val attachmentMissingMessage = stringResource(R.string.assistant_attach_missing)
    val presentOpenFailed = stringResource(R.string.present_open_failed)
    val presentShareFailed = stringResource(R.string.present_share_failed)
    val openWorkspaceFile: (String) -> Unit = { raw ->
        val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
        if (resolved == null) {
            Toast.makeText(context, attachmentMissingMessage, Toast.LENGTH_SHORT).show()
        } else {
            previewRoot = attachmentRoot
            previewAttachment = resolved
        }
    }
    val navigator = rememberAppNavigator()
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { 304.dp.toPx() }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val uiPreferences by graph.appPreferences.preferences.collectAsStateWithLifecycle(initialValue = UiPreferences())
    val pendingQuestions by graph.questionCenter.pending.collectAsStateWithLifecycle()
    val activeQuestions = pendingQuestions.filter { it.sessionId == state.conversationId }
    val foreignQuestion = pendingQuestions.firstOrNull { it.sessionId != state.conversationId }
    val goals by graph.goalService.goals.collectAsStateWithLifecycle()
    val activeGoal = state.conversationId?.let { goals[it] }?.takeIf { it.goal.phase != io.github.whywood.deepdeep.runtime.GoalPhase.COMPLETE }
    val allJobs by graph.jobRegistry.jobs.collectAsStateWithLifecycle()
    val activeJobs = allJobs.filter { it.sessionId == state.conversationId }
    var modelsOpen by remember { mutableStateOf(false) }
    var usageSheetOpen by remember { mutableStateOf(false) }
    var goalSheetOpen by remember { mutableStateOf(false) }
    var jobsSheetOpen by remember { mutableStateOf(false) }
    var filesSheetOpen by remember { mutableStateOf(false) }
    var selectedJobId by remember { mutableStateOf<String?>(null) }
    var jobOutput by remember { mutableStateOf<String?>(null) }
    val personalization by graph.personalizationStore.personalization.collectAsStateWithLifecycle(
        initialValue = PersonalizationUi(),
    )
    val nickname = personalization.nickname.trim().ifBlank { "DeepDeep" }
    val expandedWorkspacesSaver = remember {
        Saver<Set<String>, ArrayList<String>>(
            save = { ArrayList(it) },
            restore = { it.toSet() },
        )
    }
    var expandedWorkspaceIds by rememberSaveable(stateSaver = expandedWorkspacesSaver) {
        mutableStateOf(emptySet<String>())
    }
    var workspaceCreateOpen by remember { mutableStateOf(false) }
    var workspaceRenameTarget by remember { mutableStateOf<RuntimeWorkspace?>(null) }
    var workspaceDeleteTarget by remember { mutableStateOf<RuntimeWorkspace?>(null) }
    var workspaceDeleteCount by remember { mutableIntStateOf(0) }
    val drawerProgress by animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "drawer-progress",
    )
    val activeProvider = graph.providerCatalog.summary(state.providerId)
    val appResources = LocalResources.current

    val commandItems = listOf(
        CommandItem(
            id = ChatCommand.COMPACT.commandName,
            name = ChatCommand.COMPACT.commandName,
            title = stringResource(R.string.chat_cmd_compact),
            description = stringResource(R.string.chat_cmd_compact_desc),
            enabled = state.conversationId != null && !state.sending && !state.compacting,
        ),
        CommandItem(
            id = ChatCommand.GOAL.commandName,
            name = ChatCommand.GOAL.commandName,
            title = stringResource(R.string.chat_cmd_goal),
            description = stringResource(R.string.chat_cmd_goal_desc),
            enabled = state.conversationId != null,
        ),
    )

    fun rejectCommandArgs(name: String) {
        chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_usage, name))
    }

    fun runGoalCommand(args: String) {
        val sessionId = state.conversationId
        if (sessionId == null) {
            chatViewModel.showCommandError(appResources.getString(R.string.chat_error_session_not_ready))
            return
        }
        when (args) {
            "" -> goalSheetOpen = true
            "pause" -> scope.launch { graph.goalService.pause(sessionId) }
            "resume" -> scope.launch { graph.goalService.resume(sessionId) }
            "clear" -> scope.launch { graph.goalService.clear(sessionId) }
            else -> scope.launch {
                if (graph.goalService.create(sessionId, args) == null) {
                    chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_goal_invalid))
                }
            }
        }
    }

    fun runChatCommand(command: ChatCommand, args: String) {
        when (command) {
            ChatCommand.COMPACT -> if (args.isEmpty()) chatViewModel.compactContext() else rejectCommandArgs(command.commandName)
            ChatCommand.GOAL -> runGoalCommand(args.trim())
        }
    }

    fun pickCommand(command: ChatCommand) {
        val trigger = findCommandTrigger(state.input)
        if (trigger != null) {
            chatViewModel.onInputChange(state.input.removeRange(trigger.start, state.input.length).trimEnd())
        }
        runChatCommand(command, "")
    }

    fun submitChatInput() {
        when (val parsed = parseChatCommand(state.input)) {
            ParsedCommand.None -> chatViewModel.send()
            is ParsedCommand.Found -> {
                if (!parsed.command.takesArguments && parsed.args.isNotEmpty()) {
                    rejectCommandArgs(parsed.command.commandName)
                } else {
                    chatViewModel.onInputChange("")
                    runChatCommand(parsed.command, parsed.args)
                }
            }

            is ParsedCommand.Unknown -> {
                chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_unknown, parsed.name))
            }
        }
    }

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

    LaunchedEffect(state.ready, state.currentWorkspaceId) {
        if (state.ready && state.currentWorkspaceId.isNotBlank()) {
            expandedWorkspaceIds = expandedWorkspaceIds + state.currentWorkspaceId
        }
    }

    LaunchedEffect(openSessionId, state.ready, state.conversations, state.archivedConversations) {
        val sessionId = openSessionId ?: return@LaunchedEffect
        if (!state.ready) return@LaunchedEffect
        val found = state.conversations.any { it.id == sessionId } ||
            state.archivedConversations.any { it.id == sessionId }
        if (!found) return@LaunchedEffect
        drawerOpen = false
        navigator.reset(AppDestination.Chat)
        chatViewModel.openConversation(sessionId)
        onOpenSessionConsumed()
    }

    CompositionLocalProvider(LocalHapticsEnabled provides true) {
    Box(Modifier.fillMaxSize().background(colors.paper)) {
        AnimatedScreenHost(
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
        ) { destination ->
            when (destination) {
                AppDestination.Chat -> ChatScreen(
                    state = state.copy(configured = state.providerId in configuredProviders),
                    nickname = nickname,
                    onInputChange = chatViewModel::onInputChange,
                    onSend = ::submitChatInput,
                    onStop = chatViewModel::stop,
                    onOpenDrawer = { drawerOpen = true },
                    onOpenSettings = ::openSettings,
                    onReload = {
                        scope.launch {
                            val result = chatViewModel.reloadSession()
                            Toast.makeText(
                                context,
                                result.exceptionOrNull()?.message ?: reloadDoneMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    providers = graph.providerCatalog.summaries.filter { summary ->
                        summary.id in configuredProviders
                    },
                    onModelChange = chatViewModel::setModel,
                    onProviderChange = chatViewModel::setProvider,
                    onThinkingLevelChange = chatViewModel::setThinkingLevel,
                    onToggleStep = chatViewModel::toggleStep,
                    onQuoteMessage = chatViewModel::quote,
                    onClearQuote = chatViewModel::clearQuote,
                    attachments = state.attachments,
                    onPickAttachment = { attachmentPicker.launch(arrayOf("*/*")) },
                    onRemoveAttachment = chatViewModel::removeAttachment,
                    onOpenAttachment = {
                        previewRoot = attachmentRoot
                        previewAttachment = it
                    },
                    attachmentRoot = attachmentRoot,
                    autoCollapseProcess = uiPreferences.autoCollapseProcess,
                    reminderCount = schedules.count { schedule ->
                        schedule.enabled && schedule.sessionId == state.conversationId
                    },
                    onOpenSchedules = { navigator.push(AppDestination.Schedules) },
                    onRetry = chatViewModel::retry,
                    reloading = state.reloading,
                    usage = state.usage,
                    compacting = state.compacting,
                    commandItems = commandItems,
                    onCommand = ::pickCommand,
                    goal = activeGoal,
                    onOpenGoal = { goalSheetOpen = true },
                    onPauseGoal = {
                        state.conversationId?.let { id -> scope.launch { graph.goalService.pause(id) } }
                    },
                    onResumeGoal = {
                        state.conversationId?.let { id -> scope.launch { graph.goalService.resume(id) } }
                    },
                    jobCount = activeJobs.count { it.status == JobStatus.RUNNING },
                    onOpenJobs = {
                        selectedJobId = null
                        jobOutput = null
                        jobsSheetOpen = true
                    },
                    modelsOpen = modelsOpen,
                    onModelsOpenChange = { modelsOpen = it },
                    onOpenUsage = { usageSheetOpen = true },
                    pendingQuestions = activeQuestions,
                    onAnswerQuestion = { pending, answers ->
                        graph.questionCenter.answer(pending.requestId, answers)
                    },
                    foreignQuestion = foreignQuestion,
                    onOpenForeignQuestion = { sessionId ->
                        navigator.reset(AppDestination.Chat)
                        chatViewModel.openConversationById(sessionId)
                    },
                    onOpenFile = openWorkspaceFile,
                    onOpenFiles = { filesSheetOpen = true },
                    onOpenPresentFile = { raw ->
                        val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                        if (resolved == null) {
                            Toast.makeText(
                                context,
                                attachmentMissingMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (resolved.kind != AttachmentKind.OTHER) {
                            previewRoot = attachmentRoot
                            previewAttachment = resolved
                        } else {
                            val intent = graph.attachmentStore.viewIntent(resolved, attachmentRoot)
                            val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
                            if (!opened) {
                                Toast.makeText(context, presentOpenFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSharePresentFile = { raw ->
                        val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                        if (resolved == null) {
                            Toast.makeText(
                                context,
                                attachmentMissingMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            val intent = graph.attachmentStore.shareIntent(resolved, attachmentRoot)
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                            } else {
                                Toast.makeText(context, presentShareFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )

                AppDestination.Settings -> SettingsScreen(
                    nickname = nickname,
                    providerName = activeProvider?.name,
                    modelName = state.modelLabel.takeIf { it.isNotBlank() },
                    configured = state.providerId in configuredProviders,
                    themeMode = theme,
                    linuxState = linuxState,
                    versionName = BuildConfig.VERSION_NAME,
                    fontLabel = fontStyle.displayName,
                    onBack = ::back,
                    onThemeModeChange = onThemeChange,
                    onOpenChatSettings = { navigator.push(AppDestination.ChatSettings) },
                    onOpenProviders = { navigator.push(AppDestination.Providers) },
                    onOpenRuntime = { navigator.push(AppDestination.RuntimeSettings) },
                    onOpenPersonalization = { navigator.push(AppDestination.Personalization) },
                    onOpenFonts = { navigator.push(AppDestination.Fonts) },
                    onOpenPrivacy = { navigator.push(AppDestination.Privacy) },
                )

                AppDestination.Providers -> ProviderListScreen(
                    providers = graph.providerCatalog.summaries,
                    configuredProviderIds = configuredProviders,
                    activeProviderId = state.providerId,
                    onBack = ::back,
                    onProviderClick = { id -> navigator.push(AppDestination.ProviderDetail(id)) },
                )

                AppDestination.RuntimeSettings -> {
                    LaunchedEffect(linuxState) { graph.devPackageManager.refresh() }
                    RuntimeSettingsScreen(
                        linuxState = linuxState,
                        linuxSupported = graph.linuxEnvironment.isSupported,
                        linuxEnabled = linuxEnabled,
                        sharedStorageEnabled = sharedStorageEnabled,
                        devPackages = devPackages,
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
                        onSharedStorageChange = { enabled ->
                            scope.launch { graph.linuxSettings.setSharedStorageEnabled(enabled) }
                        },
                        onInstallBundle = graph.devPackageManager::installBundle,
                        onRefreshPackages = graph.devPackageManager::refresh,
                    )
                }

                AppDestination.Personalization -> PersonalizationScreen(
                    value = personalization,
                    onBack = ::back,
                    onSave = { value -> scope.launch { graph.personalizationStore.save(value) } },
                )

                AppDestination.Privacy -> PrivacyScreen(onBack = ::back)

                AppDestination.Fonts -> FontScreen(
                    current = fontStyle,
                    onBack = ::back,
                    onSelect = onFontStyleChange,
                )

                AppDestination.ChatSettings -> ChatSettingsScreen(
                    preferences = uiPreferences,
                    onBack = ::back,
                    onToggle = { toggle, enabled ->
                        scope.launch { graph.appPreferences.set(toggle, enabled) }
                    },
                )

                is AppDestination.ProviderDetail -> {
                    val provider = graph.providerCatalog.summary(destination.id)
                    if (provider == null) {
                        LaunchedEffect(destination.id) {
                            navigator.pop()
                        }
                    } else {
                        val savedApiKey by produceState<String?>(
                            initialValue = null,
                            provider.id,
                            provider.id in configuredProviders,
                        ) {
                            value = runCatching { graph.apiKeyStore.read(provider.id) }.getOrNull()
                        }
                        ProviderDetailScreen(
                            provider = provider,
                            models = graph.providerCatalog.models(provider.id),
                            configured = provider.id in configuredProviders,
                            active = provider.id == state.providerId,
                            activeModelId = state.modelId.takeIf { provider.id == state.providerId },
                            savedApiKey = savedApiKey,
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

                AppDestination.Search -> SessionSearchScreen(
                    onBack = ::back,
                    loadSessions = chatViewModel::loadSearchSessions,
                    onOpen = { id ->
                        chatViewModel.openConversation(id)
                        navigator.reset(AppDestination.Chat)
                    },
                )

                AppDestination.Skills -> SkillsScreen(
                    repository = graph.skillRepository,
                    onBack = ::back,
                )

                AppDestination.Schedules -> ScheduleScreen(
                    service = graph.scheduleService,
                    runtime = graph.host,
                    initialConversationId = state.conversationId,
                    onBack = ::back,
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
                            skillRepository = graph.skillRepository,
                            agentDocStore = graph.agentDocStore,
                            onBack = ::back,
                            onOpenSkills = { navigator.push(AppDestination.Skills) },
                        )
                    }
                }
            }
        }

        if (drawerOpen || drawerProgress > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = drawerProgress }
                    .drawBehind { drawRect(colors.scrim.copy(alpha = 0.32f * drawerProgress)) }
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
                    sessions = state.conversations,
                    archivedCount = state.archivedCount,
                    workspaces = state.workspaces,
                    pinnedWorkspaceIds = uiPreferences.pinnedWorkspaceIds,
                    expandedWorkspaceIds = expandedWorkspaceIds,
                    busySessionIds = state.busySessionIds,
                    reminderSessionIds = schedules.filter { it.enabled }.mapTo(mutableSetOf()) { it.sessionId },
                    userName = nickname,
                    onChat = ::openChat,
                    onArchived = {
                        drawerOpen = false
                        navigator.push(AppDestination.Archived)
                    },
                    onOpenSearch = {
                        drawerOpen = false
                        navigator.push(AppDestination.Search)
                    },
                    onOpenSkills = {
                        drawerOpen = false
                        navigator.push(AppDestination.Skills)
                    },
                    onScheduled = {
                        drawerOpen = false
                        navigator.push(AppDestination.Schedules)
                    },
                    onCreateWorkspace = {
                        drawerOpen = false
                        workspaceCreateOpen = true
                    },
                    onToggleWorkspaceExpanded = { workspaceId ->
                        expandedWorkspaceIds = if (workspaceId in expandedWorkspaceIds) {
                            expandedWorkspaceIds - workspaceId
                        } else {
                            expandedWorkspaceIds + workspaceId
                        }
                    },
                    onToggleWorkspacePinned = { workspaceId ->
                        val pinned = workspaceId in uiPreferences.pinnedWorkspaceIds
                        scope.launch { graph.appPreferences.setWorkspacePinned(workspaceId, !pinned) }
                    },
                    onNewSessionInWorkspace = { workspaceId ->
                        drawerOpen = false
                        chatViewModel.newSession(workspaceId)
                    },
                    onWorkspaceSettings = { workspaceId ->
                        drawerOpen = false
                        navigator.push(AppDestination.WorkspaceSettings(workspaceId))
                    },
                    onRenameWorkspace = { workspaceId ->
                        drawerOpen = false
                        workspaceRenameTarget = state.workspaces.firstOrNull { it.id == workspaceId }
                    },
                    onDeleteWorkspace = { workspaceId ->
                        drawerOpen = false
                        val target = state.workspaces.firstOrNull { it.id == workspaceId }
                        if (target != null) {
                            workspaceDeleteTarget = target
                            scope.launch {
                                workspaceDeleteCount = chatViewModel.loadWorkspaceSessionCount(workspaceId)
                            }
                        }
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
        if (workspaceCreateOpen) {
            WorkspaceNameDialog(
                mode = WorkspaceNameMode.CREATE,
                existingNames = state.workspaces.map { it.name },
                onConfirm = { name ->
                    workspaceCreateOpen = false
                    chatViewModel.createWorkspace(name)
                },
                onDismiss = { workspaceCreateOpen = false },
            )
        }
        workspaceRenameTarget?.let { target ->
            WorkspaceNameDialog(
                mode = WorkspaceNameMode.RENAME,
                initial = target.name,
                existingNames = state.workspaces.filterNot { it.id == target.id }.map { it.name },
                onConfirm = { name ->
                    workspaceRenameTarget = null
                    chatViewModel.renameWorkspace(target.id, name)
                },
                onDismiss = { workspaceRenameTarget = null },
            )
        }
        workspaceDeleteTarget?.let { target ->
            DeleteWorkspaceDialog(
                name = target.name,
                sessionCount = workspaceDeleteCount,
                onConfirm = {
                    workspaceDeleteTarget = null
                    chatViewModel.deleteWorkspace(target.id)
                },
                onDismiss = { workspaceDeleteTarget = null },
            )
        }
        previewAttachment?.let { attachment ->
            val root = previewRoot ?: attachmentRoot
            AttachmentPreviewDialog(
                attachment = attachment,
                file = graph.attachmentStore.file(attachment, root),
                onDismiss = {
                    previewAttachment = null
                    previewRoot = null
                },
                onShare = {
                    val intent = graph.attachmentStore.shareIntent(attachment, root)
                    if (intent != null) {
                        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                    }
                },
            )
        }
        if (usageSheetOpen) {
            state.usage?.let { usage ->
                UsageSheet(usage = usage, onDismiss = { usageSheetOpen = false })
            }
        }
        if (goalSheetOpen) {
            GoalSheet(
                view = activeGoal,
                onDismiss = { goalSheetOpen = false },
                onCreate = { objective ->
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.create(id, objective)
                            goalSheetOpen = false
                        }
                    }
                },
                onEdit = { objective ->
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.edit(id, objective)
                            goalSheetOpen = false
                        }
                    }
                },
                onPause = {
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.pause(id)
                            goalSheetOpen = false
                        }
                    }
                },
                onResume = {
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.resume(id)
                            goalSheetOpen = false
                        }
                    }
                },
                onComplete = {
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.complete(id)
                            goalSheetOpen = false
                        }
                    }
                },
                onClear = {
                    state.conversationId?.let { id ->
                        scope.launch {
                            graph.goalService.clear(id)
                            goalSheetOpen = false
                        }
                    }
                },
            )
        }
        LaunchedEffect(jobsSheetOpen, selectedJobId) {
            val id = selectedJobId
            if (!jobsSheetOpen || id == null) return@LaunchedEffect
            while (true) {
                val job = graph.jobRegistry.jobs.value.firstOrNull { it.id == id } ?: break
                jobOutput = runCatching { graph.jobRegistry.read(id).output }.getOrNull()
                if (job.status != JobStatus.RUNNING) break
                delay(800)
            }
        }
        if (jobsSheetOpen) {
            JobsSheet(
                jobs = activeJobs,
                selectedJobId = selectedJobId,
                output = jobOutput,
                onSelect = { id ->
                    if (selectedJobId == id) {
                        selectedJobId = null
                        jobOutput = null
                    } else {
                        selectedJobId = id
                        jobOutput = null
                        scope.launch { jobOutput = graph.jobRegistry.read(id).output }
                    }
                },
                onCloseOutput = {
                    selectedJobId = null
                    jobOutput = null
                },
                onKill = { id -> scope.launch { graph.jobRegistry.kill(id, null) } },
                onClearSettled = { graph.jobRegistry.clearSettled() },
                onDismiss = { jobsSheetOpen = false },
            )
        }
        if (filesSheetOpen) {
            val rootLabel = state.workspaces
                .firstOrNull { it.cwd == state.conversationCwd }
                ?.name
                ?: attachmentRoot.name
            FileBrowserSheet(
                root = attachmentRoot,
                rootLabel = rootLabel,
                onOpenFile = openWorkspaceFile,
                onDismiss = { filesSheetOpen = false },
            )
        }
    }
    }
}
