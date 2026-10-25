package io.github.whywood.deepdeep.plugin.arrange

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

internal class ArrangeAlarmScheduler(
    private val context: Context,
    private val store: ArrangeAlarmStore,
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    fun schedule(alarm: ArrangeAlarm) {
        val manager = alarmManager ?: throw ArrangeExactAlarmException("系统闹钟服务不可用")
        if (!canScheduleExactAlarms()) {
            throw ArrangeExactAlarmException("未授予精确闹钟权限，无法设置闹钟")
        }
        try {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(alarm.triggerAt, showIntent(alarm.id)),
                fireIntent(alarm.id),
            )
        } catch (_: SecurityException) {
            throw ArrangeExactAlarmException("系统拒绝了精确闹钟请求")
        }
    }

    fun cancel(id: String) {
        runCatching { alarmManager?.cancel(fireIntent(id)) }
    }

    fun onFired(id: String): ArrangeAlarm? {
        val fired = store.fire(id) ?: return null
        if (fired.enabled) runCatching { schedule(fired) }
        return fired
    }

    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        store.list().forEach { alarm ->
            if (!alarm.enabled) return@forEach
            val updated = when {
                alarm.timer -> if (alarm.triggerAt > now) alarm else alarm.copy(enabled = false)
                alarm.days.isEmpty() -> if (alarm.triggerAt > now) alarm else alarm.copy(enabled = false)
                else -> alarm.copy(
                    triggerAt = nextAlarmTrigger(alarm.hour, alarm.minute, alarm.days, now, zone),
                )
            }
            if (updated != alarm) store.update(updated)
            if (updated.enabled) runCatching { schedule(updated) }
        }
    }

    private fun fireIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(id),
        Intent(context, ArrangeAlarmReceiver::class.java)
            .setAction(ArrangeAlarmReceiver.ACTION_ALARM_FIRED)
            .putExtra(ArrangeAlarmReceiver.EXTRA_ALARM_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(id: String): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(id),
        Intent(context, ArrangeAlarmActivity::class.java)
            .putExtra(ArrangeAlarmReceiver.EXTRA_ALARM_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestCode(id: String): Int = id.hashCode()
}
