package io.github.whywood.deepdeep.data

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.preferences.core.Preferences
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
    private val sharedStorageKey = booleanPreferencesKey("shared_storage_enabled")
    private val rootEnabledKey = booleanPreferencesKey("root_enabled")

    val enabled: Flow<Boolean> = flag(enabledKey, linuxEnabledDefault())
    val sharedStorageEnabled: Flow<Boolean> = flag(sharedStorageKey)
    val rootEnabled: Flow<Boolean> = flag(rootEnabledKey)

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[enabledKey] = value }
    }

    suspend fun isRootEnabled(): Boolean = rootEnabled.first()

    suspend fun isSharedStorageEnabled(): Boolean = sharedStorageEnabled.first()

    suspend fun setSharedStorageEnabled(value: Boolean) {
        store.edit { it[sharedStorageKey] = value }
    }

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> = store.data
        .map { it[key] ?: default }
        .catch { error ->
            if (error is IOException) emit(default) else throw error
        }
}

internal fun linuxEnabledDefault(): Boolean = true

fun isSharedStorageGranted(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
