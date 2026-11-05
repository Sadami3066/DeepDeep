package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.core.content.edit

class OnboardingPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    fun isStarted(): Boolean = preferences.getBoolean(KEY_STARTED, false)

    fun markStarted() {
        preferences.edit { putBoolean(KEY_STARTED, true) }
    }

    fun markCompleted() {
        preferences.edit { putBoolean(KEY_COMPLETED, true) }
    }

    companion object {
        private const val FILE_NAME = "onboarding"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_STARTED = "started"
    }
}
