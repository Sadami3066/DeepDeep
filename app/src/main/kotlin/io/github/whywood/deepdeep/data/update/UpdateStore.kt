package io.github.whywood.deepdeep.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONObject

data class UpdateSnapshot(
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val release: UpdateRelease? = null,
)

interface UpdateStore {
    suspend fun read(): UpdateSnapshot

    suspend fun write(snapshot: UpdateSnapshot)
}

private val Context.updateStateDataStore by preferencesDataStore(name = "update_state")

private val KEY_LAST_SUCCESS = longPreferencesKey("last_success_at")
private val KEY_LAST_ATTEMPT = longPreferencesKey("last_attempt_at")
private val KEY_RELEASE = stringPreferencesKey("release")

class DataStoreUpdateStore(context: Context) : UpdateStore {
    private val store = context.updateStateDataStore

    override suspend fun read(): UpdateSnapshot {
        val preferences = store.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()
        return UpdateSnapshot(
            lastSuccessAt = preferences[KEY_LAST_SUCCESS],
            lastAttemptAt = preferences[KEY_LAST_ATTEMPT],
            release = preferences[KEY_RELEASE]?.let(::decodeRelease),
        )
    }

    override suspend fun write(snapshot: UpdateSnapshot) {
        store.edit { preferences ->
            if (snapshot.lastSuccessAt != null) {
                preferences[KEY_LAST_SUCCESS] = snapshot.lastSuccessAt
            } else {
                preferences.remove(KEY_LAST_SUCCESS)
            }
            if (snapshot.lastAttemptAt != null) {
                preferences[KEY_LAST_ATTEMPT] = snapshot.lastAttemptAt
            } else {
                preferences.remove(KEY_LAST_ATTEMPT)
            }
            val release = snapshot.release
            if (release != null) {
                preferences[KEY_RELEASE] = encodeRelease(release)
            } else {
                preferences.remove(KEY_RELEASE)
            }
        }
    }
}

private fun encodeRelease(release: UpdateRelease): String = JSONObject().apply {
    put("tag", release.tag)
    put("versionName", release.versionName)
    put("notes", release.notes)
    put("apkUrl", release.apkUrl)
    put("apkSize", release.apkSize)
    put("sha256", release.sha256)
    put("pageUrl", release.pageUrl)
}.toString()

private fun decodeRelease(raw: String): UpdateRelease? = runCatching {
    val json = JSONObject(raw)
    UpdateRelease(
        tag = json.getString("tag"),
        versionName = json.getString("versionName"),
        notes = json.optString("notes"),
        apkUrl = json.getString("apkUrl"),
        apkSize = json.optLong("apkSize"),
        sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
        pageUrl = json.getString("pageUrl"),
    )
}.getOrNull()
