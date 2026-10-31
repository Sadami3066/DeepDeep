package io.github.whywood.deepdeep.adb

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.R

class AdbPairingNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.adb_pairing_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.adb_pairing_channel_description)
            },
        )
    }

    fun canNotify(): Boolean {
        if (manager == null) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyCodeRequired(): Boolean {
        val remoteInput = RemoteInput.Builder(EXTRA_CODE)
            .setLabel(context.getString(R.string.adb_pairing_reply_label))
            .build()
        val action = NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.adb_pairing_reply_action),
            replyIntent(),
        ).addRemoteInput(remoteInput).build()
        val text = context.getString(R.string.adb_pairing_notification_text)
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.adb_pairing_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(settingsIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
        return post(builder.build())
    }

    fun notifyPairingStarted() {
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.adb_pairing_progress_title))
            .setContentText(context.getString(R.string.adb_pairing_progress_text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
        post(builder.build())
    }

    fun notifyPaired() {
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.adb_pairing_success_title))
            .setContentText(context.getString(R.string.adb_pairing_success_text))
            .setAutoCancel(true)
        post(builder.build())
    }

    fun notifyFailed(error: AdbBridgeError) {
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.adb_pairing_failed_title))
            .setContentText(errorText(error))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.adb_pairing_retry_action), settingsIntent())
        post(builder.build())
    }

    fun cancel() {
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    private fun replyIntent(): PendingIntent {
        val intent = Intent(context, AdbPairingReceiver::class.java)
            .setAction(ACTION_REPLY)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REPLY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
    }

    private fun settingsIntent(): PendingIntent {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_SETTINGS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    @SuppressLint("MissingPermission")
    private fun post(notification: android.app.Notification): Boolean {
        if (!canNotify()) return false
        return runCatching {
            manager?.notify(NOTIFICATION_ID, notification) ?: return false
            true
        }.getOrDefault(false)
    }

    fun errorText(error: AdbBridgeError): String = context.getString(
        when (error) {
            AdbBridgeError.WIRELESS_DEBUGGING_DISABLED -> R.string.adb_error_wireless_debugging_disabled
            AdbBridgeError.NOTIFICATION_PERMISSION_REQUIRED -> R.string.adb_error_notification_permission_required
            AdbBridgeError.NOTIFICATION_UNAVAILABLE -> R.string.adb_error_notification_unavailable
            AdbBridgeError.PAIRING_CODE_INVALID -> R.string.adb_error_pairing_code_invalid
            AdbBridgeError.NO_PAIRING_SERVICE -> R.string.adb_error_no_pairing_service
            AdbBridgeError.PAIRING_FAILED -> R.string.adb_error_pairing_failed
            AdbBridgeError.NOT_PAIRED -> R.string.adb_error_not_paired
            AdbBridgeError.CONNECT_FAILED -> R.string.adb_error_connect_failed
            AdbBridgeError.DAEMON_START_FAILED -> R.string.adb_error_daemon_start_failed
        },
    )

    companion object {
        const val ACTION_REPLY = "io.github.whywood.deepdeep.adb.action.PAIRING_REPLY"
        const val EXTRA_CODE = "io.github.whywood.deepdeep.adb.extra.PAIRING_CODE"
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 600
        private const val REQUEST_REPLY = 601
        private const val REQUEST_SETTINGS = 602
    }
}
