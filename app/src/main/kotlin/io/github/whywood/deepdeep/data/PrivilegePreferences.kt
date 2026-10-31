package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.privilege.parsePrivilegeLevel
import io.github.whywood.deepdeep.privilege.resolveMigratedLevel
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privilegeDataStore by preferencesDataStore(name = "privilege_settings")

class PrivilegePreferences(
    context: Context,
    private val legacyRootEnabled: suspend () -> Boolean,
) {
    private val store = context.applicationContext.privilegeDataStore
    private val levelKey = stringPreferencesKey("level")

    val level: Flow<PrivilegeLevel> = store.data
        .map { preferences -> preferences[levelKey]?.let(::parsePrivilegeLevel) ?: PrivilegeLevel.NORMAL }
        .catch { error -> if (error is IOException) emit(PrivilegeLevel.NORMAL) else throw error }

    suspend fun readLevel(): PrivilegeLevel = level.first()

    suspend fun writeLevel(level: PrivilegeLevel) {
        store.edit { it[levelKey] = level.name }
    }

    suspend fun migrateLegacyRoot() {
        val raw = store.data.first()[levelKey]
        val migrated = resolveMigratedLevel(raw?.let(::parsePrivilegeLevel), legacyRootEnabled())
        if (raw == migrated.name) return
        writeLevel(migrated)
    }
}
