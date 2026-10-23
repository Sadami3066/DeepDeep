package io.github.whywood.deepdeep.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.whywood.deepdeep.DeepDeepApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val graph by lazy { (application as DeepDeepApplication).graph }

    override fun onCreate() {
        super.onCreate()
        graph.notificationCenter.start()
        startForeground(NOTIFICATION_ID, graph.notificationCenter.foregroundNotification())
        graph.notificationCenter.onForegroundStarted()
        scope.launch { graph.host.ensureStarted() }
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
        graph.notificationCenter.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.whywood.deepdeep.action.STOP"
        const val ACTION_STOP_SESSION = "io.github.whywood.deepdeep.action.STOP_SESSION"
        private const val NOTIFICATION_ID = 1
    }
}
