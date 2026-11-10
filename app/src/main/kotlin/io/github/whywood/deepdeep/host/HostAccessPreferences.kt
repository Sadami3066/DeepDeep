package io.github.whywood.deepdeep.host

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.runtime.host.HostAccess
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hostAccessDataStore by preferencesDataStore(name = "host_access")
private val Context.legacyPrivilegeDataStore by preferencesDataStore(name = "privilege_settings")

interface HostAccessStore {
    val selected: Flow<HostAccess>

    suspend fun setSelected(value: HostAccess)
}

class HostAccessPreferences(context: Context) : HostAccessStore {
    private val appContext = context.applicationContext
    private val store = appContext.hostAccessDataStore
    private val legacyStore = appContext.legacyPrivilegeDataStore
    private val selectedKey = stringPreferencesKey("selected")
    private val legacyLevelKey = stringPreferencesKey("level")

    override val selected: Flow<HostAccess> = store.data
        .map { preferences ->
            preferences[selectedKey]?.let(::parseAccess) ?: migrateLegacyLevel()
        }
        .catch { error -> if (error is IOException) emit(HostAccess.LOCAL) else throw error }

    override suspend fun setSelected(value: HostAccess) {
        store.edit { it[selectedKey] = value.name }
    }

    private suspend fun migrateLegacyLevel(): HostAccess {
        val raw = runCatching { legacyStore.data.first()[legacyLevelKey] }.getOrNull()
        val access = parseLegacyHostAccess(raw)
        setSelected(access)
        return access
    }

    private fun parseAccess(raw: String): HostAccess =
        HostAccess.entries.firstOrNull { it.name == raw } ?: HostAccess.LOCAL
}

internal fun parseLegacyHostAccess(raw: String?): HostAccess = when (raw) {
    "ADB", "SHIZUKU" -> HostAccess.ADB
    "ROOT" -> HostAccess.ROOT
    else -> HostAccess.LOCAL
}
