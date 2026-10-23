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
    val pinnedWorkspaceIds: Set<String> = emptySet(),
)

enum class UiToggle {
    AUTO_COLLAPSE_PROCESS,
}

class AppPreferences(context: Context) {
    private val store = context.applicationContext.appPreferencesDataStore
    private val autoCollapseKey = booleanPreferencesKey("auto_collapse_process")
    private val pinnedWorkspacesKey = stringSetPreferencesKey("pinned_workspace_ids")

    val preferences: Flow<UiPreferences> = store.data
        .map { values ->
            UiPreferences(
                autoCollapseProcess = values[autoCollapseKey] ?: true,
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
