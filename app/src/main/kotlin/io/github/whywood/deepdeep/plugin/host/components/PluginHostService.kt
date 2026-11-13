package io.github.whywood.deepdeep.plugin.host.components

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.ForegroundServiceOptions
import io.github.whywood.deepdeep.plugin.api.foregroundServiceOptions
import io.github.whywood.deepdeep.plugin.host.services.pluginChannelId
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class PluginHostService : Service() {
    private val notifications = ConcurrentHashMap<String, ForegroundServiceOptions>()
    private var foregroundPluginId: String? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> handleShow(intent)
            ACTION_STOP -> handleStop(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notifications.keys.forEach { pluginId -> notificationManager()?.cancel(notificationId(pluginId)) }
        notifications.clear()
        foregroundStarted = false
        foregroundPluginId = null
        super.onDestroy()
    }

    private fun handleShow(intent: Intent) {
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)?.takeIf { it.isNotBlank() } ?: return
        val options = optionsFrom(intent) ?: return
        notifications[pluginId] = options
        ensureChannel(pluginId, options)
        val notification = buildNotification(
            pluginId = pluginId,
            options = options,
            firstPageId = intent.getStringExtra(EXTRA_FIRST_PAGE_ID),
        )
        when {
            !foregroundStarted -> {
                if (!startForegroundCompat(notificationId(pluginId), notification)) {
                    notifications.remove(pluginId)
                    stopSelf()
                    return
                }
                foregroundStarted = true
                foregroundPluginId = pluginId
            }

            foregroundPluginId == pluginId -> {
                if (!startForegroundCompat(notificationId(pluginId), notification)) {
                    notifications.remove(pluginId)
                }
            }

            else -> notificationManager()?.notify(notificationId(pluginId), notification)
        }
    }

    private fun handleStop(intent: Intent) {
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)?.takeIf { it.isNotBlank() } ?: return
        notifications.remove(pluginId)
        notificationManager()?.cancel(notificationId(pluginId))
        if (foregroundPluginId != pluginId) {
            if (!foregroundStarted) stopSelf()
            return
        }
        val next = notifications.entries.firstOrNull()
        if (next != null) {
            val notification = buildNotification(next.key, next.value, firstPageId = null)
            if (startForegroundCompat(notificationId(next.key), notification)) {
                foregroundPluginId = next.key
            }
        } else {
            foregroundPluginId = null
            foregroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(
        pluginId: String,
        options: ForegroundServiceOptions,
        firstPageId: String?,
    ): Notification {
        val builder = NotificationCompat.Builder(this, pluginChannelId(pluginId, CHANNEL_KIND))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(options.title)
            .setContentText(options.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(options.text))
            .setOngoing(options.ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        options.progress?.let { progress ->
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }
        contentIntent(pluginId, firstPageId)?.let(builder::setContentIntent)
        return builder.build()
    }

    private fun contentIntent(pluginId: String, firstPageId: String?): PendingIntent? = runCatching {
        val intent = if (firstPageId.isNullOrBlank()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("deepdeep://plugins/$pluginId/${Uri.encode(firstPageId)}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        PendingIntent.getActivity(
            this,
            notificationId(pluginId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }.getOrNull()

    private fun ensureChannel(pluginId: String, options: ForegroundServiceOptions) {
        val manager = notificationManager() ?: return
        val channelId = pluginChannelId(pluginId, CHANNEL_KIND)
        if (manager.getNotificationChannel(channelId) != null) return
        val name = options.title.takeIf { it.isNotBlank() } ?: getString(R.string.plugins_title)
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(id: Int, notification: Notification): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
        true
    }.getOrDefault(false)

    private fun notificationManager(): NotificationManager? =
        getSystemService(NotificationManager::class.java)

    private fun optionsFrom(intent: Intent): ForegroundServiceOptions? {
        val id = intent.getStringExtra(EXTRA_OPTIONS_ID) ?: return null
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return null
        val ongoing = intent.getBooleanExtra(EXTRA_ONGOING, true)
        val progress = if (intent.hasExtra(EXTRA_PROGRESS)) {
            intent.getIntExtra(EXTRA_PROGRESS, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return foregroundServiceOptions(id, title, text, ongoing, progress)
    }

    companion object {
        private const val ACTION_START = "io.github.whywood.deepdeep.plugin.action.START_FOREGROUND"
        private const val ACTION_UPDATE = "io.github.whywood.deepdeep.plugin.action.UPDATE_FOREGROUND"
        private const val ACTION_STOP = "io.github.whywood.deepdeep.plugin.action.STOP_FOREGROUND"
        private const val EXTRA_PLUGIN_ID = "pluginId"
        private const val EXTRA_OPTIONS_ID = "optionsId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_ONGOING = "ongoing"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_FIRST_PAGE_ID = "firstPageId"
        private const val CHANNEL_KIND = "service"
        private const val NOTIFICATION_BASE = 900_000

        internal fun start(
            context: Context,
            pluginId: String,
            options: ForegroundServiceOptions,
            firstPageId: String? = null,
        ): Boolean = dispatch(context, ACTION_START, pluginId, options, firstPageId)

        internal fun update(
            context: Context,
            pluginId: String,
            options: ForegroundServiceOptions,
        ): Boolean = dispatch(context, ACTION_UPDATE, pluginId, options, null)

        internal fun stop(context: Context, pluginId: String): Boolean = runCatching {
            context.startService(
                buildIntent(context, ACTION_STOP, pluginId, null, null),
            )
            true
        }.getOrDefault(false)

        private fun dispatch(
            context: Context,
            action: String,
            pluginId: String,
            options: ForegroundServiceOptions?,
            firstPageId: String?,
        ): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                buildIntent(context, action, pluginId, options, firstPageId),
            )
            true
        }.getOrDefault(false)

        private fun buildIntent(
            context: Context,
            action: String,
            pluginId: String,
            options: ForegroundServiceOptions?,
            firstPageId: String?,
        ): Intent = Intent(context, PluginHostService::class.java)
            .setAction(action)
            .putExtra(EXTRA_PLUGIN_ID, pluginId)
            .putExtra(EXTRA_FIRST_PAGE_ID, firstPageId)
            .apply {
                if (options != null) {
                    putExtra(EXTRA_OPTIONS_ID, options.id)
                    putExtra(EXTRA_TITLE, options.title)
                    putExtra(EXTRA_TEXT, options.text)
                    putExtra(EXTRA_ONGOING, options.ongoing)
                    options.progress?.let { putExtra(EXTRA_PROGRESS, it) }
                }
            }

        private fun notificationId(pluginId: String): Int =
            NOTIFICATION_BASE + (pluginId.hashCode() and 0x3FFFF)
    }
}
