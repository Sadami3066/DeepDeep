package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

data class UiPreferences(
    val hapticsEnabled: Boolean = true,
    val sendHidesKeyboard: Boolean = true,
    val followStreaming: Boolean = true,
    val showReasoning: Boolean = true,
    val expandProcessByDefault: Boolean = false,
    val pinnedWorkspaceIds: Set<String> = emptySet(),
)

enum class UiToggle {
    HAPTICS,
    SEND_HIDES_KEYBOARD,
    FOLLOW_STREAMING,
    SHOW_REASONING,
    EXPAND_PROCESS,
}

class AppPreferences(context: Context) {
    private val store = context.applicationContext.appPreferencesDataStore
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val sendHidesKeyboardKey = booleanPreferencesKey("send_hides_keyboard")
    private val followStreamingKey = booleanPreferencesKey("follow_streaming")
    private val showReasoningKey = booleanPreferencesKey("show_reasoning")
    private val expandProcessKey = booleanPreferencesKey("expand_process_by_default")
    private val pinnedWorkspacesKey = stringSetPreferencesKey("pinned_workspace_ids")

    val preferences: Flow<UiPreferences> = store.data
        .map { values ->
            UiPreferences(
                hapticsEnabled = values[hapticsKey] ?: true,
                sendHidesKeyboard = values[sendHidesKeyboardKey] ?: true,
                followStreaming = values[followStreamingKey] ?: true,
                showReasoning = values[showReasoningKey] ?: true,
                expandProcessByDefault = values[expandProcessKey] ?: false,
                pinnedWorkspaceIds = values[pinnedWorkspacesKey] ?: emptySet(),
            )
        }
        .catch { error ->
            if (error is IOException) emit(UiPreferences()) else throw error
        }

    suspend fun read(): UiPreferences = preferences.first()

    suspend fun set(toggle: UiToggle, enabled: Boolean) {
        store.edit { values ->
            when (toggle) {
                UiToggle.HAPTICS -> values[hapticsKey] = enabled
                UiToggle.SEND_HIDES_KEYBOARD -> values[sendHidesKeyboardKey] = enabled
                UiToggle.FOLLOW_STREAMING -> values[followStreamingKey] = enabled
                UiToggle.SHOW_REASONING -> values[showReasoningKey] = enabled
                UiToggle.EXPAND_PROCESS -> values[expandProcessKey] = enabled
            }
        }
    }

    suspend fun setWorkspacePinned(workspaceId: String, pinned: Boolean) {
        if (workspaceId.isBlank()) return
        store.edit { values ->
            val current = values[pinnedWorkspacesKey] ?: emptySet()
            values[pinnedWorkspacesKey] = if (pinned) current + workspaceId else current - workspaceId
        }
    }
}
