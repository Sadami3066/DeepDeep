package io.github.whywood.deepdeep.plugin.host.components

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.github.whywood.deepdeep.plugin.api.PluginNotificationActionInfo
import io.github.whywood.deepdeep.plugin.api.PluginNotificationRecord
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject

class PluginNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        PluginNotificationEvents.onConnected(this)
    }

    override fun onListenerDisconnected() {
        PluginNotificationEvents.onDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        PluginNotificationEvents.onDisconnected(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        PluginNotificationEvents.posted(this, notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        PluginNotificationEvents.removed(notification.key)
    }
}

internal object PluginNotificationEvents {
    private const val Capacity = 100

    private val lock = Any()
    private val buffer = ArrayDeque<PluginNotificationRecord>()
    private val labels = ConcurrentHashMap<String, String>()

    private val receivedFlow = MutableSharedFlow<PluginNotificationRecord>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val removedFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var service: PluginNotificationListenerService? = null

    @Volatile
    var connected: Boolean = false
        private set

    var receivedDispatcher: ((PluginNotificationRecord, Boolean) -> Unit)? = null

    var actionDispatcher: ((String, String, String, JsonObject) -> Unit)? = null

    fun onConnected(listener: PluginNotificationListenerService) {
        service = listener
        connected = true
    }

    fun onDisconnected(listener: PluginNotificationListenerService) {
        if (service === listener) {
            service = null
            connected = false
        }
    }

    fun listenerGranted(context: Context): Boolean =
        connected || runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }.getOrDefault(false)

    fun recent(limit: Int): List<PluginNotificationRecord> = synchronized(lock) {
        buffer.takeLast(limit.coerceIn(1, Capacity)).reversed()
    }

    fun received(): Flow<PluginNotificationRecord> = receivedFlow

    fun removed(): Flow<String> = removedFlow

    fun dismiss(key: String): Boolean {
        val target = service ?: return false
        val cancelled = runCatching { target.cancelNotification(key) }.isSuccess
        removeRecord(key)
        return cancelled
    }

    fun posted(context: Context, sbn: StatusBarNotification) {
        val record = buildRecord(context, sbn) ?: return
        synchronized(lock) {
            buffer.removeAll { it.key == record.key }
            buffer.addLast(record)
            while (buffer.size > Capacity) buffer.removeFirst()
        }
        receivedFlow.tryEmit(record)
        receivedDispatcher?.invoke(record, false)
    }

    fun removed(key: String) {
        val record = removeRecord(key) ?: return
        removedFlow.tryEmit(key)
        receivedDispatcher?.invoke(record, true)
    }

    fun dispatchAction(pluginId: String, notificationId: String, actionId: String, payload: JsonObject) {
        actionDispatcher?.invoke(pluginId, notificationId, actionId, payload)
    }

    private fun removeRecord(key: String): PluginNotificationRecord? = synchronized(lock) {
        val removed = buffer.firstOrNull { it.key == key }
        buffer.removeAll { it.key == key }
        removed
    }

    private fun buildRecord(context: Context, sbn: StatusBarNotification): PluginNotificationRecord? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val packageName = sbn.packageName ?: return null
        val appLabel = labels[packageName] ?: appLabel(context, packageName).also { label ->
            if (label.isNotBlank()) labels[packageName] = label
        }
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1).takeIf { it in 0..100 }
        val actions = notification.actions.orEmpty().mapIndexed { index, action ->
            PluginNotificationActionInfo(
                id = index.toString(),
                title = action.title?.toString()?.takeIf { it.isNotBlank() },
            )
        }
        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        return PluginNotificationRecord(
            key = sbn.key.orEmpty(),
            packageName = packageName,
            appLabel = appLabel.takeIf { it.isNotBlank() },
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() },
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            channelId = notification.channelId,
            category = notification.category,
            postedAtMs = sbn.postTime,
            ongoing = ongoing,
            clearable = !ongoing && notification.flags and Notification.FLAG_NO_CLEAR == 0,
            progress = progress,
            actions = actions,
        )
    }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault("")
}
