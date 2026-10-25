package io.github.whywood.deepdeep.plugin.arrange

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.whywood.deepdeep.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ArrangeAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStop = Runnable { stopAlarm() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val id = intent.getStringExtra(ArrangeAlarmReceiver.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(ArrangeAlarmReceiver.EXTRA_ALARM_LABEL).orEmpty()
        val firedAt = intent.getLongExtra(
            ArrangeAlarmReceiver.EXTRA_ALARM_FIRED_AT,
            System.currentTimeMillis(),
        )
        return try {
            showForegroundNotification(id = id, label = label, firedAt = firedAt)
            startRinging()
            startVibration()
            holdWakeLock()
            handler.removeCallbacks(autoStop)
            handler.postDelayed(autoStop, AUTO_STOP_MILLIS)
            START_NOT_STICKY
        } catch (_: Exception) {
            cleanup()
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        cleanup()
        super.onDestroy()
    }

    @SuppressLint("InlinedApi")
    private fun showForegroundNotification(id: String, label: String, firedAt: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, ArrangeAlarmActivity::class.java).apply {
                putExtra(ArrangeAlarmReceiver.EXTRA_ALARM_ID, id)
                putExtra(ArrangeAlarmReceiver.EXTRA_ALARM_LABEL, label)
                putExtra(ArrangeAlarmReceiver.EXTRA_ALARM_FIRED_AT, firedAt)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, ArrangeAlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label.ifBlank { getString(R.string.plugin_arrange_name) })
            .setContentText(formatTime(firedAt))
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
        if (canUseFullScreenIntent(manager)) {
            builder.setFullScreenIntent(openIntent, true)
        }
        val notification = builder.build()
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun canUseFullScreenIntent(manager: NotificationManager?): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager?.canUseFullScreenIntent() == true
        } else {
            true
        }

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        val tone = runCatching { RingtoneManager.getRingtone(this, uri) }.getOrNull() ?: return
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        tone.isLooping = true
        runCatching { tone.play() }.onSuccess { ringtone = tone }
    }

    private fun startVibration() {
        val vibratorService = getSystemService(Vibrator::class.java) ?: return
        if (!vibratorService.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        runCatching { vibratorService.vibrate(effect) }.onSuccess { vibrator = vibratorService }
    }

    private fun holdWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        val lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        runCatching { lock.acquire(AUTO_STOP_MILLIS) }.onSuccess { wakeLock = lock }
    }

    private fun stopAlarm() {
        handler.removeCallbacks(autoStop)
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        ringtone?.let { tone -> runCatching { tone.stop() } }
        ringtone = null
        vibrator?.let { service -> runCatching { service.cancel() } }
        vibrator = null
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.plugin_arrange_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_FIRE = "io.github.whywood.deepdeep.plugin.arrange.ACTION_FIRE"
        const val ACTION_STOP = "io.github.whywood.deepdeep.plugin.arrange.ACTION_STOP"
        private const val CHANNEL_ID = "arrange_alarm"
        private const val NOTIFICATION_ID = 5301
        private const val REQUEST_OPEN = 5302
        private const val REQUEST_STOP = 5303
        private const val AUTO_STOP_MILLIS = 10L * 60L * 1_000L
        private const val WAKE_LOCK_TAG = "deepdeep:arrange_alarm"
        private val VIBRATION_PATTERN = longArrayOf(0L, 800L, 600L)
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }

    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
}
