package io.github.whywood.deepdeep.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AssistantEntry
import io.github.whywood.deepdeep.service.Diagnostics
import io.github.whywood.deepdeep.core.resolveAttachmentRoot
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemePreset
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.ui.common.chat.AttachmentSource
import io.github.whywood.deepdeep.ui.common.chat.AttachmentSourceSheet
import io.github.whywood.deepdeep.ui.common.chat.CameraCaptureSheet
import io.github.whywood.deepdeep.ui.common.chat.rememberAttachmentPickers
import io.github.whywood.deepdeep.ui.common.shareChooser
import io.github.whywood.deepdeep.ui.common.ProvideHostFiles
import io.github.whywood.deepdeep.ui.common.chat.ProvidePluginManager
import io.github.whywood.deepdeep.ui.shell.rememberShellConversationStrings
import kotlinx.coroutines.launch



class AssistantOverlayActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private val draftStore by lazy {
        OverlayDraftStore(PreferencesOverlayDraftPersistence(this))
    }
    private var controller: AssistantOverlayController? = null
    private var requestedSessionId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AgentService.start(this)
        requestedSessionId = savedInstanceState?.getString(STATE_SESSION_ID)
            ?: intent.requestedSessionId()
        requestedSessionId?.let { graph.activeSessionId.value = it }
        val language = LanguagePreferences(this).read()
        val fontStyle = graph.fontPreferences.read()
        setContent {
            val chatUiStrings = rememberShellConversationStrings()
            val controller = remember {
                AssistantOverlayController(
                    runtime = graph.host,
                    store = graph.conversationStore,
                    activeSessionId = { graph.activeSessionId.value },
                    homeWorkspaceId = AgentRuntimeHost.HOME_ID,
                    scope = graph.appScope,
                    providers = graph.providerCatalog,
                    attachmentStore = graph.attachmentStore,
                    homeDir = graph.homeDir,
                    configuredProviders = { graph.credentials.authKinds.value.keys },
                    defaultModel = { graph.modelConfigStore.read() },
                    onSessionSelected = { sessionId -> graph.activeSessionId.value = sessionId },
                    initialSessionId = { requestedSessionId },
                )
            }
            this.controller = controller
            LaunchedEffect(controller) {
                runCatching { graph.processRuntime.start() }.onFailure {
                    Diagnostics.log(this@AssistantOverlayActivity, "overlay: process start failed", it)
                }
                controller.start()
            }
            val preset by graph.themePreferences.preset.collectAsStateWithLifecycle(
                initialValue = ThemePreset.DEEP_DEEP,
            )
            val autoSpeak by graph.voicePreferences.autoSpeak.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val overlayEntryMode by graph.overlayEntryPreferences.mode.collectAsStateWithLifecycle(
                initialValue = OverlayEntryMode.VOICE,
            )
            val uiPreferences by graph.appPreferences.preferences.collectAsStateWithLifecycle(
                initialValue = UiPreferences(),
            )
            val state by controller.state.collectAsStateWithLifecycle()
            val pendingQuestions by graph.questionCenter.pending.collectAsStateWithLifecycle()
            val sessionQuestions = remember(pendingQuestions, state.sessionId) {
                pendingQuestions.filter { it.sessionId == state.sessionId }
            }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val missingFileMessage = stringResource(R.string.assistant_attach_missing)
            val shareFailedMessage = stringResource(R.string.present_share_failed)
            val shareTitle = stringResource(R.string.assistant_attach_share)
            var preview by remember { mutableStateOf<AssistantOverlayPreview?>(null) }
            var attachSheetOpen by remember { mutableStateOf(false) }
            var cameraSheetOpen by remember { mutableStateOf(false) }
            val attachmentPickers = rememberAttachmentPickers { uri -> controller.attach(uri) }
            val attachmentRoot = remember(state.conversationCwd) {
                resolveAttachmentRoot(state.conversationCwd, graph.homeDir)
            }

            val openFile: (String) -> Unit = { raw ->
                scope.launch {
                    val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                    if (resolved == null) {
                        Toast.makeText(context, missingFileMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        preview = AssistantOverlayPreview(
                            attachment = resolved,
                            file = graph.attachmentStore.file(resolved, attachmentRoot),
                        )
                    }
                }
            }
            val shareFile: (String) -> Unit = { raw ->
                scope.launch {
                    val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                    if (resolved == null) {
                        Toast.makeText(context, missingFileMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = graph.attachmentStore.shareIntent(resolved, attachmentRoot)
                        if (intent != null) {
                            context.startActivity(shareChooser(context, intent, shareTitle))
                        } else {
                            Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            DeepDeepTheme(preset = preset, fontStyle = fontStyle) {
                ProvidePluginManager(graph.pluginManager) {
                    ProvideHostFiles(graph.hostFiles) {
                AssistantOverlayScreen(
                    state = state,
                    pendingQuestions = sessionQuestions,
                    preview = preview,
                    draftStore = draftStore,
                    config = AssistantOverlayConfig(
                        autoSpeak = autoSpeak,
                        entryMode = overlayEntryMode,
                        autoCollapseProcess = uiPreferences.autoCollapseProcess,
                        chatFontScale = uiPreferences.chatFontScale,
                        language = language,
                        attachmentRoot = attachmentRoot,
                    ),
                    actions = AssistantOverlayActions(
                        onDismiss = { finish() },
                        onOpenInApp = ::openInApp,
                        onSend = controller::send,
                        onStop = controller::stop,
                        onRetry = controller::retry,
                        onToggleStep = controller::toggleStep,
                        onAnswerQuestion = { pending, answers ->
                            graph.questionCenter.answer(pending.requestId, answers)
                        },
                        onOpenPreview = { attachment ->
                            preview = AssistantOverlayPreview(
                                attachment = attachment,
                                file = graph.attachmentStore.file(attachment, attachmentRoot),
                            )
                        },
                        onPickAttachment = { attachSheetOpen = true },
                        onOpenCamera = { cameraSheetOpen = true },
                        onRemoveAttachment = controller::removeAttachment,
                        onOpenFile = openFile,
                        onShareFile = shareFile,
                        onOpenMiniapp = { state.sessionId?.let(::openInApp) },
                        onOpenSession = controller::openSession,
                        onNewSessionInWorkspace = { workspaceId -> controller.newSession(workspaceId) },
                        onRefreshLists = controller::refreshLists,
                        onSelectProvider = controller::selectProvider,
                        onSelectModel = controller::selectModel,
                        onSelectThinking = controller::selectThinking,
                        onClearQueue = controller::clearQueue,
                        onRemoveQueued = controller::removeQueued,
                        onSendQueued = controller::sendQueued,
                        onRetryStart = controller::start,
                        onDismissPreview = { preview = null },
                        onSharePreview = {
                            scope.launch {
                                preview?.let { current ->
                                    val intent = graph.attachmentStore.shareIntent(current.attachment, attachmentRoot)
                                    if (intent != null) {
                                        context.startActivity(shareChooser(context, intent, shareTitle))
                                    } else {
                                        Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    ),
                )
                if (attachSheetOpen) {
                    AttachmentSourceSheet(
                        onDismiss = { attachSheetOpen = false },
                        onPick = { source ->
                            attachSheetOpen = false
                            when (source) {
                                AttachmentSource.IMAGE -> attachmentPickers.pickImages()
                                AttachmentSource.FILE -> attachmentPickers.pickFiles()
                                AttachmentSource.CAMERA -> cameraSheetOpen = true
                            }
                        },
                    )
                }
                if (cameraSheetOpen) {
                    CameraCaptureSheet(
                        onDismiss = { cameraSheetOpen = false },
                        onCaptured = { uri ->
                            cameraSheetOpen = false
                            controller.attach(uri)
                        },
                    )
                }
                }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sessionId = intent.requestedSessionId() ?: return
        requestedSessionId = sessionId
        graph.activeSessionId.value = sessionId
        controller?.openSession(sessionId)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        controller?.state?.value?.sessionId?.let { outState.putString(STATE_SESSION_ID, it) }
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        super.onStop()
    }

    override fun onDestroy() {
        controller?.reset()
        controller = null
        super.onDestroy()
    }

    private fun openInApp(sessionId: String?) {
        val target = sessionId ?: return
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(NotificationCenter.EXTRA_SESSION_ID, target),
        )
        finish()
    }

    private companion object {
        const val STATE_SESSION_ID = "io.github.whywood.deepdeep.extra.STATE_SESSION_ID"
    }
}

private fun Intent.requestedSessionId(): String? =
    getStringExtra(AssistantEntry.EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
