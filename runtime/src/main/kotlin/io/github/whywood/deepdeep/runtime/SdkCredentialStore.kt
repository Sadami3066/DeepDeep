package io.github.whywood.deepdeep.runtime

import dev.pi.ai.PiJson
import dev.pi.ai.auth.ApiKeyCredential
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.auth.OAuthCredential
import dev.pi.ai.auth.oauth.OAuthCredential as OAuthToken
import dev.pi.ai.auth.oauth.oauthCredentialFromJson
import dev.pi.ai.auth.oauth.toJsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class ProviderAuthKind {
    API_KEY,
    OAUTH,
}

class SdkCredentialStore(
    private val apiKeys: ApiKeyRecordStore,
    private val oauth: OAuthRecordStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : CredentialStore {
    private val apiKeyCache = ConcurrentHashMap<String, String>()
    private val oauthCache = ConcurrentHashMap<String, JsonElement>()
    private val pendingWrites = ConcurrentHashMap.newKeySet<String>()
    private val authKindsState = MutableStateFlow<Map<String, ProviderAuthKind>>(emptyMap())
    val authKinds: StateFlow<Map<String, ProviderAuthKind>> = authKindsState.asStateFlow()
    private val revisionState = MutableStateFlow(0L)
    val revision: StateFlow<Long> = revisionState.asStateFlow()

    suspend fun preload(vararg providerIds: String) {
        for (providerId in providerIds) {
            if (providerId in pendingWrites) continue
            val key = apiKeys.readRecord(providerId)
            if (key.isNullOrBlank()) apiKeyCache.remove(providerId) else apiKeyCache[providerId] = key
            val raw = oauth.readRawRecords()[providerId]
            if (raw == null) oauthCache.remove(providerId) else oauthCache[providerId] = raw
        }
        publishKinds()
    }

    suspend fun preloadAll() {
        val pendingKeys = pendingWrites.toSet()
        val pendingApiKeys = pendingKeys.associateWith { apiKeyCache[it] }
        val pendingOauth = pendingKeys.associateWith { oauthCache[it] }
        apiKeyCache.clear()
        oauthCache.clear()
        apiKeyCache.putAll(apiKeys.readAllRecords().filterValues { it.isNotBlank() })
        oauthCache.putAll(oauth.readRawRecords())
        pendingKeys.forEach { providerId ->
            pendingApiKeys[providerId]?.let { apiKeyCache[providerId] = it } ?: apiKeyCache.remove(providerId)
            pendingOauth[providerId]?.let { oauthCache[providerId] = it } ?: oauthCache.remove(providerId)
        }
        publishKinds()
    }

    fun cachedApiKey(providerId: String): String? = apiKeyCache[providerId]?.takeIf { it.isNotBlank() }

    fun invalidate(providerId: String) {
        apiKeyCache.remove(providerId)
        oauthCache.remove(providerId)
        publishKinds()
    }

    fun kindOf(providerId: String): ProviderAuthKind? = when {
        oauthCache.containsKey(providerId) -> ProviderAuthKind.OAUTH
        apiKeyCache[providerId]?.isNotBlank() == true -> ProviderAuthKind.API_KEY
        else -> null
    }

    override fun read(providerId: String): Credential? {
        oauthCache[providerId]?.let { raw -> decodeBasic(raw)?.let { return it } }
        return apiKeyCache[providerId]?.takeIf { it.isNotBlank() }?.let { ApiKeyCredential(key = it) }
    }

    override fun readAll(): Map<String, Credential> {
        val result = LinkedHashMap<String, Credential>()
        apiKeyCache.forEach { (providerId, key) ->
            if (key.isNotBlank()) result[providerId] = ApiKeyCredential(key = key)
        }
        oauthCache.forEach { (providerId, raw) ->
            decodeBasic(raw)?.let { result[providerId] = it }
        }
        return result
    }

    override fun readApiKey(providerId: String): String? = apiKeyCache[providerId]?.takeIf { it.isNotBlank() }

    override fun readEnv(providerId: String): Map<String, String>? = null

    override fun list(): List<CredentialInfo> = keys().map { providerId ->
        CredentialInfo(
            providerId = providerId,
            type = if (oauthCache.containsKey(providerId)) "oauth" else "api_key",
        )
    }

    override fun keys(): List<String> = (apiKeyCache.keys + oauthCache.keys).distinct()

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
        apiKeyCache[providerId] = trimmed
        oauthCache.remove(providerId)
        publishKinds()
        pendingWrites.add(providerId)
        scope.launch(ioDispatcher) {
            try {
                oauth.deleteRecord(providerId)
                apiKeys.saveRecord(providerId, trimmed)
            } finally {
                pendingWrites.remove(providerId)
            }
        }
    }

    override fun write(providerId: String, credential: Credential) {
        when (credential) {
            is ApiKeyCredential -> writeApiKey(providerId, credential.key.orEmpty(), credential.env)
            is OAuthCredential -> {
                val rich = OAuthToken(
                    refresh = credential.refresh,
                    access = credential.access,
                    expires = credential.expires,
                )
                writeRawCredential(providerId, rich.toJsonObject())
            }
        }
    }

    override fun writeRawCredential(providerId: String, credential: JsonElement) {
        if (credential !is JsonObject) return
        oauthCache[providerId] = credential
        apiKeyCache.remove(providerId)
        publishKinds()
        pendingWrites.add(providerId)
        scope.launch(ioDispatcher) {
            try {
                apiKeys.saveRecord(providerId, null)
                oauth.saveRawRecord(providerId, credential)
            } finally {
                pendingWrites.remove(providerId)
            }
        }
    }

    override fun delete(providerId: String) {
        apiKeyCache.remove(providerId)
        oauthCache.remove(providerId)
        publishKinds()
        pendingWrites.add(providerId)
        scope.launch(ioDispatcher) {
            try {
                apiKeys.saveRecord(providerId, null)
                oauth.deleteRecord(providerId)
            } finally {
                pendingWrites.remove(providerId)
            }
        }
    }

    override fun readRaw(): Map<String, JsonElement> = oauthCache.toMap()

    private fun publishKinds() {
        val kinds = LinkedHashMap<String, ProviderAuthKind>()
        apiKeyCache.keys.forEach { providerId -> kinds[providerId] = ProviderAuthKind.API_KEY }
        oauthCache.keys.forEach { providerId -> kinds[providerId] = ProviderAuthKind.OAUTH }
        authKindsState.value = kinds
        revisionState.value = revisionState.value + 1
    }

    private fun decodeBasic(raw: JsonElement): OAuthCredential? {
        val rich = oauthCredentialFromJson(raw)
        if (rich != null) {
            return OAuthCredential(refresh = rich.refresh, access = rich.access, expires = rich.expires)
        }
        return runCatching {
            PiJson.decodeFromJsonElement(OAuthCredential.serializer(), raw)
        }.getOrNull()
    }
}
