package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal data class TrustedPublisher(
    val keyId: String,
    val name: String,
    val publicKey: String,
    val firstSeenAt: Long = 0,
)

internal sealed interface TrustResult {
    data class Added(val publisher: TrustedPublisher) : TrustResult

    data class Known(val publisher: TrustedPublisher) : TrustResult

    data class Conflict(val existing: TrustedPublisher) : TrustResult
}

internal class PublisherTrustStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun publishers(): List<TrustedPublisher> = mutex.withLock {
        withContext(ioDispatcher) { loadLocked().values.sortedBy { it.keyId } }
    }

    suspend fun trusted(keyId: String): TrustedPublisher? = mutex.withLock {
        withContext(ioDispatcher) { loadLocked()[keyId] }
    }

    suspend fun trust(publisher: TrustedPublisher): TrustResult = mutex.withLock {
        withContext(ioDispatcher) {
            val current = loadLocked().toMutableMap()
            val existing = current[publisher.keyId]
            when {
                existing == null -> {
                    current[publisher.keyId] = publisher
                    writeLocked(current)
                    TrustResult.Added(publisher)
                }

                existing.publicKey == publisher.publicKey -> TrustResult.Known(existing)

                else -> TrustResult.Conflict(existing)
            }
        }
    }

    suspend fun untrust(keyId: String): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val current = loadLocked().toMutableMap()
            if (current.remove(keyId) == null) {
                false
            } else {
                writeLocked(current)
                true
            }
        }
    }

    private fun loadLocked(): Map<String, TrustedPublisher> {
        if (!file.isFile) return emptyMap()
        val root = JsonFiles.read(file)
        if (root == null) {
            resetLocked()
            return emptyMap()
        }
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 0
        val publishers = root["publishers"] as? JsonObject
        if (schemaVersion != SCHEMA_VERSION || publishers == null) {
            resetLocked()
            return emptyMap()
        }
        val result = LinkedHashMap<String, TrustedPublisher>()
        publishers.forEach { (keyId, element) ->
            val entry = element as? JsonObject ?: return@forEach
            val name = entry.string("name") ?: return@forEach
            val publicKey = entry.string("publicKey") ?: return@forEach
            val firstSeenAt = (entry["firstSeenAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            result[keyId] = TrustedPublisher(keyId, name, publicKey, firstSeenAt)
        }
        return result
    }

    private fun resetLocked() {
        JsonFiles.backup(file, CORRUPT_SUFFIX)
        writeLocked(emptyMap())
    }

    private fun writeLocked(publishers: Map<String, TrustedPublisher>) {
        JsonFiles.write(
            file,
            buildJsonObject {
                put("schemaVersion", SCHEMA_VERSION)
                putJsonObject("publishers") {
                    publishers.values.sortedBy { it.keyId }.forEach { publisher ->
                        putJsonObject(publisher.keyId) {
                            put("name", publisher.name)
                            put("publicKey", publisher.publicKey)
                            put("firstSeenAt", publisher.firstSeenAt)
                        }
                    }
                }
            },
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val SCHEMA_VERSION = 1

        const val CORRUPT_SUFFIX = ".corrupt"
    }
}
