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
import io.github.whywood.deepdeep.plugin.api.PluginNotificationAction
import io.github.whywood.deepdeep.plugin.api.PluginNotificationHandle
import io.github.whywood.deepdeep.plugin.api.PluginNotificationRecord
import io.github.whywood.deepdeep.plugin.host.components.PluginNotificationActionReceiver
import io.github.whywood.deepdeep.plugin.host.components.PluginNotificationEvents
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
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

    override fun notifyChecked(notification: PluginNotification): PluginNotificationHandle? {
        posted[notification.id] = PostedNotification(notification.title, notification.body)
        if (!enabled) return null
        post(notification)
        return PluginNotificationHandle(
            id = notification.id,
            key = pluginNotificationKey(pluginId, notification.id),
        )
    }

    override val listenerGranted: Boolean
        get() = PluginNotificationEvents.listenerGranted(context)

    override suspend fun recent(limit: Int): List<PluginNotificationRecord> =
        PluginNotificationEvents.recent(limit)

    override fun received(): Flow<PluginNotificationRecord> = PluginNotificationEvents.received()

    override fun removed(): Flow<String> = PluginNotificationEvents.removed()

    override suspend fun dismiss(key: String): Boolean = PluginNotificationEvents.dismiss(key)

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
        notification.actions.take(MaxActions).forEach { action ->
            builder.addAction(
                0,
                action.label,
                actionIntent(notificationId = notification.id, action = action, payload = notification.payload.toString()),
            )
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

    private fun actionIntent(
        notificationId: String,
        action: PluginNotificationAction,
        payload: String,
    ): PendingIntent? = runCatching {
        val intent = Intent(context, PluginNotificationActionReceiver::class.java)
            .setAction(PluginNotificationActionReceiver.ACTION_PLUGIN_NOTIFICATION)
            .putExtra(PluginNotificationActionReceiver.EXTRA_PLUGIN_ID, pluginId)
            .putExtra(PluginNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(PluginNotificationActionReceiver.EXTRA_ACTION_ID, action.id)
            .putExtra(PluginNotificationActionReceiver.EXTRA_PAYLOAD, payload)
        PendingIntent.getBroadcast(
            context,
            ("$pluginId:$notificationId:${action.id}").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }.getOrNull()

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
        const val MaxActions = 3
    }
}

internal fun pluginNotificationKey(pluginId: String, notificationId: String): String =
    "plugin:$pluginId:$notificationId"

internal fun pluginChannelId(pluginId: String, kind: String): String = "deepdeep_plugin_${pluginId}_$kind"
