package io.github.whywood.deepdeep

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.core.resolveAttachmentRoot
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.SharePayload
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.plugins.PluginSetupSheet
import io.github.whywood.deepdeep.ui.chat.ChatScreen
import io.github.whywood.deepdeep.ui.chat.ChatScreenActions
import io.github.whywood.deepdeep.ui.chat.ChatScreenOverlay
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.chat.CommandItem
import io.github.whywood.deepdeep.ui.navigation.AnimatedScreenHost
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppRouteContent
import io.github.whywood.deepdeep.ui.navigation.deepLinkStack
import io.github.whywood.deepdeep.ui.shell.OverlayHost
import io.github.whywood.deepdeep.ui.shell.ShellCommandAction
import io.github.whywood.deepdeep.ui.shell.ShellCommandExecutor
import io.github.whywood.deepdeep.ui.shell.ShellIntakeActions
import io.github.whywood.deepdeep.ui.shell.ShellIntakeChatState
import io.github.whywood.deepdeep.ui.shell.ShellIntakeHost
import io.github.whywood.deepdeep.ui.shell.ShellSubmitPlan
import io.github.whywood.deepdeep.ui.shell.buildCommandItems
import io.github.whywood.deepdeep.ui.shell.commandInsertion
import io.github.whywood.deepdeep.ui.shell.planSubmission
import io.github.whywood.deepdeep.ui.shell.rememberShellChatUiStrings
import io.github.whywood.deepdeep.ui.shell.ShellCommandPalette
import io.github.whywood.deepdeep.ui.shell.ShellNavigationBackHandler
import io.github.whywood.deepdeep.ui.shell.ShellNavigationChrome
import io.github.whywood.deepdeep.ui.shell.rememberShellChatViewModel
import io.github.whywood.deepdeep.ui.shell.ShellChatLifecycle
import io.github.whywood.deepdeep.ui.shell.ShellDrawerActions
import io.github.whywood.deepdeep.ui.shell.ShellDrawerState
import io.github.whywood.deepdeep.ui.shell.ShellOverlay
import io.github.whywood.deepdeep.ui.shell.ShellOverlayActions
import io.github.whywood.deepdeep.ui.shell.ShellOverlayState
import io.github.whywood.deepdeep.ui.shell.rememberFrozenChatState
import io.github.whywood.deepdeep.ui.shell.shareChooser
import io.github.whywood.deepdeep.ui.navigation.rememberAppNavigator
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeepDeepApp(
    graph: AppDependencies,
    theme: ThemeMode,
    fontStyle: DdFontStyle = DdFontStyle.LORA,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    pendingDeepDestination: AppDestination? = null,
    onDeepDestinationConsumed: () -> Unit = {},
    pendingShare: SharePayload? = null,
    onShareConsumed: () -> Unit = {},
    openVoiceAssistant: Boolean = false,
    onOpenVoiceAssistantConsumed: () -> Unit = {},
    onActiveSessionChanged: (String?) -> Unit = {},
    onFontStyleChange: (DdFontStyle) -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reloadDoneMessage = stringResource(R.string.chat_reload_done)
    val sessionMissingMessage = stringResource(R.string.chat_error_session_missing)
    val shareImportTimeoutMessage = stringResource(R.string.chat_share_import_timeout)
    val chatUiStrings = rememberShellChatUiStrings()
    val chatViewModel: ChatViewModel = rememberShellChatViewModel(graph, chatUiStrings)
    val stateHolder = chatViewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder
    val availableSkills by chatViewModel.availableSkills.collectAsStateWithLifecycle()
    val attachmentRoot = remember(state.conversationCwd) {
        resolveAttachmentRoot(state.conversationCwd, graph.homeDir)
    }
    var previewRoot by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    ShellChatLifecycle(chatViewModel)
    LaunchedEffect(state.conversationId) {
        onActiveSessionChanged(state.conversationId)
    }
    val shareChooserTitle = stringResource(R.string.assistant_attach_share)
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(chatViewModel::attach)
    }
    var previewAttachment by remember { mutableStateOf<AttachmentRef?>(null) }
    val authKinds by graph.credentials.authKinds.collectAsStateWithLifecycle(initialValue = emptyMap())
    val credentialRevision by graph.credentials.revision.collectAsStateWithLifecycle(initialValue = 0L)
    val configuredProviders = authKinds.keys
    val configuredProviderList = remember(graph.providerCatalog.summaries, configuredProviders) {
        graph.providerCatalog.summaries.filter { it.id in configuredProviders }
    }
    val defaultModelConfig by graph.modelConfigStore.config.collectAsStateWithLifecycle(initialValue = ModelConfig())
    val defaultProvider = graph.providerCatalog.summary(defaultModelConfig.providerId)
    val defaultModelLabel = graph.providerCatalog.models(defaultModelConfig.providerId)
        .firstOrNull { it.id == defaultModelConfig.modelId }
        ?.displayName
        ?: defaultModelConfig.modelId
    val schedules by graph.scheduleService.schedules.collectAsStateWithLifecycle()
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by graph.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val sharedStorageEnabled by graph.linuxSettings.sharedStorageEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val devPackages by graph.devPackageManager.state.collectAsStateWithLifecycle()
    val pluginsState by graph.pluginManager.state.collectAsStateWithLifecycle()
    val voiceAutoSpeak by graph.voicePreferences.autoSpeak.collectAsStateWithLifecycle(initialValue = false)
    val voiceSendAfterRecognition by graph.voicePreferences.sendAfterRecognition.collectAsStateWithLifecycle(
        initialValue = false,
    )
    var voiceSheetOpen by rememberSaveable { mutableStateOf(false) }
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
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val uiPreferences by graph.appPreferences.preferences.collectAsStateWithLifecycle(initialValue = UiPreferences())
    val pendingQuestions by graph.questionCenter.pending.collectAsStateWithLifecycle()
    val activeQuestions = pendingQuestions.filter { it.sessionId == state.conversationId }
    val foreignQuestion = pendingQuestions.firstOrNull { it.sessionId != state.conversationId }
    val goals by graph.goalService.goals.collectAsStateWithLifecycle()
    val activeGoal = state.conversationId?.let { goals[it] }?.takeIf { it.goal.phase != io.github.whywood.deepdeep.runtime.GoalPhase.COMPLETE }
    val allJobs by graph.jobRegistry.jobs.collectAsStateWithLifecycle()
    val activeJobs = remember(allJobs, state.conversationId) {
        allJobs.filter { it.sessionId == state.conversationId }
    }
    var modelsOpen by remember { mutableStateOf(false) }
    var usageSheetOpen by remember { mutableStateOf(false) }
    var jobsSheetOpen by remember { mutableStateOf(false) }
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
    var workspaceCreateOpen by rememberSaveable { mutableStateOf(false) }
    var workspaceRenameId by rememberSaveable { mutableStateOf<String?>(null) }
    val workspaceRenameTarget = workspaceRenameId?.let { id ->
        state.workspaces.firstOrNull { it.id == id }
    }
    var workspaceDeleteTarget by remember { mutableStateOf<RuntimeWorkspace?>(null) }
    var workspaceDeleteCount by remember { mutableIntStateOf(0) }
    val appResources = LocalResources.current
    val intakeChat = ShellIntakeChatState(
        ready = state.ready,
        historyLoaded = state.historyLoaded,
        sending = state.sending,
        switchingConversation = state.switchingConversation,
        conversationId = state.conversationId,
        conversationCwd = state.conversationCwd,
        conversations = state.conversations,
        archivedConversations = state.archivedConversations,
        archivedCount = state.archivedCount,
    )
    ShellIntakeHost(
        openSessionId = openSessionId,
        pendingShare = pendingShare,
        pendingDeepDestination = pendingDeepDestination,
        openVoiceAssistant = openVoiceAssistant,
        chat = intakeChat,
        chatSnapshot = {
            val latest = chatViewModel.state.value
            ShellIntakeChatState(
                latest.ready, latest.historyLoaded, latest.sending, latest.switchingConversation,
                latest.conversationId, latest.conversationCwd, latest.conversations,
                latest.archivedConversations, latest.archivedCount,
            )
        },
        actions = ShellIntakeActions(
            onOpenSession = { sessionId ->
                drawerOpen = false
                navigator.reset(AppDestination.Chat)
                chatViewModel.openConversation(sessionId)
                onOpenSessionConsumed()
            },
            onSessionMissing = {
                Toast.makeText(context, sessionMissingMessage, Toast.LENGTH_SHORT).show()
                onOpenSessionConsumed()
            },
            onRefreshSessions = chatViewModel::requestSessionsRefresh,
            onApplyDeepLink = { destination ->
                drawerOpen = false
                navigator.reset(AppDestination.Chat)
                deepLinkStack(destination).forEach(navigator::push)
                onDeepDestinationConsumed()
            },
            onShareStaged = { payload ->
                val staged = withContext(Dispatchers.IO) { graph.shareStaging.stage(payload.streams) }
                staged.files.forEach { stagedFile ->
                    graph.shareStaging.shareUri(stagedFile.file)?.let(chatViewModel::attach)
                }
                if (payload.text.isNotBlank()) chatViewModel.onInputChange(payload.text)
                staged.failures.forEach { failure ->
                    Toast.makeText(context, graph.shareStaging.messageFor(failure), Toast.LENGTH_SHORT).show()
                }
                if (payload.streams.isEmpty() && payload.text.isBlank()) {
                    Toast.makeText(context, R.string.share_error_no_content, Toast.LENGTH_SHORT).show()
                }
                navigator.reset(AppDestination.Chat)
                onShareConsumed()
            },
            onShareTimeout = {
                Toast.makeText(context, shareImportTimeoutMessage, Toast.LENGTH_SHORT).show()
                onShareConsumed()
            },
            onOpenVoiceAssistant = {
                voiceSheetOpen = true
                onOpenVoiceAssistantConsumed()
            },
        ),
    )
    LaunchedEffect(state.ready, state.conversationId, state.conversationCwd) {
        if (state.ready) chatViewModel.refreshAvailableSkills()
    }
    ShellCommandPalette(input = state.input) { chatViewModel.refreshAvailableSkills() }

    val compactTitle = stringResource(R.string.chat_cmd_compact)
    val compactDescription = stringResource(R.string.chat_cmd_compact_desc)
    val goalTitle = stringResource(R.string.chat_cmd_goal)
    val goalDescription = stringResource(R.string.chat_cmd_goal_desc)
    val commandItems = remember(
        state.conversationId,
        state.sending,
        state.compacting,
        availableSkills,
        compactTitle,
        compactDescription,
        goalTitle,
        goalDescription,
    ) {
        buildCommandItems(
            conversationId = state.conversationId,
            sending = state.sending,
            compacting = state.compacting,
            skills = availableSkills,
            compactTitle = compactTitle,
            compactDescription = compactDescription,
            goalTitle = goalTitle,
            goalDescription = goalDescription,
        )
    }
    val commandExecutor = object : ShellCommandExecutor {
        override fun perform(action: ShellCommandAction) {
            val sessionId = chatViewModel.state.value.conversationId
            when (action) {
                ShellCommandAction.Compact -> chatViewModel.compactContext()
                ShellCommandAction.GoalPause,
                ShellCommandAction.GoalResume,
                ShellCommandAction.GoalClear,
                ShellCommandAction.GoalInvalid,
                is ShellCommandAction.GoalCreate,
                -> if (sessionId == null) {
                    chatViewModel.showCommandError(appResources.getString(R.string.chat_error_session_not_ready))
                } else {
                    when (action) {
                        ShellCommandAction.GoalPause -> scope.launch { graph.goalService.pause(sessionId) }
                        ShellCommandAction.GoalResume -> scope.launch { graph.goalService.resume(sessionId) }
                        ShellCommandAction.GoalClear -> scope.launch { graph.goalService.clear(sessionId) }
                        is ShellCommandAction.GoalCreate -> scope.launch {
                            if (graph.goalService.create(sessionId, action.objective) == null) {
                                chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_goal_invalid))
                            }
                        }
                        else -> chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_goal_invalid))
                    }
                }
            }
        }
    }

    fun pickCommand(item: CommandItem) {
        chatViewModel.onInputChange(commandInsertion(state.input, item.insertText))
    }

    fun submitChatInput() {
        when (val plan = planSubmission(state.input, state.sending)) {
            ShellSubmitPlan.Send -> chatViewModel.send()
            ShellSubmitPlan.RejectWhileSending ->
                chatViewModel.showCommandError(appResources.getString(R.string.chat_error_stop_before_command))
            is ShellSubmitPlan.RunCommand -> {
                chatViewModel.onInputChange("")
                commandExecutor.perform(plan.action)
            }
            is ShellSubmitPlan.UnknownCommand ->
                chatViewModel.showCommandError(appResources.getString(R.string.chat_cmd_unknown, plan.name))
        }
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    fun openWorkshopCreate() {
        drawerOpen = false
        chatViewModel.newSession(AgentRuntimeHost.HOME_ID, WORKSHOP_SKILL_COMMAND)
        navigator.reset(AppDestination.Chat)
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

    ShellNavigationBackHandler(navigator = navigator, drawerOpen = drawerOpen, onBack = ::back)
    ShellNavigationChrome(navigator = navigator)

    LaunchedEffect(state.ready, state.currentWorkspaceId) {
        if (state.ready && state.currentWorkspaceId.isNotBlank()) {
            expandedWorkspaceIds = expandedWorkspaceIds + state.currentWorkspaceId
        }
    }

    CompositionLocalProvider(LocalHapticsEnabled provides true) {
    Box(Modifier.fillMaxSize().background(colors.paper)) {
        val reminderCount = remember(schedules, state.conversationId) {
            schedules.count { it.enabled && it.sessionId == state.conversationId }
        }
        val runningJobCount = remember(activeJobs) {
            activeJobs.count { it.status == JobStatus.RUNNING }
        }
        AnimatedScreenHost(
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
            predictiveBackEnabled = !drawerOpen,
            base = { baseFrozen ->
                val frozenState = rememberFrozenChatState(state, baseFrozen)
                ChatScreen(
                    state = remember(frozenState, configuredProviders) {
                                            frozenState.copy(configured = frozenState.providerId in configuredProviders)
                                        },
                    nickname = nickname,
                    providers = configuredProviderList,
                    attachmentRoot = attachmentRoot,
                    actions = ChatScreenActions(
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
                        onModelChange = chatViewModel::setModel,
                        onProviderChange = chatViewModel::setProvider,
                        onThinkingLevelChange = chatViewModel::setThinkingLevel,
                        onToggleStep = chatViewModel::toggleStep,
                        onQuoteMessage = chatViewModel::quote,
                        onClearQuote = chatViewModel::clearQuote,
                        onPickAttachment = { attachmentPicker.launch(arrayOf("*/*")) },
                        onRemoveAttachment = chatViewModel::removeAttachment,
                        onOpenAttachment = {
                                                previewRoot = attachmentRoot
                                                previewAttachment = it
                                            },
                        onOpenFile = openWorkspaceFile,
                        onOpenSchedules = { navigator.push(AppDestination.Schedules) },
                        onRetry = chatViewModel::retry,
                        onPickCommand = ::pickCommand,
                        onPauseGoal = {
                                                state.conversationId?.let { id -> scope.launch { graph.goalService.pause(id) } }
                                            },
                        onResumeGoal = {
                                                state.conversationId?.let { id -> scope.launch { graph.goalService.resume(id) } }
                                            },
                        onOpenJobs = {
                                                selectedJobId = null
                                                jobOutput = null
                                                jobsSheetOpen = true
                                            },
                        onModelsOpenChange = { modelsOpen = it },
                        onOpenUsage = { usageSheetOpen = true },
                        onAnswerQuestion = { pending, answers ->
                                                graph.questionCenter.answer(pending.requestId, answers)
                                            },
                        onOpenForeignQuestion = { sessionId ->
                                                navigator.reset(AppDestination.Chat)
                                                chatViewModel.openConversationById(sessionId)
                                            },
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
                                                        context.startActivity(shareChooser(context, intent, shareChooserTitle))
                                                    } else {
                                                        Toast.makeText(context, presentShareFailed, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                        onOpenWorkshop = { id -> navigator.push(AppDestination.WorkshopDetail(id)) },
                        onOpenFiles = { navigator.push(AppDestination.Files(attachmentRoot.absolutePath)) },
                        onVoiceClick = { voiceSheetOpen = true },
                        onFollowUp = chatViewModel::followUpNow,
                        onClearQueue = chatViewModel::clearQueuedInputs,
                        onScrollAnchorChange = chatViewModel::updateScrollAnchor,
                    ),
                    overlay = ChatScreenOverlay(
                        reminderCount = reminderCount,
                        reloading = frozenState.reloading,
                        compacting = frozenState.compacting,
                        commandItems = commandItems,
                        goal = activeGoal,
                        jobCount = runningJobCount,
                        modelsOpen = modelsOpen,
                        pendingQuestions = activeQuestions,
                        foreignQuestion = foreignQuestion,
                    ),
                    scrollAnchorFor = chatViewModel::scrollAnchorFor,
                    autoCollapseProcess = uiPreferences.autoCollapseProcess,
                    chatFontScale = uiPreferences.chatFontScale,
                )
            },
        ) { destination ->
            AppRouteContent(
                destination, navigator, ::back, ::openChat, ::openWorkshopCreate,
                state, chatViewModel, graph, scope, theme, onThemeChange, fontStyle,
                onFontStyleChange, nickname, defaultProvider?.name, defaultModelLabel,
                defaultModelConfig, configuredProviders, linuxState, linuxEnabled,
                sharedStorageEnabled, devPackages, personalization, uiPreferences,
                voiceAutoSpeak, voiceSendAfterRecognition, authKinds, credentialRevision,
                onOpenFile = openWorkspaceFile,
                onOpenAssistantSettings = { navigator.push(AppDestination.AssistantSettings) },
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
        OverlayHost(
            overlays = ShellOverlayState(
                workspaceCreate = ShellOverlay.WorkspaceCreate(state.workspaces.map { it.name })
                    .takeIf { workspaceCreateOpen },
                workspaceRename = workspaceRenameTarget?.let { target ->
                    ShellOverlay.WorkspaceRename(
                        target.id, target.name, state.workspaces.filterNot { it.id == target.id }.map { it.name },
                    )
                },
                workspaceDelete = workspaceDeleteTarget?.let {
                    ShellOverlay.WorkspaceDelete(it.id, it.name, workspaceDeleteCount)
                },
                attachmentPreview = previewAttachment?.let {
                    ShellOverlay.AttachmentPreview(it, graph.attachmentStore.file(it, previewRoot ?: attachmentRoot))
                },
                usage = state.usage?.takeIf { usageSheetOpen }?.let(ShellOverlay::Usage),
                jobs = ShellOverlay.Jobs(activeJobs, selectedJobId, jobOutput).takeIf { jobsSheetOpen },
                pluginSetup = pluginsState.interaction?.let(ShellOverlay::PluginSetup),
                voiceAssistant = ShellOverlay.VoiceAssistant(
                    state.messages
                        .lastOrNull { it.role == io.github.whywood.deepdeep.runtime.AssistantRole.ASSISTANT }
                        ?.text
                        .orEmpty(),
                    state.sending, voiceAutoSpeak, voiceSendAfterRecognition,
                ).takeIf { voiceSheetOpen },
            ),
            overlayActions = ShellOverlayActions(
                onConfirmWorkspaceCreate = { name -> workspaceCreateOpen = false; chatViewModel.createWorkspace(name) },
                onDismissWorkspaceCreate = { workspaceCreateOpen = false },
                onConfirmWorkspaceRename = { name ->
                    val id = workspaceRenameId
                    workspaceRenameId = null
                    id?.let { chatViewModel.renameWorkspace(it, name) }
                },
                onDismissWorkspaceRename = { workspaceRenameId = null },
                onConfirmWorkspaceDelete = {
                    val target = workspaceDeleteTarget
                    workspaceDeleteTarget = null
                    target?.let { chatViewModel.deleteWorkspace(it.id) }
                },
                onDismissWorkspaceDelete = { workspaceDeleteTarget = null },
                onDismissAttachmentPreview = { previewAttachment = null; previewRoot = null },
                onShareAttachmentPreview = {
                    previewAttachment?.let { attachment ->
                        graph.attachmentStore.shareIntent(attachment, previewRoot ?: attachmentRoot)
                    }?.let { context.startActivity(shareChooser(context, it, shareChooserTitle)) }
                },
                onDismissUsage = { usageSheetOpen = false },
                onSelectJob = { id ->
                    val same = selectedJobId == id
                    selectedJobId = if (same) null else id
                    jobOutput = null
                    if (!same) scope.launch { jobOutput = graph.jobRegistry.read(id).output }
                },
                onCloseJobOutput = { selectedJobId = null; jobOutput = null },
                onKillJob = { id -> scope.launch { graph.jobRegistry.kill(id, null) } },
                onClearSettledJobs = { graph.jobRegistry.clearSettled() },
                onDismissJobs = { jobsSheetOpen = false },
                onFillVoice = chatViewModel::onInputChange,
                onSendVoice = { text -> chatViewModel.onInputChange(text); chatViewModel.send() },
                onDismissVoice = { voiceSheetOpen = false },
            ),
            drawer = ShellDrawerState(
                drawerOpen,
                state.conversations,
                state.workspaces,
                uiPreferences.pinnedWorkspaceIds,
                expandedWorkspaceIds,
                state.busySessionIds,
                schedules.filter { it.enabled }.mapTo(mutableSetOf()) { it.sessionId },
                nickname,
            ),
            drawerActions = ShellDrawerActions(
                onNavigate = { destination -> drawerOpen = false; navigator.push(destination) },
                onDismiss = { drawerOpen = false },
                onCreateWorkspace = { drawerOpen = false; workspaceCreateOpen = true },
                onToggleWorkspaceExpanded = { id ->
                    expandedWorkspaceIds = if (id in expandedWorkspaceIds) expandedWorkspaceIds - id else expandedWorkspaceIds + id
                },
                onToggleWorkspacePinned = { id ->
                    val pinned = id in uiPreferences.pinnedWorkspaceIds
                    scope.launch { graph.appPreferences.setWorkspacePinned(id, !pinned) }
                },
                onNewSessionInWorkspace = { id -> drawerOpen = false; chatViewModel.newSession(id) },
                onWorkspaceSettings = { id ->
                    drawerOpen = false
                    navigator.push(AppDestination.WorkspaceSettings(id))
                },
                onRenameWorkspace = { id -> drawerOpen = false; workspaceRenameId = id },
                onDeleteWorkspace = { id ->
                    drawerOpen = false
                    state.workspaces.firstOrNull { it.id == id }?.let {
                        workspaceDeleteTarget = it
                        scope.launch { workspaceDeleteCount = chatViewModel.loadWorkspaceSessionCount(id) }
                    }
                },
                onConversation = { id -> drawerOpen = false; chatViewModel.openConversation(id) },
                onRename = chatViewModel::renameSession,
                onSetPinned = chatViewModel::setSessionPinned,
                onSetArchived = chatViewModel::setSessionArchived,
                onDelete = chatViewModel::deleteSession,
                onSettings = ::openSettings,
            ),
            pluginSetupContent = { interaction -> PluginSetupSheet(graph.pluginManager, interaction) },
        )
    }
    }
}

private const val WORKSHOP_SKILL_COMMAND = "/skill:workshop "
