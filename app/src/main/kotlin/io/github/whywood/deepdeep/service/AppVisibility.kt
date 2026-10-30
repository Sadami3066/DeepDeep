package io.github.whywood.deepdeep.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger

object AppVisibility {
    private val startedActivities = AtomicInteger(0)

    val foreground: Boolean get() = startedActivities.get() > 0

    @Volatile
    var taskId: Int? = null

    fun activityStarted() {
        startedActivities.incrementAndGet()
    }

    fun activityStopped() {
        startedActivities.updateAndGet { count -> if (count > 0) count - 1 else 0 }
    }

    @Suppress("DEPRECATION")
    fun bringToFront(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val task = taskId
        if (task != null) {
            val moved = runCatching {
                manager.moveTaskToFront(task, ActivityManager.MOVE_TASK_NO_USER_ACTION)
            }.isSuccess
            if (moved) return
        }
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { appContext.startActivity(intent) }
    }
}
