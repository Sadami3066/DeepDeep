package io.github.whywood.deepdeep.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

object AppVisibility {
    @Volatile
    var foreground: Boolean = false

    @Volatile
    var taskId: Int? = null

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
