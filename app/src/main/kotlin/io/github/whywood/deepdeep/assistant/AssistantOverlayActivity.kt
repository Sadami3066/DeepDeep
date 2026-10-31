package io.github.whywood.deepdeep.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.ui.shell.rememberShellChatUiStrings

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
            val pendingForSession = pendingQuestions.any { it.sessionId == state.sessionId }
            var modelLabel by remember { mutableStateOf("") }
            LaunchedEffect(state.sessionId) {
                val sessionId = state.sessionId ?: return@LaunchedEffect
                val snapshot = runCatching { graph.host.sessionModel(sessionId) }.getOrNull()
                modelLabel = snapshot?.let { model ->
                    graph.providerCatalog.models(model.providerId)
                        .firstOrNull { it.id == model.modelId }
                        ?.displayName
                        ?: model.modelId
                }.orEmpty()
            }
            DeepDeepTheme(theme, fontStyle) {
                AssistantOverlayScreen(
                    state = state,
                    chatUiStrings = chatUiStrings,
                    autoSpeak = autoSpeak,
                    sendAfterRecognition = sendAfterRecognition,
                    autoCollapseProcess = uiPreferences.autoCollapseProcess,
                    chatFontScale = uiPreferences.chatFontScale,
                    language = language,
                    attachmentRoot = graph.homeDir,
                    pendingQuestion = pendingForSession,
                    modelLabel = modelLabel,
                    onOpenQuestion = { openInApp(state.sessionId) },
                    onDismiss = { finish() },
                    onOpenInApp = ::openInApp,
                    onAsk = controller::ask,
                    onStop = controller::stop,
                    onRetry = controller::retry,
                    onToggleStep = controller::toggleStep,
                    onRetryStart = controller::start,
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
