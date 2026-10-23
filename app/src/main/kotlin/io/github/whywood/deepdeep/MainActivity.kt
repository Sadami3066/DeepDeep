package io.github.whywood.deepdeep

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private val pendingSessionId = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSessionId.value = if (savedInstanceState == null) {
            intent?.getStringExtra(NotificationCenter.EXTRA_SESSION_ID)
        } else {
            null
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
        pendingSessionId.value = intent.getStringExtra(NotificationCenter.EXTRA_SESSION_ID)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
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
}
