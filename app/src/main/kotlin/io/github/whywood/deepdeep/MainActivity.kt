package io.github.whywood.deepdeep

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.MAX_SHARE_STREAMS
import io.github.whywood.deepdeep.data.SharePayload
import io.github.whywood.deepdeep.data.parseSharePayload
import io.github.whywood.deepdeep.deeplink.DeepLink
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private val pendingSessionId = mutableStateOf<String?>(null)
    private val pendingDeepDestination = mutableStateOf<AppDestination?>(null)
    private val pendingShare = mutableStateOf<SharePayload?>(null)
    private val openVoiceAssistant = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIntent(intent)
        } else {
            restoreIntake(savedInstanceState)
        }
        startAgentService()
        requestNotificationPermission()
        val fontStyle = graph.fontPreferences.read()
        setContent {
            val theme by graph.themePreferences.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()
            DeepDeepTheme(theme, fontStyle) {
                DeepDeepApp(
                    graph = graph,
                    theme = theme,
                    fontStyle = fontStyle,
                    openSessionId = pendingSessionId.value,
                    onOpenSessionConsumed = { pendingSessionId.value = null },
                    pendingDeepDestination = pendingDeepDestination.value,
                    onDeepDestinationConsumed = { pendingDeepDestination.value = null },
                    pendingShare = pendingShare.value,
                    onShareConsumed = { pendingShare.value = null },
                    openVoiceAssistant = openVoiceAssistant.value,
                    onOpenVoiceAssistantConsumed = { openVoiceAssistant.value = false },
                    onActiveSessionChanged = { graph.activeSessionId.value = it },
                    onFontStyleChange = { style ->
                        graph.fontPreferences.save(style)
                        recreate()
                    },
                    onThemeChange = { mode -> scope.launch { graph.themePreferences.setTheme(mode) } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingShare.value?.let { payload ->
            outState.putStringArrayList(EXTRA_SHARE_STREAMS, ArrayList(payload.streams))
            outState.putString(EXTRA_SHARE_TEXT, payload.text)
            outState.putString(EXTRA_SHARE_TITLE, payload.title)
        }
        outState.putBoolean(EXTRA_OPEN_VOICE, openVoiceAssistant.value)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
        AppVisibility.taskId = taskId
        graph.notificationCenter.clearQuestionNotifications()
        graph.pluginManager.onAppForeground()
        graph.pluginManager.refreshIfStale(PLUGIN_REFRESH_MIN_INTERVAL_MS)
        lifecycleScope.launch { runCatching { graph.skillRepository.syncIfChanged() } }
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        graph.notificationCenter.refreshQuestionNotifications()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW) {
            when (val link = DeepLink.parse(intent.dataString.orEmpty())) {
                is DeepLink.Session -> pendingSessionId.value = link.sessionId
                is DeepLink.Page -> pendingDeepDestination.value = link.destination
                null -> Unit
            }
            return
        }
        pendingSessionId.value = intent.getStringExtra(NotificationCenter.EXTRA_SESSION_ID)
        if (intent.action == Intent.ACTION_ASSIST) {
            openVoiceAssistant.value = true
            return
        }
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val streams = if (action == Intent.ACTION_SEND) {
            listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
            )
        } else {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                .orEmpty()
                .map(Uri::toString)
        }
        val clipStreams = intent.clipData?.let { clip ->
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri?.toString() }
        }.orEmpty()
        if (streams.size + clipStreams.size > MAX_SHARE_STREAMS) {
            Toast.makeText(this, R.string.share_error_too_many, Toast.LENGTH_SHORT).show()
        }
        val payload = parseSharePayload(
            action = action,
            type = intent.type,
            streamUris = streams,
            clipUris = clipStreams,
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
            title = intent.getStringExtra(Intent.EXTRA_TITLE),
        )
        if (payload.isEmpty) {
            Toast.makeText(this, R.string.share_error_no_content, Toast.LENGTH_SHORT).show()
        } else {
            pendingShare.value = payload
        }
    }

    private fun restoreIntake(savedInstanceState: Bundle) {
        openVoiceAssistant.value = savedInstanceState.getBoolean(EXTRA_OPEN_VOICE, false)
        val streams = savedInstanceState.getStringArrayList(EXTRA_SHARE_STREAMS).orEmpty()
        val text = savedInstanceState.getString(EXTRA_SHARE_TEXT).orEmpty()
        val title = savedInstanceState.getString(EXTRA_SHARE_TITLE).orEmpty()
        val payload = parseSharePayload(
            action = Intent.ACTION_SEND_MULTIPLE,
            type = null,
            streamUris = streams,
            text = text,
            title = title,
        )
        if (!payload.isEmpty) pendingShare.value = payload
    }

    private fun startAgentService() {
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private companion object {
        const val EXTRA_SHARE_STREAMS = "io.github.whywood.deepdeep.extra.SHARE_STREAMS"
        const val EXTRA_SHARE_TEXT = "io.github.whywood.deepdeep.extra.SHARE_TEXT"
        const val EXTRA_SHARE_TITLE = "io.github.whywood.deepdeep.extra.SHARE_TITLE"
        const val EXTRA_OPEN_VOICE = "io.github.whywood.deepdeep.extra.OPEN_VOICE"
    }
}
