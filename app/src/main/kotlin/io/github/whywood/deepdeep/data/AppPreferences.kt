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
    val autoCollapseProcess: Boolean = true,
    val autoRetry: Boolean = true,
    val backgroundJobWake: Boolean = true,
    val pinnedWorkspaceIds: Set<String> = emptySet(),
)

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

    val preferences: Flow<UiPreferences> = store.data
        .map { values ->
            UiPreferences(
                autoCollapseProcess = values[autoCollapseKey] ?: true,
                autoRetry = values[autoRetryKey] ?: true,
                backgroundJobWake = values[backgroundJobWakeKey] ?: true,
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
                UiToggle.AUTO_COLLAPSE_PROCESS -> values[autoCollapseKey] = enabled
                UiToggle.AUTO_RETRY -> values[autoRetryKey] = enabled
                UiToggle.BACKGROUND_JOB_WAKE -> values[backgroundJobWakeKey] = enabled
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
