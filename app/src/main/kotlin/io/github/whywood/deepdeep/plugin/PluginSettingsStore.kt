package io.github.whywood.deepdeep.plugin

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class PluginSettingsStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    suspend fun readAll(pluginId: String): Map<String, String> = mutex.withLock {
        withContext(ioDispatcher) {
            val values = loadLocked()[pluginId].orEmpty()
            cache[pluginId] = values
            values
        }
    }

    suspend fun warm(pluginIds: Collection<String>) = mutex.withLock {
        withContext(ioDispatcher) {
            val all = loadLocked()
            pluginIds.forEach { pluginId -> cache[pluginId] = all[pluginId].orEmpty() }
        }
    }

    suspend fun set(pluginId: String, key: String, value: String) = mutex.withLock {
        withContext(ioDispatcher) {
            val all = loadLocked().toMutableMap()
            val values = all[pluginId].orEmpty().toMutableMap()
            values[key] = value
            all[pluginId] = values
            writeLocked(all)
            cache[pluginId] = values
        }
    }

    fun cached(pluginId: String): Map<String, String> = cache[pluginId].orEmpty()

    fun cachedInt(pluginId: String, key: String, default: Int): Int =
        cached(pluginId)[key]?.toIntOrNull() ?: default

    fun cachedBool(pluginId: String, key: String, default: Boolean): Boolean =
        cached(pluginId)[key]?.toBooleanStrictOrNull() ?: default

    fun cachedString(pluginId: String, key: String, default: String): String =
        cached(pluginId)[key] ?: default

    suspend fun int(pluginId: String, key: String, default: Int): Int =
        readAll(pluginId)[key]?.toIntOrNull() ?: default

    suspend fun bool(pluginId: String, key: String, default: Boolean): Boolean =
        readAll(pluginId)[key]?.toBooleanStrictOrNull() ?: default

    suspend fun string(pluginId: String, key: String, default: String): String =
        readAll(pluginId)[key] ?: default

    private fun loadLocked(): Map<String, Map<String, String>> {
        if (!file.isFile) return emptyMap()
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyMap()
        val plugins = root["plugins"] as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, Map<String, String>>()
        plugins.forEach { (pluginId, element) ->
            val values = element as? JsonObject ?: return@forEach
            val settings = LinkedHashMap<String, String>()
            values.forEach { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { settings[key] = it }
            }
            if (settings.isNotEmpty()) result[pluginId] = settings
        }
        return result
    }

    private fun writeLocked(all: Map<String, Map<String, String>>) {
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put(
                "plugins",
                buildJsonObject {
                    all.entries.sortedBy { it.key }.forEach { (pluginId, values) ->
                        put(
                            pluginId,
                            buildJsonObject {
                                values.entries.sortedBy { it.key }.forEach { (key, value) ->
                                    put(key, value)
                                }
                            },
                        )
                    }
                },
            )
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
