package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.linuxSettingsDataStore by preferencesDataStore(name = "linux_settings")

class LinuxSettingsStore(context: Context) {
    private val store = context.applicationContext.linuxSettingsDataStore
    private val enabledKey = booleanPreferencesKey("assistant_enabled")

    val enabled: Flow<Boolean> = store.data
        .map { it[enabledKey] ?: false }
        .catch { error ->
            if (error is IOException) emit(false) else throw error
        }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[enabledKey] = value }
    }
}
