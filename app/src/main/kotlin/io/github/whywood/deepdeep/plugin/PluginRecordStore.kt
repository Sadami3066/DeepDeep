package io.github.whywood.deepdeep.plugin

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class PluginRecord(
    val id: String,
    val version: String,
    val installedAt: Long,
    val skillIds: List<String> = emptyList(),
)

data class PluginRecords(
    val records: Map<String, PluginRecord> = emptyMap(),
    val error: String? = null,
)

class PluginRecordStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun load(): PluginRecords = mutex.withLock { withContext(ioDispatcher) { loadLocked() } }

    suspend fun upsert(record: PluginRecord) = mutex.withLock {
        withContext(ioDispatcher) {
            val current = loadLocked()
            writeLocked(current.records + (record.id to record))
        }
    }

    suspend fun remove(id: String) = mutex.withLock {
        withContext(ioDispatcher) {
            val current = loadLocked()
            if (id !in current.records) return@withContext
            writeLocked(current.records - id)
        }
    }

    private fun loadLocked(): PluginRecords {
        if (!file.isFile) return PluginRecords()
        val text = runCatching { file.readText() }.getOrElse { error ->
            return PluginRecords(error = "plugins.json 读取失败：${error.message ?: "unknown"}")
        }
        if (text.isBlank()) return PluginRecords()
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse { error ->
            return PluginRecords(error = "plugins.json 解析失败：${error.message ?: "invalid JSON"}")
        }
        val plugins = root["plugins"] as? JsonArray ?: return PluginRecords()
        val records = LinkedHashMap<String, PluginRecord>()
        plugins.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val id = item.string("id") ?: return@forEach
            val version = item.string("version") ?: return@forEach
            val installedAt = (item["installedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            val skills = (item["skillIds"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
            records[id] = PluginRecord(id, version, installedAt, skills)
        }
        return PluginRecords(records)
    }

    private fun writeLocked(records: Map<String, PluginRecord>) {
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put(
                "plugins",
                buildJsonArray {
                    records.values.sortedBy { it.id }.forEach { record ->
                        add(
                            buildJsonObject {
                                put("id", record.id)
                                put("version", record.version)
                                put("installedAt", record.installedAt)
                                put(
                                    "skillIds",
                                    buildJsonArray {
                                        record.skillIds.forEach { add(JsonPrimitive(it)) }
                                    },
                                )
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

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
