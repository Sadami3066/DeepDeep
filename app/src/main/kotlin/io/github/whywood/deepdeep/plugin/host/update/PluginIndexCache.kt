package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.JsonFiles
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal data class CachedPluginIndex(
    val index: PluginIndex,
    val indexUrl: String?,
    val fetchedAt: Long,
)

internal object PluginIndexCache {
    fun read(file: File): CachedPluginIndex? {
        if (!file.isFile) return null
        val root = JsonFiles.read(file) ?: return null
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
        if (schemaVersion != PluginUpdateDefaults.CACHE_SCHEMA_VERSION) return null
        val plugins = root["plugins"] as? JsonArray ?: return null
        val parsed = PluginIndexCodec.parsePlugins(plugins)
        if (parsed !is PluginIndexParseResult.Valid) return null
        return CachedPluginIndex(
            index = parsed.index,
            indexUrl = (root["indexUrl"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            fetchedAt = (root["fetchedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    }

    fun write(file: File, index: PluginIndex, indexUrl: String, fetchedAt: Long) {
        val root: JsonObject = buildJsonObject {
            put("schemaVersion", PluginUpdateDefaults.CACHE_SCHEMA_VERSION)
            put("indexUrl", indexUrl)
            put("fetchedAt", fetchedAt)
            putJsonArray("plugins") {
                index.plugins.forEach { entry -> add(PluginIndexCodec.entryToJson(entry)) }
            }
        }
        JsonFiles.write(file, root)
    }
}
