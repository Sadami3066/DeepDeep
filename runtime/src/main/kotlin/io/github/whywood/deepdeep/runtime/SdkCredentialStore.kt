package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.ApiKeyCredential
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

class SdkCredentialStore(
    private val apiKeys: ApiKeyStore,
    private val scope: CoroutineScope,
) : CredentialStore {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun preload(vararg providerIds: String) {
        for (providerId in providerIds) {
            val key = apiKeys.read(providerId)
            if (key.isNullOrBlank()) cache.remove(providerId) else cache[providerId] = key
        }
    }

    fun invalidate(providerId: String) {
        cache.remove(providerId)
    }

    override fun read(providerId: String): Credential? =
        cache[providerId]?.takeIf { it.isNotBlank() }?.let { ApiKeyCredential(key = it) }

    override fun readAll(): Map<String, Credential> = cache.mapValues { (_, key) -> ApiKeyCredential(key = key) }

    override fun readApiKey(providerId: String): String? = cache[providerId]?.takeIf { it.isNotBlank() }

    override fun readEnv(providerId: String): Map<String, String>? = null

    override fun list(): List<CredentialInfo> = cache.keys.map { CredentialInfo(it, "api_key") }

    override fun keys(): List<String> = cache.keys.toList()

    override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? {
        val current = read(providerId)
        val next = transform(current) ?: return null
        write(providerId, next)
        return next
    }

    override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            delete(providerId)
            return
        }
        cache[providerId] = trimmed
        scope.launch(Dispatchers.IO) { apiKeys.save(providerId, trimmed) }
    }

    override fun write(providerId: String, credential: Credential) {
        when (credential) {
            is ApiKeyCredential -> writeApiKey(providerId, credential.key.orEmpty(), credential.env)
            else -> error("Only API key credentials are supported")
        }
    }

    override fun writeRawCredential(providerId: String, credential: JsonElement) {
        error("Raw credentials are not supported")
    }

    override fun delete(providerId: String) {
        cache.remove(providerId)
        scope.launch(Dispatchers.IO) { apiKeys.save(providerId, null) }
    }

    override fun readRaw(): Map<String, JsonElement> = emptyMap()

    fun blockingRead(providerId: String): String? = runBlocking { apiKeys.read(providerId) }
}
