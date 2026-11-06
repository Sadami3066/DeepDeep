package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class ExternalPluginRecordStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun load(): Map<String, ExternalPluginRecord> = mutex.withLock {
        withContext(ioDispatcher) { loadLocked() }
    }

    suspend fun records(): List<ExternalPluginRecord> = load().values.sortedBy { it.id }

    suspend fun get(pluginId: String): ExternalPluginRecord? = load()[pluginId]

    suspend fun upsert(record: ExternalPluginRecord) {
        mutate { current -> current + (record.id to record) }
    }

    suspend fun remove(pluginId: String): Boolean {
        var removed = false
        mutate { current ->
            if (current.containsKey(pluginId)) {
                removed = true
                current - pluginId
            } else {
                current
            }
        }
        return removed
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean {
        var updated = false
        mutate { current ->
            val record = current[pluginId]
            if (record == null || record.enabled == enabled) {
                current
            } else {
                updated = true
                current + (pluginId to record.copy(enabled = enabled))
            }
        }
        return updated
    }

    suspend fun mutate(transform: (Map<String, ExternalPluginRecord>) -> Map<String, ExternalPluginRecord>) =
        mutex.withLock {
            withContext(ioDispatcher) {
                writeLocked(transform(loadLocked()))
            }
        }

    private fun loadLocked(): Map<String, ExternalPluginRecord> {
        if (!file.isFile) return emptyMap()
        val root = JsonFiles.read(file)
        if (root == null) {
            resetLocked()
            return emptyMap()
        }
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 0
        if (schemaVersion != SCHEMA_VERSION) {
            resetLocked()
            return emptyMap()
        }
        val plugins = root["plugins"] as? JsonArray
        if (plugins == null) {
            resetLocked()
            return emptyMap()
        }
        val result = LinkedHashMap<String, ExternalPluginRecord>()
        plugins.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val record = parseRecord(item) ?: return@forEach
            result[record.id] = record
        }
        return result
    }

    private fun parseRecord(json: JsonObject): ExternalPluginRecord? {
        val id = json.string("id") ?: return null
        val version = json.string("version") ?: return null
        val installPath = json.string("installPath") ?: return null
        val activeBundleSha256 = json.string("activeBundleSha256") ?: return null
        val publisherKeyId = json.string("publisherKeyId") ?: return null
        return ExternalPluginRecord(
            id = id,
            version = version,
            installedAt = json.long("installedAt") ?: 0L,
            enabled = json.boolean("enabled") ?: true,
            skillIds = json.stringList("skillIds"),
            installPath = installPath,
            activeBundleSha256 = activeBundleSha256,
            size = json.long("size") ?: 0L,
            publisherKeyId = publisherKeyId,
            apiLevel = json.int("apiLevel") ?: PLUGIN_API_LEVEL,
            uiApiLevel = json.int("uiApiLevel") ?: HOST_UI_API_LEVEL,
            lastError = json.string("lastError"),
            pendingVersion = json.string("pendingVersion"),
            pendingBundleSha256 = json.string("pendingBundleSha256"),
            previousVersion = json.string("previousVersion"),
            previousInstallPath = json.string("previousInstallPath"),
            previousBundleSha256 = json.string("previousBundleSha256"),
        )
    }

    private fun resetLocked() {
        JsonFiles.backup(file, RESET_BACKUP_SUFFIX)
        writeLocked(emptyMap())
    }

    private fun writeLocked(records: Map<String, ExternalPluginRecord>) {
        JsonFiles.write(
            file,
            buildJsonObject {
                put("schemaVersion", SCHEMA_VERSION)
                putJsonArray("plugins") {
                    records.values.sortedBy { it.id }.forEach { record ->
                        add(
                            buildJsonObject {
                                put("id", record.id)
                                put("version", record.version)
                                put("installedAt", record.installedAt)
                                put("enabled", record.enabled)
                                putJsonArray("skillIds") { record.skillIds.forEach { add(it) } }
                                put("installPath", record.installPath)
                                put("activeBundleSha256", record.activeBundleSha256)
                                put("size", record.size)
                                put("publisherKeyId", record.publisherKeyId)
                                put("apiLevel", record.apiLevel)
                                put("uiApiLevel", record.uiApiLevel)
                                record.lastError?.let { put("lastError", it) }
                                record.pendingVersion?.let { put("pendingVersion", it) }
                                record.pendingBundleSha256?.let { put("pendingBundleSha256", it) }
                                record.previousVersion?.let { put("previousVersion", it) }
                                record.previousInstallPath?.let { put("previousInstallPath", it) }
                                record.previousBundleSha256?.let { put("previousBundleSha256", it) }
                            },
                        )
                    }
                }
            },
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String, fallback: T): T {
        val value = string(name) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }

    private companion object {
        const val SCHEMA_VERSION = 4

        const val RESET_BACKUP_SUFFIX = ".v2.bak"
    }
}
