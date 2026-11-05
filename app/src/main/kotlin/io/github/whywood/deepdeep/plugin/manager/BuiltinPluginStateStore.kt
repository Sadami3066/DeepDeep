package io.github.whywood.deepdeep.plugin.manager

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal data class BuiltinPluginState(
    val installed: Boolean,
    val enabled: Boolean,
    val installedVersion: String?,
)

internal class BuiltinPluginStateStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun load(): Map<String, BuiltinPluginState> = mutex.withLock {
        withContext(ioDispatcher) { loadLocked() }
    }

    suspend fun upsert(pluginId: String, state: BuiltinPluginState) = mutex.withLock {
        withContext(ioDispatcher) {
            writeLocked(loadLocked() + (pluginId to state))
        }
    }

    suspend fun remove(pluginId: String) = mutex.withLock {
        withContext(ioDispatcher) {
            writeLocked(loadLocked() - pluginId)
        }
    }

    private fun loadLocked(): Map<String, BuiltinPluginState> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        if (root == null || root["schemaVersion"]?.primitiveInt() != SCHEMA_VERSION) {
            file.parentFile?.mkdirs()
            file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
            return emptyMap()
        }
        val plugins = root["plugins"] as? JsonObject ?: return emptyMap()
        return plugins.entries.mapNotNull { (id, value) ->
            val item = value as? JsonObject ?: return@mapNotNull null
            id to BuiltinPluginState(
                installed = (item["installed"] as? JsonPrimitive)?.booleanOrNull ?: true,
                enabled = (item["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                installedVersion = (item["installedVersion"] as? JsonPrimitive)?.contentOrNull,
            )
        }.toMap()
    }

    private fun writeLocked(states: Map<String, BuiltinPluginState>) {
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put(
                "plugins",
                buildJsonObject {
                    states.forEach { (id, state) ->
                        put(
                            id,
                            buildJsonObject {
                                put("installed", state.installed)
                                put("enabled", state.enabled)
                                state.installedVersion?.let { put("installedVersion", it) }
                            },
                        )
                    }
                },
            )
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.primitiveInt(): Int? =
        (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
