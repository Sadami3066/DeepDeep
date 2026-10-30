package io.github.whywood.deepdeep.assistant

import android.content.Context
import android.content.Intent

internal object AssistantEntry {
    fun powerSettingsActions(): List<String> = listOf(
        "android.settings.ACTION_POWER_MENU_SETTINGS",
        "android.settings.ASSIST_GESTURE_SETTINGS",
        "android.settings.VOICE_INPUT_SETTINGS",
    )

    fun powerSettingsIntent(context: Context): Intent? {
        val manager = context.packageManager
        return powerSettingsActions()
            .map { action -> Intent(action) }
            .firstOrNull { intent -> manager.resolveActivity(intent, 0) != null }
    }
}
