package io.github.whywood.deepdeep.core

import android.content.Context
import android.content.Intent

import io.github.whywood.deepdeep.assistant.AssistantOverlayActivity

internal object AssistantEntry {
    const val EXTRA_SESSION_ID = "io.github.whywood.deepdeep.extra.SESSION_ID"

    fun openOverlay(context: Context, sessionId: String? = null): Boolean {
        val intent = Intent(context, AssistantOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!sessionId.isNullOrBlank()) intent.putExtra(EXTRA_SESSION_ID, sessionId)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

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
