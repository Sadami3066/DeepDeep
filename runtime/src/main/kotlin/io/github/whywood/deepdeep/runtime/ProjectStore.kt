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

private val Context.projectStore by preferencesDataStore(name = "projects")

class ProjectStore(context: Context) {
    private val store = context.applicationContext.projectStore
    private val key = stringPreferencesKey("entries")

    val projects: Flow<List<RuntimeProject>> = store.data
        .map { values -> decode(values[key]) }
        .catch { error ->
            if (error is IOException) emit(emptyList()) else throw error
        }

    suspend fun read(): List<RuntimeProject> = projects.first()

    suspend fun add(project: RuntimeProject) {
        store.edit { values ->
            val current = decode(values[key]).toMutableList()
            current.removeAll { it.id == project.id }
            current.add(project)
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

    private fun decode(raw: String?): List<RuntimeProject> {
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
                        RuntimeProject(
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

    private fun encode(projects: List<RuntimeProject>): String {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(
                JSONObject()
                    .put("id", project.id)
                    .put("name", project.name)
                    .put("cwd", project.cwd)
                    .put("createdAt", project.createdAt),
            )
        }
        return array.toString()
    }
}
