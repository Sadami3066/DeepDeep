package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

enum class SendWhileRunning {
    STEER,
    QUEUE,
}

data class UiPreferences(
    val autoCollapseProcess: Boolean = true,
    val autoRetry: Boolean = true,
    val backgroundJobWake: Boolean = true,
    val pinnedWorkspaceIds: Set<String> = emptySet(),
    val chatFontScale: Float = CHAT_FONT_SCALE_DEFAULT,
    val sendWhileRunning: SendWhileRunning = SendWhileRunning.STEER,
)

const val CHAT_FONT_SCALE_MIN = 0.85f
const val CHAT_FONT_SCALE_MAX = 1.4f
const val CHAT_FONT_SCALE_STEP = 0.05f
const val CHAT_FONT_SCALE_DEFAULT = 1f

internal fun normalizeChatFontScale(value: Float): Float {
    if (!value.isFinite()) return CHAT_FONT_SCALE_DEFAULT
    val percent = (value * 100f).roundToInt().coerceIn(
        (CHAT_FONT_SCALE_MIN * 100f).roundToInt(),
        (CHAT_FONT_SCALE_MAX * 100f).roundToInt(),
    )
    val roundedPercent = (percent / (CHAT_FONT_SCALE_STEP * 100f)).roundToInt() * (CHAT_FONT_SCALE_STEP * 100f).roundToInt()
    return roundedPercent / 100f
}

enum class UiToggle {
    AUTO_COLLAPSE_PROCESS,
    AUTO_RETRY,
    BACKGROUND_JOB_WAKE,
}

class AppPreferences(context: Context) {
    private val store = context.applicationContext.appPreferencesDataStore
    private val autoCollapseKey = booleanPreferencesKey("auto_collapse_process")
    private val autoRetryKey = booleanPreferencesKey("auto_retry")
    private val backgroundJobWakeKey = booleanPreferencesKey("background_job_wake")
    private val pinnedWorkspacesKey = stringSetPreferencesKey("pinned_workspace_ids")
    private val chatFontScaleKey = floatPreferencesKey("chat_font_scale")
    private val sendWhileRunningKey = stringPreferencesKey("send_while_running")

    val preferences: Flow<UiPreferences> = store.data
        .map { values ->
            UiPreferences(
                autoCollapseProcess = values[autoCollapseKey] ?: true,
                autoRetry = values[autoRetryKey] ?: true,
                backgroundJobWake = values[backgroundJobWakeKey] ?: true,
                pinnedWorkspaceIds = values[pinnedWorkspacesKey] ?: emptySet(),
                chatFontScale = normalizeChatFontScale(values[chatFontScaleKey] ?: CHAT_FONT_SCALE_DEFAULT),
                sendWhileRunning = values[sendWhileRunningKey]
                    ?.let { stored -> SendWhileRunning.entries.firstOrNull { it.name == stored } }
                    ?: SendWhileRunning.STEER,
            )
        }
        .catch { error ->
            if (error is IOException) emit(UiPreferences()) else throw error
        }

    suspend fun read(): UiPreferences = preferences.first()

    suspend fun set(toggle: UiToggle, enabled: Boolean) {
        store.edit { values ->
            when (toggle) {
                UiToggle.AUTO_COLLAPSE_PROCESS -> values[autoCollapseKey] = enabled
                UiToggle.AUTO_RETRY -> values[autoRetryKey] = enabled
                UiToggle.BACKGROUND_JOB_WAKE -> values[backgroundJobWakeKey] = enabled
            }
        }
    }

    suspend fun setChatFontScale(value: Float) {
        store.edit { it[chatFontScaleKey] = normalizeChatFontScale(value) }
    }

    suspend fun setSendWhileRunning(value: SendWhileRunning) {
        store.edit { it[sendWhileRunningKey] = value.name }
    }

    suspend fun setWorkspacePinned(workspaceId: String, pinned: Boolean) {
        if (workspaceId.isBlank()) return
        store.edit { values ->
            val current = values[pinnedWorkspacesKey] ?: emptySet()
            values[pinnedWorkspacesKey] = if (pinned) current + workspaceId else current - workspaceId
        }
    }
}
