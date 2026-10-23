package io.github.whywood.deepdeep.runtime

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.workspaceStore by preferencesDataStore(name = "workspaces")

class WorkspaceStore(context: Context) {
    private val store = context.applicationContext.workspaceStore
    private val key = stringPreferencesKey("entries")

    val workspaces: Flow<List<RuntimeWorkspace>> = store.data
        .map { values -> decode(values[key]) }
        .catch { error ->
            if (error is IOException) emit(emptyList()) else throw error
        }

    suspend fun read(): List<RuntimeWorkspace> = workspaces.first()

    suspend fun add(workspace: RuntimeWorkspace) {
        store.edit { values ->
            val current = decode(values[key]).toMutableList()
            current.removeAll { it.id == workspace.id }
            current.add(workspace)
            values[key] = encode(current)
        }
    }

    suspend fun rename(id: String, name: String) {
        store.edit { values ->
            val current = decode(values[key]).map {
                if (it.id == id) it.copy(name = name) else it
            }
            values[key] = encode(current)
        }
    }

    suspend fun remove(id: String) {
        store.edit { values ->
            values[key] = encode(decode(values[key]).filterNot { it.id == id })
        }
    }

    private fun decode(raw: String?): List<RuntimeWorkspace> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: id
                    val cwd = item.optString("cwd")
                    if (cwd.isBlank()) continue
                    add(
                        RuntimeWorkspace(
                            id = id,
                            name = name,
                            cwd = cwd,
                            createdAt = item.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(workspaces: List<RuntimeWorkspace>): String {
        val array = JSONArray()
        workspaces.forEach { workspace ->
            array.put(
                JSONObject()
                    .put("id", workspace.id)
                    .put("name", workspace.name)
                    .put("cwd", workspace.cwd)
                    .put("createdAt", workspace.createdAt),
            )
        }
        return array.toString()
    }
}
