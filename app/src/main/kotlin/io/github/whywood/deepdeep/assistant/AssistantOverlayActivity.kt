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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.resolveAttachmentRoot
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.ui.shell.rememberShellChatUiStrings
import io.github.whywood.deepdeep.ui.shell.shareChooser

class AssistantOverlayActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private var controller: AssistantOverlayController? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AgentService.start(this)
        val language = LanguagePreferences(this).read()
        val fontStyle = graph.fontPreferences.read()
        setContent {
            val chatUiStrings = rememberShellChatUiStrings()
            val controller = remember {
                AssistantOverlayController(
                    runtime = graph.host,
                    activeSessionId = { graph.activeSessionId.value },
                    homeWorkspaceId = AgentRuntimeHost.HOME_ID,
                    scope = graph.appScope,
                    providers = graph.providerCatalog,
                    configuredProviders = { graph.credentials.authKinds.value.keys },
                    defaultModel = { graph.modelConfigStore.read() },
                    onSessionSelected = { sessionId -> graph.activeSessionId.value = sessionId },
                    strings = { chatUiStrings },
                )
            }
            this.controller = controller
            LaunchedEffect(controller) {
                runCatching { graph.prepare() }
                controller.start()
            }
            val theme by graph.themePreferences.theme.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM,
            )
            val autoSpeak by graph.voicePreferences.autoSpeak.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val sendAfterRecognition by graph.voicePreferences.sendAfterRecognition.collectAsStateWithLifecycle(
                initialValue = false,
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
            val missingFileMessage = stringResource(R.string.assistant_attach_missing)
            val openFailedMessage = stringResource(R.string.present_open_failed)
            val shareFailedMessage = stringResource(R.string.present_share_failed)
            val shareTitle = stringResource(R.string.assistant_attach_share)
            var preview by remember { mutableStateOf<AssistantOverlayPreview?>(null) }
            val attachmentRoot = remember(state.conversationCwd) {
                resolveAttachmentRoot(state.conversationCwd, graph.homeDir)
            }

            val openFile: (String) -> Unit = { raw ->
                val resolved = graph.attachmentStore.resolve(raw, attachmentRoot)
                if (resolved == null) {
                    Toast.makeText(context, missingFileMessage, Toast.LENGTH_SHORT).show()
                } else if (resolved.kind != AttachmentKind.OTHER) {
                    preview = AssistantOverlayPreview(
                        attachment = resolved,
                        file = graph.attachmentStore.file(resolved, attachmentRoot),
                    )
                } else {
                    val intent = graph.attachmentStore.viewIntent(resolved, attachmentRoot)
                    val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
                    if (!opened) Toast.makeText(context, openFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
            val shareFile: (String) -> Unit = { raw ->
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

            DeepDeepTheme(theme, fontStyle) {
                AssistantOverlayScreen(
                    state = state,
                    pendingQuestions = sessionQuestions,
                    preview = preview,
                    config = AssistantOverlayConfig(
                        autoSpeak = autoSpeak,
                        sendAfterRecognition = sendAfterRecognition,
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
                        onOpenFile = openFile,
                        onShareFile = shareFile,
                        onOpenWorkshop = { state.sessionId?.let(::openInApp) },
                        onOpenSession = controller::openSession,
                        onNewSessionInWorkspace = { workspaceId -> controller.newSession(workspaceId) },
                        onRefreshLists = controller::refreshLists,
                        onSelectProvider = controller::selectProvider,
                        onSelectModel = controller::selectModel,
                        onSelectThinking = controller::selectThinking,
                        onClearQueue = controller::clearQueue,
                        onRetryStart = controller::start,
                        onDismissPreview = { preview = null },
                        onSharePreview = {
                            preview?.let { current ->
                                val intent = graph.attachmentStore.shareIntent(current.attachment, attachmentRoot)
                                if (intent != null) {
                                    context.startActivity(shareChooser(context, intent, shareTitle))
                                } else {
                                    Toast.makeText(context, shareFailedMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
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
}
