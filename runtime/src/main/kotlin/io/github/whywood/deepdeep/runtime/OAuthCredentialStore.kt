package io.github.whywood.deepdeep.runtime

import dev.pi.ai.PiJson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

interface OAuthRecordStore {
    suspend fun readRawRecords(): Map<String, JsonElement>

    suspend fun saveRawRecord(providerId: String, credential: JsonElement)

    suspend fun deleteRecord(providerId: String)
}

class OAuthCredentialStore(private val secrets: EncryptedSecretStore) : OAuthRecordStore {
    val providerIds: Flow<Set<String>> = secrets.entries(OAUTH_PREFIX)

    suspend fun readRaw(): Map<String, JsonElement> = readRawRecords()

    suspend fun readRaw(providerId: String): JsonElement? =
        secrets.read(OAUTH_PREFIX, providerId)?.let { text ->
            runCatching { PiJson.parseToJsonElement(text) }.getOrNull()
        }

    suspend fun saveRaw(providerId: String, credential: JsonElement) {
        secrets.save(OAUTH_PREFIX, providerId, credential.toString())
    }

    suspend fun delete(providerId: String) {
        secrets.save(OAUTH_PREFIX, providerId, null)
    }

    override suspend fun readRawRecords(): Map<String, JsonElement> =
        secrets.readAll(OAUTH_PREFIX).mapNotNull { (providerId, text) ->
            runCatching { PiJson.parseToJsonElement(text) }.getOrNull()?.let { providerId to it }
        }.toMap()

    override suspend fun saveRawRecord(providerId: String, credential: JsonElement) {
        secrets.save(OAUTH_PREFIX, providerId, credential.toString())
    }

    override suspend fun deleteRecord(providerId: String) {
        secrets.save(OAUTH_PREFIX, providerId, null)
    }

    private companion object {
        const val OAUTH_PREFIX = "oauth_"
    }
}
