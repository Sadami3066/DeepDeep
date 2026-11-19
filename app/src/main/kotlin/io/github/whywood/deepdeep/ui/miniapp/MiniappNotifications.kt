package io.github.whywood.deepdeep.ui.miniapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.MiniappHostException
import io.github.whywood.deepdeep.runtime.MiniappNotificationRequest
import io.github.whywood.deepdeep.runtime.MiniappReminder
import io.github.whywood.deepdeep.runtime.MiniappReminderRequest
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CHANNEL_ID = "miniapp_reminders"
private const val PREFS_NAME = "miniapp_notifications"
private const val ACTION_REMINDER = "io.github.whywood.deepdeep.action.MINIAPP_REMINDER"
private const val EXTRA_APP_ID = "app_id"
private const val EXTRA_REMINDER_ID = "reminder_id"
private const val MAX_REMINDERS_PER_APP = 64
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

@Serializable
internal data class StoredReminder(
    val appId: String,
    val appName: String,
    val id: String,
    val title: String,
    val body: String,
    val at: Long,
    val repeatDaily: Boolean,
)

internal class MiniappReminderStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cache: List<StoredReminder>? = null

    fun all(): List<StoredReminder> = synchronized(lock) { load().toList() }

    fun forApp(appId: String): List<StoredReminder> = all().filter { it.appId == appId }

    fun find(appId: String, id: String): StoredReminder? =
        all().firstOrNull { it.appId == appId && it.id == id }

    fun upsert(reminder: StoredReminder) = synchronized(lock) {
        val current = load().filterNot { it.appId == reminder.appId && it.id == reminder.id }
        save(current + reminder)
    }

    fun remove(appId: String, id: String) = synchronized(lock) {
        save(load().filterNot { it.appId == appId && it.id == id })
    }

    fun removeAllForApp(appId: String) = synchronized(lock) {
        save(load().filterNot { it.appId == appId })
    }

    private fun load(): List<StoredReminder> {
        cache?.let { return it }
        val loaded = runCatching {
            if (!file.isFile) emptyList() else json.decodeFromString<List<StoredReminder>>(file.readText())
        }.getOrDefault(emptyList())
        cache = loaded
        return loaded
    }

    private fun save(reminders: List<StoredReminder>) {
        cache = reminders
        runCatching {
            file.parentFile?.let { parent -> if (!parent.isDirectory) parent.mkdirs() }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(reminders))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }
}

internal class MiniappReminderScheduler(private val context: Context) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(reminder: StoredReminder) {
        val manager = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.appId, reminder.id),
            reminderIntent(reminder.appId, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
            }
        }
    }

    fun cancel(reminder: StoredReminder) {
        val manager = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.appId, reminder.id),
            reminderIntent(reminder.appId, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { manager.cancel(pending) }
    }

    fun reschedule(reminders: List<StoredReminder>) {
        reminders.forEach { reminder ->
            if (reminder.at > System.currentTimeMillis()) schedule(reminder) else cancel(reminder)
        }
    }

    private fun reminderIntent(appId: String, reminderId: String): Intent =
        Intent(context, MiniappReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_APP_ID, appId)
            .putExtra(EXTRA_REMINDER_ID, reminderId)

    private fun requestCode(appId: String, id: String): Int = (appId + ":" + id).hashCode()
}

internal class MiniappNotificationCenter(
    private val context: Context,
    private val appId: String,
    private val appName: String,
) {
    private val store = MiniappReminderStore(reminderFile(context))
    private val scheduler = MiniappReminderScheduler(context)
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun show(request: MiniappNotificationRequest): String {
        ensureChannel(context)
        val id = request.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        postNotification(context, appId, appName, id, request.title, request.body)
        recordPosted(id)
        return id
    }

    fun cancel(id: String): Boolean {
        val removedPosted = forgetPosted(id)
        val reminder = store.find(appId, id)
        if (reminder != null) {
            scheduler.cancel(reminder)
            store.remove(appId, id)
        }
        NotificationManagerCompat.from(context).cancel(notificationId(appId, id))
        return removedPosted || reminder != null
    }

    fun cancelAll(): Boolean {
        val posted = postedIds()
        posted.forEach { id -> NotificationManagerCompat.from(context).cancel(notificationId(appId, id)) }
        clearPosted()
        val reminders = store.forApp(appId)
        reminders.forEach { reminder -> scheduler.cancel(reminder) }
        store.removeAllForApp(appId)
        return posted.isNotEmpty() || reminders.isNotEmpty()
    }

    fun schedule(request: MiniappReminderRequest): MiniappReminder {
        ensureChannel(context)
        val existing = store.forApp(appId)
        if (existing.size >= MAX_REMINDERS_PER_APP && existing.none { it.id == request.id }) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "too many reminders")
        }
        val id = request.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val reminder = StoredReminder(
            appId = appId,
            appName = appName,
            id = id,
            title = request.title,
            body = request.body,
            at = request.atMillis,
            repeatDaily = request.repeatDaily,
        )
        store.upsert(reminder)
        scheduler.schedule(reminder)
        return reminder.toRuntime()
    }

    fun list(): List<MiniappReminder> = store.forApp(appId)
        .sortedBy { it.at }
        .map { it.toRuntime() }

    fun clearAllForApp() {
        val reminders = store.forApp(appId)
        reminders.forEach { reminder -> scheduler.cancel(reminder) }
        store.removeAllForApp(appId)
        clearPosted()
    }

    private fun recordPosted(id: String) {
        val ids = postedIds() + id
        preferences.edit().putStringSet(key(), ids.toSet()).apply()
    }

    private fun forgetPosted(id: String): Boolean {
        val ids = postedIds()
        if (id !in ids) return false
        preferences.edit().putStringSet(key(), (ids - id).toSet()).apply()
        return true
    }

    private fun clearPosted() {
        preferences.edit().remove(key()).apply()
    }

    private fun postedIds(): Set<String> = preferences.getStringSet(key(), emptySet()).orEmpty()

    private fun key(): String = "posted_$appId"
}

internal fun reminderFile(context: Context): File = File(context.filesDir, "miniapp-reminders.json")

internal fun notificationId(appId: String, id: String): Int = ("miniapp:$appId:$id").hashCode()

private fun StoredReminder.toRuntime(): MiniappReminder = MiniappReminder(
    id = id,
    title = title,
    body = body,
    atMillis = at,
    repeatDaily = repeatDaily,
)

internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.miniapp_notifications_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}

internal fun postNotification(
    context: Context,
    appId: String,
    appName: String,
    reminderId: String,
    title: String,
    body: String,
) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        throw MiniappHostException(MiniappHostException.PERMISSION_DENIED, "notifications are disabled")
    }
    ensureChannel(context)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("deepdeep://miniapp/$appId?note=$reminderId"))
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pending = PendingIntent.getActivity(
        context,
        notificationId(appId, reminderId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title.ifBlank { appName })
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(notificationId(appId, reminderId), notification)
    }.getOrElse { error ->
        throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot post notification")
    }
}

class MiniappReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = MiniappReminderStore(reminderFile(context))
        val scheduler = MiniappReminderScheduler(context)
        when (intent.action) {
            EXTRA_ACTION_REMINDER -> deliver(context, store, scheduler, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                scheduler.reschedule(store.all())
        }
    }

    private fun deliver(
        context: Context,
        store: MiniappReminderStore,
        scheduler: MiniappReminderScheduler,
        intent: Intent,
    ) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val reminder = store.find(appId, reminderId) ?: return
        val appDirectory = File(context.filesDir, "miniapp/apps/$appId")
        if (!appDirectory.isDirectory) {
            store.remove(appId, reminderId)
            return
        }
        runCatching {
            postNotification(context, reminder.appId, reminder.appName, reminder.id, reminder.title, reminder.body)
        }
        if (reminder.repeatDaily) {
            var next = reminder.at + DAY_MILLIS
            val now = System.currentTimeMillis()
            while (next <= now) next += DAY_MILLIS
            val repeated = reminder.copy(at = next)
            store.upsert(repeated)
            scheduler.schedule(repeated)
        } else {
            store.remove(appId, reminderId)
        }
    }

    private companion object {
        const val EXTRA_ACTION_REMINDER = ACTION_REMINDER
    }
}
