package io.github.whywood.deepdeep.plugin.host.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel as AndroidNotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.NotificationService
import io.github.whywood.deepdeep.plugin.api.PluginNotification
import java.util.concurrent.ConcurrentHashMap
import io.github.whywood.deepdeep.plugin.api.NotificationChannel as ApiChannel

internal class NotificationPluginService(
    private val context: Context,
    private val pluginId: String,
    private val notificationsEnabled: () -> Boolean,
    private val firstPageId: String? = null,
) : NotificationService {
    private val manager = NotificationManagerCompat.from(context)
    private val posted = ConcurrentHashMap<String, PostedNotification>()

    init {
        ensureChannels()
    }

    override val enabled: Boolean
        get() = notificationsEnabled() && permissionGranted()

    override fun notify(notification: PluginNotification) {
        posted[notification.id] = PostedNotification(notification.title, notification.body)
        post(notification)
    }

    override fun updateProgress(id: String, progress: Int?, title: String?) {
        val previous = posted[id]
        val builder = NotificationCompat.Builder(context, channelId(ChannelKind.PROGRESS))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: previous?.title ?: context.getString(R.string.plugins_title))
            .setContentText(previous?.body.orEmpty())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }
        post(builder.build(), id)
    }

    override fun cancel(id: String) {
        posted.remove(id)
        manager.cancel(notificationId(id))
    }

    private fun post(notification: PluginNotification) {
        val builder = NotificationCompat.Builder(context, channelId(kindOf(notification)))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setOngoing(notification.ongoing)
            .setOnlyAlertOnce(true)
        contentIntent()?.let { intent ->
            builder.setContentIntent(intent).setAutoCancel(true)
        }
        notification.progress?.let { progress ->
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }
        post(builder.build(), notification.id)
    }

    @SuppressLint("MissingPermission")
    private fun post(notification: Notification, id: String) {
        if (!enabled) return
        runCatching { manager.notify(notificationId(id), notification) }
    }

    private fun contentIntent(): PendingIntent? {
        val pageId = firstPageId?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("deepdeep://plugins/$pluginId/${Uri.encode(pageId)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                context,
                notificationId(pluginId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
    }

    private fun kindOf(notification: PluginNotification): ChannelKind = when (notification.channel) {
        ApiChannel.DEFAULT -> ChannelKind.DEFAULT
        ApiChannel.PROGRESS -> ChannelKind.PROGRESS
        ApiChannel.ALERT -> ChannelKind.ALERT
    }

    private fun channelId(kind: ChannelKind): String = pluginChannelId(pluginId, kind.id)

    private fun notificationId(id: String): Int = PLUGIN_NOTIFICATION_BASE + (("$pluginId:$id").hashCode() and 0x3FFFF)

    private fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val name = context.getString(R.string.plugins_title)
        ChannelKind.entries.forEach { kind ->
            system.createNotificationChannel(
                AndroidNotificationChannel(channelId(kind), name, kind.importance),
            )
        }
    }

    private data class PostedNotification(val title: String, val body: String)

    private enum class ChannelKind(val id: String, val importance: Int) {
        DEFAULT("default", NotificationManager.IMPORTANCE_DEFAULT),
        PROGRESS("progress", NotificationManager.IMPORTANCE_LOW),
        ALERT("alert", NotificationManager.IMPORTANCE_HIGH),
    }

    private companion object {
        const val PLUGIN_NOTIFICATION_BASE = 10_000
    }
}

internal fun pluginChannelId(pluginId: String, kind: String): String = "deepdeep_plugin_${pluginId}_$kind"
