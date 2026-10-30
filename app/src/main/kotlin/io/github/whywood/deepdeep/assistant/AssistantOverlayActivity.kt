package io.github.whywood.deepdeep.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.service.NotificationCenter

class AssistantOverlayActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private val restartListening = mutableIntStateOf(0)
    private lateinit var controller: AssistantOverlayController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { window.setBackgroundBlurRadius(24) }
        }
        controller = AssistantOverlayController(
            runtime = graph.host,
            activeSessionId = { graph.activeSessionId.value },
            homeWorkspaceId = AgentRuntimeHost.HOME_ID,
            scope = lifecycleScope,
        )
        lifecycleScope.launch { runCatching { graph.prepare() } }
        controller.attachRunning()
        val language = LanguagePreferences(this).read()
        setContent {
            val theme by graph.themePreferences.theme.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM,
            )
            val autoSpeak by graph.voicePreferences.autoSpeak.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val state by controller.state.collectAsStateWithLifecycle()
            val pendingQuestions by graph.questionCenter.pending.collectAsStateWithLifecycle()
            val pendingForSession = pendingQuestions.any { it.sessionId == state.sessionId }
            DeepDeepTheme(theme, graph.fontPreferences.read()) {
                AssistantOverlayScreen(
                    state = state,
                    autoSpeak = autoSpeak,
                    language = language,
                    restartListening = restartListening.intValue,
                    onDismiss = { finish() },
                    onOpenInApp = { sessionId ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(NotificationCenter.EXTRA_SESSION_ID, sessionId),
                        )
                        finish()
                    },
                    pendingQuestion = pendingForSession,
                    onOpenQuestion = {
                        val sessionId = state.sessionId ?: return@AssistantOverlayScreen
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(NotificationCenter.EXTRA_SESSION_ID, sessionId),
                        )
                        finish()
                    },
                    onAsk = controller::ask,
                    onStop = controller::stop,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        restartListening.intValue += 1
    }

    override fun onDestroy() {
        controller.reset()
        super.onDestroy()
    }
}
