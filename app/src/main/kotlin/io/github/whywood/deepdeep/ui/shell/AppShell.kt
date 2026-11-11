package io.github.whywood.deepdeep.ui.shell

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.core.resolveAttachmentRoot
import io.github.whywood.deepdeep.data.AttachmentExport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.ProfileAvatarStore
import io.github.whywood.deepdeep.data.SharePayload
import io.github.whywood.deepdeep.data.session.SessionExporter
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.exportShareIntent
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.ThemePreset
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.host.services.PluginUiBridge
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.ui.chat.ChatScreen
import io.github.whywood.deepdeep.ui.chat.ChatScreenActions
import io.github.whywood.deepdeep.ui.chat.ChatScreenOverlay
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.chat.CommandItem
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import io.github.whywood.deepdeep.ui.common.ProfileAvatarCache
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.rememberAvatarBitmap
import io.github.whywood.deepdeep.ui.common.shareChooser
import io.github.whywood.deepdeep.ui.sessionIdsWithEnabledTimers
import io.github.whywood.deepdeep.ui.sessionIdsWithRunningJobs
import io.github.whywood.deepdeep.ui.navigation.AnimatedScreenHost
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppRouteContent
import io.github.whywood.deepdeep.ui.navigation.AppRouteHost
import io.github.whywood.deepdeep.ui.navigation.ChatRouteData
import io.github.whywood.deepdeep.ui.navigation.ShellRouteUi
import io.github.whywood.deepdeep.ui.navigation.deepLinkStack
import io.github.whywood.deepdeep.ui.navigation.rememberAppNavigator
import io.github.whywood.deepdeep.ui.shell.ShellCommandAction
import io.github.whywood.deepdeep.ui.shell.ShellCommandExecutor
import io.github.whywood.deepdeep.ui.shell.ShellCommandPalette
import io.github.whywood.deepdeep.ui.shell.ShellDrawerActions
import io.github.whywood.deepdeep.ui.shell.ShellDrawerState
import io.github.whywood.deepdeep.ui.shell.ShellIntakeActions
import io.github.whywood.deepdeep.ui.shell.ShellIntakeChatState
import io.github.whywood.deepdeep.ui.shell.ShellIntakeHost
import io.github.whywood.deepdeep.ui.shell.ShellNavigationBackHandler
import io.github.whywood.deepdeep.ui.shell.ShellNavigationChrome
import io.github.whywood.deepdeep.ui.shell.ShellSubmitPlan
import io.github.whywood.deepdeep.ui.shell.buildCommandItems
import io.github.whywood.deepdeep.ui.shell.commandInsertion
import io.github.whywood.deepdeep.ui.shell.planSubmission
import io.github.whywood.deepdeep.ui.shell.rememberFrozenChatState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



@Composable
internal fun AppShell(
    graph: AppDependencies,
    chatViewModel: ChatViewModel,
    state: ChatUiState,
    themePreset: ThemePreset,
    fontStyle: DdFontStyle = DdFontStyle.FREDOKA,
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    pendingDeepDestination: AppDestination? = null,
    onDeepDestinationConsumed: () -> Unit = {},
    pendingShare: SharePayload? = null,
    onShareConsumed: () -> Unit = {},
    pendingPluginImportUri: String? = null,
    onPluginImportConsumed: () -> Unit = {},
    onFontStyleChange: (DdFontStyle) -> Unit = {},
    onThemePresetChange: (ThemePreset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val overlays = rememberSaveable(saver = AppOverlaysState.Saver) { AppOverlaysState() }
    var pluginImportUri by remember { mutableStateOf<String?>(null) }
    val reloadDoneMessage = stringResource(R.string.chat_reload_done)
    val sessionMissingMessage = stringResource(R.string.chat_error_session_missing)
    val shareImportTimeoutMessage = stringResource(R.string.chat_share_import_timeout)
    val availableSkills by chatViewModel.availableSkills.collectAsStateWithLifecycle()
    val attachmentRoot = remember(state.conversationCwd) {
        resolveAttachmentRoot(state.conversationCwd, graph.homeDir)
    }
    val context = LocalContext.current
    val avatarStore = remember(context) { ProfileAvatarStore(context) }
    val shareChooserTitle = stringResource(R.string.assistant_attach_share)
    val authKinds by graph.credentials.authKinds.collectAsStateWithLifecycle(initialValue = emptyMap())
    val credentialRevision by graph.credentials.revision.collectAsStateWithLifecycle(initialValue = 0L)
    val avatarRevision by ProfileAvatarStore.revision.collectAsStateWithLifecycle(initialValue = 0L)
    val avatarKey = remember(avatarRevision) { "profile:${avatarStore.current()?.lastModified() ?: 0L}" }
    val avatarBitmap by rememberAvatarBitmap(avatarKey) { avatarStore.load() }
    val customProviders by graph.customProviderStore.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val configuredProviders = remember(authKinds, customProviders) {
        graph.configuredProviderIds()
    }
    val configuredProviderList = remember(graph.providerCatalog.summaries, configuredProviders) {
        graph.providerCatalog.summaries.filter { it.id in configuredProviders }
    }
    LaunchedEffect(customProviders) {
        chatViewModel.refreshModelCatalog()
    }
    val storedModelConfig by graph.modelConfigStore.config.collectAsStateWithLifecycle(initialValue = null)
    val defaultModelConfig = storedModelConfig ?: ModelConfig()
    val defaultProvider = graph.providerCatalog.summary(defaultModelConfig.providerId)
    val defaultModelLabel = graph.providerCatalog.models(defaultModelConfig.providerId)
        .firstOrNull { it.id == defaultModelConfig.modelId }
        ?.displayName
        ?: defaultModelConfig.modelId
    LaunchedEffect(configuredProviders, storedModelConfig, state.providerId, state.conversationId, state.ready) {
        val config = storedModelConfig ?: return@LaunchedEffect
        if (!state.ready || state.providerId.isBlank() || configuredProviders.isEmpty()) return@LaunchedEffect
        if (state.providerId in configuredProviders || config.providerId !in configuredProviders) return@LaunchedEffect
        chatViewModel.adoptConfiguredModel(config)
    }
    val timers by graph.timerService.timers.collectAsStateWithLifecycle()
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by graph.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val sharedStorageEnabled by graph.linuxSettings.sharedStorageEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val devPackages by graph.devPackageManager.state.collectAsStateWithLifecycle()
    val voiceAutoSpeak by graph.voicePreferences.autoSpeak.collectAsStateWithLifecycle(initialValue = false)
    val appLanguage = remember(context) { LanguagePreferences(context).read() }
    val colors = deepDeepColors()
    val attachmentMissingMessage = stringResource(R.string.assistant_attach_missing)
    val presentOpenFailed = stringResource(R.string.present_open_failed)
    val presentShareFailed = stringResource(R.string.present_share_failed)
    val filesExportDone = stringResource(R.string.files_export_done)
    val filesExportDonePathFormat = stringResource(R.string.files_export_done_path)
    val openWorkspaceFile: (String) -> Unit = { raw ->
        scope.launch {
            val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
            if (resolved == null) {
                Toast.makeText(context, attachmentMissingMessage, Toast.LENGTH_SHORT).show()
            } else {
                overlays.previewRoot = attachmentRoot
                overlays.previewAttachment = resolved
            }
        }
    }
    val navigator = rememberAppNavigator()
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val pendingPluginPage by PluginUiBridge.pendingPage.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPluginPage) {
        val request = pendingPluginPage ?: return@LaunchedEffect
        drawerOpen = false
        navigator.push(AppDestination.PluginPage(request.pluginId, request.pageId))
        PluginUiBridge.consumePage(request)
    }
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
    val timerSessionIds = remember(timers) { sessionIdsWithEnabledTimers(timers) }
    val jobSessionIds = remember(allJobs) { sessionIdsWithRunningJobs(allJobs) }
    var modelsOpen by remember { mutableStateOf(false) }
    var exportSummary by remember { mutableStateOf<ConversationSummary?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val summary = exportSummary
        exportSummary = null
        if (uri != null && summary != null) {
            scope.launch {
                val ok = runCatching {
                    SessionExporter.write(context.contentResolver, uri, summary.filePath)
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) R.string.assistant_export_done else R.string.assistant_export_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
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
        pendingPluginImportUri = pendingPluginImportUri,
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
            onPluginImport = { uri ->
                drawerOpen = false
                navigator.push(AppDestination.Plugins)
                pluginImportUri = uri
                onPluginImportConsumed()
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
            ShellSubmitPlan.Send -> chatViewModel.send(uiPreferences.sendWhileRunning)
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

    fun openMiniappCreate() {
        drawerOpen = false
        chatViewModel.newSession(AgentRuntimeHost.HOME_ID, MINIAPP_SKILL_COMMAND)
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

    fun openProviders() {
        drawerOpen = false
        navigator.push(AppDestination.Providers)
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
        val reminderCount = remember(timers, state.conversationId) {
            timers.count { it.enabled && it.sessionId == state.conversationId }
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
                    avatar = avatarBitmap,
                    providers = configuredProviderList,
                    attachmentRoot = attachmentRoot,
                    actions = ChatScreenActions(
                        onInputChange = chatViewModel::onInputChange,
                        onSend = ::submitChatInput,
                        onStop = chatViewModel::stop,
                        onOpenDrawer = { drawerOpen = true },
                        onManageProviders = ::openProviders,
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
                        onPickAttachment = { overlays.attachmentSheetOpen = true },
                        onRemoveAttachment = chatViewModel::removeAttachment,
                        onOpenAttachment = {
                                                overlays.previewRoot = attachmentRoot
                                                overlays.previewAttachment = it
                                            },
                        onOpenFile = openWorkspaceFile,
                        onOpenTimers = { navigator.push(AppDestination.Timers) },
                        onRetry = chatViewModel::retry,
                        onPickCommand = ::pickCommand,
                        onPauseGoal = {
                                                state.conversationId?.let { id -> scope.launch { graph.goalService.pause(id) } }
                                            },
                        onResumeGoal = {
                                                state.conversationId?.let { id -> scope.launch { graph.goalService.resume(id) } }
                                            },
                        onOpenJobs = {
                                                overlays.selectedJobId = null
                                                overlays.jobOutput = null
                                                overlays.jobsSheetOpen = true
                                            },
                        onModelsOpenChange = { modelsOpen = it },
                        onOpenUsage = { overlays.usageSheetOpen = true },
                        onAnswerQuestion = { pending, answers ->
                                                graph.questionCenter.answer(pending.requestId, answers)
                                            },
                        onOpenForeignQuestion = { sessionId ->
                                                navigator.reset(AppDestination.Chat)
                                                chatViewModel.openConversationById(sessionId)
                                            },
                        onOpenPresentFile = { raw ->
                                                scope.launch {
                                                    val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                                                    if (resolved == null) {
                                                        Toast.makeText(
                                                            context,
                                                            attachmentMissingMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else if (
                                                        resolved.kind != AttachmentKind.OTHER ||
                                                        resolved.mime.startsWith("video/")
                                                    ) {
                                                        overlays.previewRoot = attachmentRoot
                                                        overlays.previewAttachment = resolved
                                                    } else {
                                                        val intent = graph.attachmentStore.viewIntent(resolved, attachmentRoot)
                                                        val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
                                                        if (!opened) {
                                                            Toast.makeText(context, presentOpenFailed, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                        onSharePresentFile = { raw ->
                                                scope.launch {
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
                                                            val export = graph.attachmentStore.export(resolved, attachmentRoot)
                                                            val exportIntent = export?.let {
                                                                exportShareIntent(context, it, resolved.name, resolved.mime)
                                                            }
                                                            if (export != null && exportIntent != null) {
                                                                val message = if (export is AttachmentExport.SharedStorage) {
                                                                    String.format(filesExportDonePathFormat, export.file.absolutePath)
                                                                } else {
                                                                    filesExportDone
                                                                }
                                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                                context.startActivity(shareChooser(context, exportIntent, shareChooserTitle))
                                                            } else {
                                                                Toast.makeText(context, presentShareFailed, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                        onOpenMiniapp = { id -> navigator.push(AppDestination.MiniappDetail(id)) },
                        onOpenPluginPage = { pluginId, pageId -> navigator.push(AppDestination.PluginPage(pluginId, pageId)) },
                        onOpenFiles = { navigator.push(AppDestination.Files(attachmentRoot.absolutePath)) },
                        onExportSession = {
                            val summary = state.conversations.firstOrNull { it.id == state.conversationId }
                            if (summary == null) {
                                Toast.makeText(context, R.string.assistant_export_failed, Toast.LENGTH_SHORT).show()
                            } else {
                                exportSummary = summary
                                exportLauncher.launch(
                                    SessionExporter.suggestedFileName(summary.title, summary.id),
                                )
                            }
                        },
                        onRemoveQueued = chatViewModel::removeQueuedInput,
                        onSendQueued = chatViewModel::sendQueuedNow,
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
                    processDetail = uiPreferences.processDetail,
                    chatFontScale = uiPreferences.chatFontScale,
                    voiceLanguage = appLanguage,
                )
            },
        ) { destination ->
            AppRouteContent(
                destination = destination,
                host = AppRouteHost(
                    navigator = navigator,
                    dependencies = graph,
                    scope = scope,
                    chatViewModel = chatViewModel,
                    chat = ChatRouteData(
                        conversationId = state.conversationId,
                        workspaces = state.workspaces,
                        archived = state.archivedConversations,
                        archivedCount = state.archivedCount,
                    ),
                    ui = ShellRouteUi(
                        themePreset = themePreset,
                        onThemePresetChange = onThemePresetChange,
                        fontStyle = fontStyle,
                        onFontStyleChange = onFontStyleChange,
                        nickname = nickname,
                        avatar = avatarBitmap,
                        providerName = defaultProvider?.name,
                        modelLabel = defaultModelLabel,
                        modelConfig = defaultModelConfig,
                        configuredProviderIds = configuredProviders,
                    ),
                    onOpenChat = ::openChat,
                    onOpenMiniappCreate = ::openMiniappCreate,
                    onOpenFile = openWorkspaceFile,
                    onOpenAssistantSettings = { navigator.push(AppDestination.AssistantSettings) },
                    pluginImportUri = pluginImportUri,
                    onPluginImportHandled = { pluginImportUri = null },
                ),
            )
        }

        val drawerState = ShellDrawerState(
            drawerOpen,
            state.conversations,
            state.workspaces,
            state.currentWorkspaceId,
            state.conversationId,
            uiPreferences.pinnedWorkspaceIds,
            expandedWorkspaceIds,
            state.busySessionIds,
            timerSessionIds,
            jobSessionIds,
            nickname,
            avatarBitmap,
        )
        val drawerActions = ShellDrawerActions(
            onNavigate = { destination -> drawerOpen = false; navigator.push(destination) },
            onDismiss = { drawerOpen = false },
            onCreateWorkspace = { drawerOpen = false; overlays.workspaceCreateOpen = true },
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
            onRenameWorkspace = { id -> drawerOpen = false; overlays.workspaceRenameId = id },
            onDeleteWorkspace = { id ->
                drawerOpen = false
                state.workspaces.firstOrNull { it.id == id }?.let {
                    overlays.workspaceDeleteTarget = it
                    scope.launch { overlays.workspaceDeleteCount = chatViewModel.loadWorkspaceSessionCount(id) }
                }
            },
            onConversation = { id -> drawerOpen = false; chatViewModel.openConversation(id) },
            onRename = chatViewModel::renameSession,
            onSetPinned = chatViewModel::setSessionPinned,
            onSetArchived = chatViewModel::setSessionArchived,
            onDelete = chatViewModel::deleteSession,
            onSettings = ::openSettings,
        )
        AppOverlays(
            graph = graph,
            chatViewModel = chatViewModel,
            state = state,
            activeJobs = activeJobs,
            attachmentRoot = attachmentRoot,
            drawer = drawerState,
            drawerActions = drawerActions,
            overlays = overlays,
        )

    }
    }
}

private const val MINIAPP_SKILL_COMMAND = "/skill:miniapp "
