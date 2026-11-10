package io.github.whywood.deepdeep.host

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.data.PrivilegePreferences
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.runtime.host.HostAccess
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.hostAccessDataStore by preferencesDataStore(name = "host_access")

interface HostAccessStore {
    val selected: Flow<HostAccess>

    suspend fun setSelected(value: HostAccess)
}

class HostAccessPreferences(context: Context) : HostAccessStore {
    private val appContext = context.applicationContext
    private val store = appContext.hostAccessDataStore
    private val selectedKey = stringPreferencesKey("selected")
    private val legacy = PrivilegePreferences(appContext, { false })

    override val selected: Flow<HostAccess> = store.data
        .map { preferences ->
            preferences[selectedKey]?.let(::parseAccess) ?: migrateLegacyLevel()
        }
        .catch { error -> if (error is IOException) emit(HostAccess.LOCAL) else throw error }

    override suspend fun setSelected(value: HostAccess) {
        store.edit { it[selectedKey] = value.name }
    }

    private suspend fun migrateLegacyLevel(): HostAccess {
        val level = runCatching { legacy.readLevel() }.getOrDefault(PrivilegeLevel.NORMAL)
        val access = when (level) {
            PrivilegeLevel.NORMAL -> HostAccess.LOCAL
            PrivilegeLevel.ADB -> HostAccess.ADB
            PrivilegeLevel.ROOT -> HostAccess.ROOT
        }
        setSelected(access)
        return access
    }

    private fun parseAccess(raw: String): HostAccess =
        HostAccess.entries.firstOrNull { it.name == raw } ?: HostAccess.LOCAL
}
