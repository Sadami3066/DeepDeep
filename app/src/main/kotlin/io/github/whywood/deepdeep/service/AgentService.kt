package io.github.whywood.deepdeep.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.DeepDeepApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                scope.launch { graph.scheduleService.recomputeTargets() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph.notificationCenter.start()
        startForeground(NOTIFICATION_ID, graph.notificationCenter.foregroundNotification())
        graph.notificationCenter.onForegroundStarted()
        registerClockReceiver()
        scope.launch {
            ProcessExitLog.record(this@AgentService)
            graph.host.ensureStarted()
            graph.scheduleService.start()
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
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(clockReceiver) }
        graph.scheduleService.stop()
        graph.notificationCenter.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerClockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        const val ACTION_STOP = "io.github.whywood.deepdeep.action.STOP"
        const val ACTION_STOP_SESSION = "io.github.whywood.deepdeep.action.STOP_SESSION"
        private const val NOTIFICATION_ID = 1
        private const val ACCESSIBILITY_CHECK_DELAY_MS = 4_000L
    }
}
