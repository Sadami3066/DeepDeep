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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
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
    private val authKindsState = MutableStateFlow<Map<String, ProviderAuthKind>>(emptyMap())
    val authKinds: StateFlow<Map<String, ProviderAuthKind>> = authKindsState.asStateFlow()
    private val revisionState = MutableStateFlow(0L)
    val revision: StateFlow<Long> = revisionState.asStateFlow()

    private val writeLock = Any()
    private val writeRevisions = HashMap<String, Long>()
    private val writeCounts = HashMap<String, Int>()
    private val writeTails = HashMap<String, Job>()

    suspend fun preload(vararg providerIds: String) {
        for (providerId in providerIds) {
            if (isWritePending(providerId)) continue
            val key = apiKeys.readRecord(providerId)
            if (key.isNullOrBlank()) apiKeyCache.remove(providerId) else apiKeyCache[providerId] = key
            val raw = oauth.readRawRecords()[providerId]
            if (raw == null) oauthCache.remove(providerId) else oauthCache[providerId] = raw
        }
        publishKinds()
    }

    suspend fun preloadAll() {
        val pendingKeys = synchronized(writeLock) { writeCounts.keys.toSet() }
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

    suspend fun awaitCommit(providerId: String) {
        while (true) {
            val tail = synchronized(writeLock) { writeTails[providerId] } ?: return
            tail.join()
        }
    }

    suspend fun awaitAll() {
        while (true) {
            val tails = synchronized(writeLock) { writeTails.values.toList() }
            if (tails.isEmpty()) return
            tails.joinAll()
        }
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
        enqueueWrite(providerId) {
            oauth.deleteRecord(providerId)
            apiKeys.saveRecord(providerId, trimmed)
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
        enqueueWrite(providerId) {
            apiKeys.saveRecord(providerId, null)
            oauth.saveRawRecord(providerId, credential)
        }
    }

    override fun delete(providerId: String) {
        apiKeyCache.remove(providerId)
        oauthCache.remove(providerId)
        publishKinds()
        enqueueWrite(providerId) {
            apiKeys.saveRecord(providerId, null)
            oauth.deleteRecord(providerId)
        }
    }

    override fun readRaw(): Map<String, JsonElement> = oauthCache.toMap()

    private fun enqueueWrite(providerId: String, write: suspend () -> Unit) {
        val job: Job
        synchronized(writeLock) {
            val revision = (writeRevisions[providerId] ?: 0L) + 1L
            writeRevisions[providerId] = revision
            writeCounts[providerId] = (writeCounts[providerId] ?: 0) + 1
            val previous = writeTails[providerId]
            job = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                try {
                    previous?.join()
                    if (isLatestRevision(providerId, revision)) write()
                } finally {
                    finishWrite(providerId)
                }
            }
            writeTails[providerId] = job
        }
        job.start()
    }

    private fun isLatestRevision(providerId: String, revision: Long): Boolean = synchronized(writeLock) {
        writeRevisions[providerId] == revision
    }

    private fun isWritePending(providerId: String): Boolean = synchronized(writeLock) {
        (writeCounts[providerId] ?: 0) > 0
    }

    private fun finishWrite(providerId: String) {
        synchronized(writeLock) {
            val remaining = (writeCounts[providerId] ?: 1) - 1
            if (remaining > 0) {
                writeCounts[providerId] = remaining
            } else {
                writeCounts.remove(providerId)
                writeTails.remove(providerId)
            }
        }
    }

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
