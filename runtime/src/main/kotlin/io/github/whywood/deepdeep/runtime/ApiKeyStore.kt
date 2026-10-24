package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.Flow

interface ApiKeyRecordStore {
    suspend fun readRecord(providerId: String): String?

    suspend fun readAllRecords(): Map<String, String>

    suspend fun saveRecord(providerId: String, key: String?)
}

class ApiKeyStore(private val secrets: EncryptedSecretStore) : ApiKeyRecordStore {
    val providerIds: Flow<Set<String>> = secrets.entries(KEY_PREFIX)

    fun hasKey(providerId: String): Flow<Boolean> = secrets.has(KEY_PREFIX, providerId)

    suspend fun read(providerId: String): String? = secrets.read(KEY_PREFIX, providerId)

    suspend fun readAll(): Map<String, String> = secrets.readAll(KEY_PREFIX)

    suspend fun save(providerId: String, apiKey: String?) {
        secrets.save(KEY_PREFIX, providerId, apiKey?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun readRecord(providerId: String): String? = secrets.read(KEY_PREFIX, providerId)

    override suspend fun readAllRecords(): Map<String, String> = secrets.readAll(KEY_PREFIX)

    override suspend fun saveRecord(providerId: String, key: String?) {
        secrets.save(KEY_PREFIX, providerId, key?.trim()?.takeIf { it.isNotEmpty() })
    }

    private companion object {
        const val KEY_PREFIX = "api_key_"
    }
}
