package io.github.whywood.deepdeep.plugin.host.extensions.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal class PluginSettingsStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _values = MutableStateFlow(readFromDisk())

    fun observe(key: String? = null): Flow<Map<String, String>> =
        _values.map { values ->
            if (key == null) {
                values
            } else {
                values[key]?.let { mapOf(key to it) }.orEmpty()
            }
        }.distinctUntilChanged()

    suspend fun get(key: String): String? = mutex.withLock { _values.value[key] }

    suspend fun put(key: String, value: String) = update { it + (key to value) }

    suspend fun remove(key: String) = update { it - key }

    suspend fun clear() = update { emptyMap() }

    suspend fun keys(): Set<String> = mutex.withLock { _values.value.keys.toSet() }

    suspend fun snapshot(): Map<String, String> = mutex.withLock { _values.value }

    suspend fun getJson(key: String): JsonObject? {
        val raw = get(key) ?: return null
        return runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    suspend fun putJson(key: String, value: JsonObject) {
        put(key, value.toString())
    }

    private suspend fun update(transform: (Map<String, String>) -> Map<String, String>) = mutex.withLock {
        val next = transform(_values.value)
        withContext(ioDispatcher) { writeAtomic(next) }
        _values.value = next
    }

    private fun readFromDisk(): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val values = root["values"] as? JsonObject ?: return@runCatching emptyMap()
            values.mapNotNull { (key, element) ->
                runCatching { key to element.jsonPrimitive.content }.getOrNull()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeAtomic(values: Map<String, String>) {
        file.parentFile?.mkdirs()
        val valuesObject = JsonObject(values.mapValues { (_, value) -> JsonPrimitive(value) })
        val payload = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("values", valuesObject)
        }.toString()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(payload)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal class PluginSettingsServiceImpl(
    private val store: PluginSettingsStore,
) : io.github.whywood.deepdeep.plugin.api.PluginSettingsService {
    override suspend fun get(key: String): String? = store.get(key)

    override suspend fun put(key: String, value: String) = store.put(key, value)

    override suspend fun remove(key: String) = store.remove(key)

    override suspend fun clear() = store.clear()

    override suspend fun keys(): Set<String> = store.keys()

    override suspend fun snapshot(): Map<String, String> = store.snapshot()

    override suspend fun getJson(key: String): JsonObject? = store.getJson(key)

    override suspend fun putJson(key: String, value: JsonObject) = store.putJson(key, value)

    override fun observe(key: String?): Flow<Map<String, String>> = store.observe(key)
}
