package io.github.whywood.deepdeep.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.data.FloatingWindowPreferences
import io.github.whywood.deepdeep.floating.FloatingWindowController
import io.github.whywood.deepdeep.plugin.manager.PluginSetupNotice
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private var floatingWindow: FloatingWindowController? = null
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                scope.launch { graph.timerService.recomputeTargets() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph.notificationCenter.start()
        startForeground(NOTIFICATION_ID, graph.notificationCenter.foregroundNotification())
        graph.notificationCenter.onForegroundStarted()
        registerClockReceiver()
        startFloatingWindow()
        scope.launch {
            ProcessExitLog.record(this@AgentService)
            graph.host.ensureStarted()
            graph.timerService.start()
        }
        scope.launch {
            delay(ACCESSIBILITY_CHECK_DELAY_MS)
            if (!AppVisibility.foreground &&
                GuiAccessibilityState.isEnabled(this@AgentService) &&
                GuiAccessibilityState.service == null
            ) {
                graph.notificationCenter.notifyAccessibilityLost()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_SESSION -> {
                val sessionId = intent.getStringExtra(NotificationCenter.EXTRA_SESSION_ID)
                if (!sessionId.isNullOrBlank()) graph.host.stop(sessionId)
            }

            ACTION_PAIRING_CODE -> {
                val code = intent.getStringExtra(EXTRA_PAIRING_CODE)
                scope.launch { graph.adbBridge.handlePairingReply(code) }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(clockReceiver) }
        floatingWindow?.stop()
        floatingWindow = null
        graph.timerService.stop()
        graph.notificationCenter.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startFloatingWindow() {
        val pluginSetup = graph.pluginManager.state
            .map { state ->
                state.interaction?.let { interaction ->
                    PluginSetupNotice(
                        pluginId = interaction.pluginId,
                        pluginName = state.plugins
                            .firstOrNull { it.id == interaction.pluginId }
                            ?.name
                            ?: interaction.title,
                        stage = interaction.stageTitle,
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)
        val controller = FloatingWindowController(
            context = this,
            runtime = graph.host,
            activeRuns = graph.notificationCenter.runActivity.activeRuns,
            questionCenter = graph.questionCenter,
            preferences = FloatingWindowPreferences(this),
            theme = graph.themePreferences.theme,
            fontStyle = graph.fontPreferences.read(),
            virtualScreenProvider = { VirtualScreenRuntime.engine() },
            pluginSetup = pluginSetup,
            onOpenApp = { AppVisibility.bringToFront(this) },
        )
        floatingWindow = controller
        controller.start()
    }

    private fun registerClockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java))
        }

        fun applyPairingCode(context: Context, code: String?) {
            val intent = Intent(context, AgentService::class.java)
                .setAction(ACTION_PAIRING_CODE)
                .putExtra(EXTRA_PAIRING_CODE, code)
            ContextCompat.startForegroundService(context, intent)
        }

        const val ACTION_STOP = "io.github.whywood.deepdeep.action.STOP"
        const val ACTION_STOP_SESSION = "io.github.whywood.deepdeep.action.STOP_SESSION"
        const val ACTION_PAIRING_CODE = "io.github.whywood.deepdeep.action.PAIRING_CODE"
        const val EXTRA_PAIRING_CODE = "io.github.whywood.deepdeep.extra.PAIRING_CODE"
        private const val NOTIFICATION_ID = 1
        private const val ACCESSIBILITY_CHECK_DELAY_MS = 4_000L
    }
}
