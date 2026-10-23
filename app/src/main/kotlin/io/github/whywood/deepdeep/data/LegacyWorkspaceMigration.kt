package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import java.io.File
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.legacyProjects by preferencesDataStore(name = "projects")

object LegacyWorkspaceMigration {
    suspend fun migrate(context: Context, workspaceStore: WorkspaceStore) {
        val appContext = context.applicationContext
        val legacyFile = File(appContext.filesDir, "datastore/projects.preferences_pb")
        if (!legacyFile.exists()) return
        if (workspaceStore.read().isNotEmpty()) return
        val raw = runCatching {
            appContext.legacyProjects.data
                .map { values -> values[stringPreferencesKey("entries")] }
                .catch { emit(null) }
                .first()
        }.getOrNull() ?: return
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: id
            val cwd = item.optString("cwd").replace("/files/projects/", "/files/workspaces/")
            if (cwd.isBlank()) continue
            workspaceStore.add(
                io.github.whywood.deepdeep.runtime.RuntimeWorkspace(
                    id = id,
                    name = name,
                    cwd = cwd,
                    createdAt = item.optLong("createdAt", 0L),
                ),
            )
        }
    }
}
