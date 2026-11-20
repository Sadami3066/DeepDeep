package io.github.whywood.superpi.data.relay

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.superpi.runtime.EncryptedSecretStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.relayDataStore by preferencesDataStore(name = "relay")

class RelayStore(context: Context) : RelayAccountStore {
    private val appContext = context.applicationContext
    private val store = appContext.relayDataStore
    private val secrets = EncryptedSecretStore(appContext)
    private val accountKey = stringPreferencesKey("account")
    private val allowRemoteKey = stringPreferencesKey("allowRemote")

    override val account: Flow<RelayAccount?> = store.data
        .map { values -> RelayConfigCodec.decode(values[accountKey]) }
        .catch { error ->
            if (error is IOException) emit(null) else throw error
        }

    val allowRemote: Flow<Boolean> = store.data
        .map { values -> values[allowRemoteKey] == "true" }
        .catch { error ->
            if (error is IOException) emit(false) else throw error
        }

    override suspend fun read(): RelayAccount? = account.first()

    suspend fun readAllowRemote(): Boolean = allowRemote.first()

    suspend fun setAllowRemote(value: Boolean) {
        store.edit { values ->
            values[allowRemoteKey] = if (value) "true" else "false"
        }
    }

    override suspend fun token(): String? = secrets.read(TOKEN_PREFIX, TOKEN_ID)

    override suspend fun save(account: RelayAccount, token: String) {
        secrets.save(TOKEN_PREFIX, TOKEN_ID, token)
        store.edit { values ->
            values[accountKey] = RelayConfigCodec.encode(account)
        }
    }

    override suspend fun clear() {
        secrets.save(TOKEN_PREFIX, TOKEN_ID, null)
        store.edit { values ->
            values.remove(accountKey)
        }
    }

    companion object {
        private const val TOKEN_PREFIX = "relay"
        private const val TOKEN_ID = "token"
    }
}
