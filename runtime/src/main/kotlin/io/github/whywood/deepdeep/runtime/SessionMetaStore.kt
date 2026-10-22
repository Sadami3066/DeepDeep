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
import org.json.JSONObject

private val Context.sessionMetaStore by preferencesDataStore(name = "session_meta")

data class SessionMeta(
    val titleOverride: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long = 0,
)

class SessionMetaStore(context: Context) {
    private val store = context.applicationContext.sessionMetaStore
    private val key = stringPreferencesKey("entries")

    val metas: Flow<Map<String, SessionMeta>> = store.data
        .map { values -> decode(values[key]) }
        .catch { error ->
            if (error is IOException) emit(emptyMap()) else throw error
        }

    suspend fun read(): Map<String, SessionMeta> = metas.first()

    suspend fun update(sessionId: String, transform: (SessionMeta) -> SessionMeta) {
        store.edit { values ->
            val current = decode(values[key]).toMutableMap()
            val next = transform(current[sessionId] ?: SessionMeta())
            current[sessionId] = next.copy(updatedAt = next.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            values[key] = encode(current)
        }
    }

    suspend fun remove(sessionId: String) {
        store.edit { values ->
            val current = decode(values[key]).toMutableMap()
            current.remove(sessionId)
            values[key] = encode(current)
        }
    }

    private fun decode(raw: String?): Map<String, SessionMeta> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { id ->
                val item = root.optJSONObject(id) ?: JSONObject()
                SessionMeta(
                    titleOverride = item.optString("title").takeIf { it.isNotBlank() },
                    pinned = item.optBoolean("pinned", false),
                    archived = item.optBoolean("archived", false),
                    updatedAt = item.optLong("updatedAt", 0),
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun encode(entries: Map<String, SessionMeta>): String {
        val root = JSONObject()
        entries.forEach { (id, meta) ->
            root.put(
                id,
                JSONObject()
                    .put("title", meta.titleOverride ?: "")
                    .put("pinned", meta.pinned)
                    .put("archived", meta.archived)
                    .put("updatedAt", meta.updatedAt),
            )
        }
        return root.toString()
    }
}

class SessionRepository(
    private val engine: PiSessionEngine,
    private val metaStore: SessionMetaStore,
) {
    suspend fun list(includeArchived: Boolean = false): List<ConversationSummary> {
        val metas = metaStore.read()
        return engine.listSessions()
            .map { base ->
                val meta = metas[base.id]
                base.copy(
                    title = meta?.titleOverride ?: base.title,
                    pinned = meta?.pinned ?: false,
                    archived = meta?.archived ?: false,
                )
            }
            .filter { includeArchived || !it.archived }
            .sortedWith(compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.updatedAt })
    }

    suspend fun archivedCount(): Int = list(includeArchived = true).count { it.archived }

    suspend fun rename(id: String, title: String) {
        metaStore.update(id) { it.copy(titleOverride = title.trim().takeIf { value -> value.isNotEmpty() }) }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        metaStore.update(id) { it.copy(pinned = pinned) }
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        metaStore.update(id) { it.copy(archived = archived) }
    }

    suspend fun delete(id: String) {
        val target = engine.listSessions().firstOrNull { it.id == id }
        if (target != null) engine.deleteSessionFile(target.filePath)
        metaStore.remove(id)
    }
}
