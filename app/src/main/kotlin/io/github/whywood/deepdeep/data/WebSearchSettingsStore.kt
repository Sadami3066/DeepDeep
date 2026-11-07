package io.github.whywood.deepdeep.data

import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import kotlinx.coroutines.flow.Flow

class WebSearchSettingsStore(private val secrets: EncryptedSecretStore) {
    fun hasKey(providerId: String): Flow<Boolean> = secrets.has(PREFIX, providerId)

    suspend fun key(providerId: String): String? = secrets.read(PREFIX, providerId)

    suspend fun readAll(): Map<String, String> = secrets.readAll(PREFIX)

    suspend fun save(providerId: String, key: String?) = secrets.save(PREFIX, providerId, key)

    companion object {
        const val EXA = "exa"
        const val TAVILY = "tavily"
        const val BRAVE = "brave"
        const val SERPER = "serper"

        val providers = listOf(EXA, TAVILY, BRAVE, SERPER)

        private const val PREFIX = "web_search_key_"
    }
}
