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

private val Context.capabilityPreferencesDataStore by preferencesDataStore(name = "capability_preferences")

class CapabilityPreferences(context: Context) {
    private val store = context.applicationContext.capabilityPreferencesDataStore
    private val readSmsKey = booleanPreferencesKey("read_sms_enabled")

    val readSmsEnabled: Flow<Boolean> = store.data
        .map { it[readSmsKey] ?: false }
        .catch { error ->
            if (error is IOException) emit(false) else throw error
        }

    suspend fun isReadSmsEnabled(): Boolean = readSmsEnabled.first()

    suspend fun setReadSmsEnabled(value: Boolean) {
        store.edit { it[readSmsKey] = value }
    }
}
