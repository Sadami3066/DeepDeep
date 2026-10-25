package io.github.whywood.deepdeep.plugin.arrange

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ArrangeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_FIRED -> onAlarmFired(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> reschedule(context)
        }
    }

    private fun onAlarmFired(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val scheduler = ArrangeAlarmScheduler(context, arrangeAlarmStore(context))
        val alarm = runCatching { scheduler.onFired(id) }.getOrNull() ?: return
        val serviceIntent = Intent(context, ArrangeAlarmService::class.java).apply {
            action = ArrangeAlarmService.ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_FIRED_AT, System.currentTimeMillis())
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
    }

    private fun reschedule(context: Context) {
        val scheduler = ArrangeAlarmScheduler(context, arrangeAlarmStore(context))
        runCatching { scheduler.rescheduleAll() }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "io.github.whywood.deepdeep.plugin.arrange.ACTION_ALARM_FIRED"
        const val EXTRA_ALARM_ID = "io.github.whywood.deepdeep.plugin.arrange.EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "io.github.whywood.deepdeep.plugin.arrange.EXTRA_ALARM_LABEL"
        const val EXTRA_ALARM_FIRED_AT = "io.github.whywood.deepdeep.plugin.arrange.EXTRA_ALARM_FIRED_AT"
    }
}
