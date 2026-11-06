package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginValidation
import io.github.whywood.deepdeep.plugin.api.PluginVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class PluginIndexEntry(
    val id: String,
    val version: String,
    val apiLevel: Int,
    val minHostVersion: String?,
    val url: String,
    val sha256: String,
    val size: Long,
    val publisherKeyId: String,
)

internal data class PluginIndex(
    val schemaVersion: Int,
    val plugins: List<PluginIndexEntry>,
)

internal sealed interface PluginIndexParseResult {
    data class Valid(val index: PluginIndex) : PluginIndexParseResult

    data class Invalid(val reason: String) : PluginIndexParseResult
}

internal object PluginIndexCodec {
    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

    private val PUBLISHER_KEY_ID_PATTERN = Regex("^ed25519:[0-9a-f]{64}$")

    fun parse(text: String): PluginIndexParseResult {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return PluginIndexParseResult.Invalid("index is not a JSON object")
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return PluginIndexParseResult.Invalid("index schemaVersion is missing")
        if (schemaVersion != PluginUpdateDefaults.INDEX_SCHEMA_VERSION) {
            return PluginIndexParseResult.Invalid("unsupported index schemaVersion: $schemaVersion")
        }
        val plugins = root["plugins"] as? JsonArray
            ?: return PluginIndexParseResult.Invalid("index plugins is missing")
        return parsePlugins(plugins)
    }

    fun parsePlugins(plugins: JsonArray): PluginIndexParseResult {
        if (plugins.size > PluginUpdateDefaults.MAX_INDEX_ENTRIES) {
            return PluginIndexParseResult.Invalid(
                "index has ${plugins.size} entries, limit is ${PluginUpdateDefaults.MAX_INDEX_ENTRIES}",
            )
        }
        val entries = ArrayList<PluginIndexEntry>(plugins.size)
        val seenIds = HashSet<String>(plugins.size)
        plugins.forEachIndexed { position, element ->
            val json = element as? JsonObject
                ?: return PluginIndexParseResult.Invalid("plugin entry #$position is not an object")
            val entry = try {
                parseEntry(json)
            } catch (error: EntryException) {
                return PluginIndexParseResult.Invalid("plugin entry #$position: ${error.message}")
            }
            if (!seenIds.add(entry.id)) {
                return PluginIndexParseResult.Invalid("duplicate plugin id: ${entry.id}")
            }
            entries += entry
        }
        return PluginIndexParseResult.Valid(
            PluginIndex(
                schemaVersion = PluginUpdateDefaults.INDEX_SCHEMA_VERSION,
                plugins = entries.sortedBy { it.id },
            ),
        )
    }

    fun entryToJson(entry: PluginIndexEntry): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("version", entry.version)
        put("apiLevel", entry.apiLevel)
        entry.minHostVersion?.let { put("minHostVersion", it) }
        put("url", entry.url)
        put("sha256", entry.sha256)
        put("size", entry.size)
        put("publisherKeyId", entry.publisherKeyId)
    }

    private fun parseEntry(json: JsonObject): PluginIndexEntry {
        val id = json.requiredString("id")
        if (!PluginValidation.isValidId(id)) throw EntryException("invalid plugin id: $id")
        val version = json.requiredString("version")
        if (PluginVersion.parse(version) == null) throw EntryException("invalid version: $version")
        val apiLevel = (json["apiLevel"] as? JsonPrimitive)?.intOrNull ?: PLUGIN_API_LEVEL
        if (apiLevel < 1) throw EntryException("invalid apiLevel: $apiLevel")
        val minHostVersion = json.optionalString("minHostVersion")?.also {
            if (PluginVersion.parse(it) == null) throw EntryException("invalid minHostVersion: $it")
        }
        val url = json.requiredString("url")
        if (!PluginUpdateDefaults.supportsUrl(url)) throw EntryException("unsupported url: $url")
        val sha256 = json.requiredString("sha256").lowercase()
        if (!SHA256_PATTERN.matches(sha256)) throw EntryException("invalid sha256: $sha256")
        val size = (json["size"] as? JsonPrimitive)?.longOrNull
            ?: throw EntryException("size is missing")
        if (size <= 0) throw EntryException("invalid size: $size")
        val publisherKeyId = json.requiredString("publisherKeyId").lowercase()
        if (!PUBLISHER_KEY_ID_PATTERN.matches(publisherKeyId)) {
            throw EntryException("invalid publisherKeyId: $publisherKeyId")
        }
        return PluginIndexEntry(
            id = id,
            version = version,
            apiLevel = apiLevel,
            minHostVersion = minHostVersion,
            url = url,
            sha256 = sha256,
            size = size,
            publisherKeyId = publisherKeyId,
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name) ?: throw EntryException("$name is missing")

    private fun JsonObject.optionalString(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private class EntryException(message: String) : Exception(message)
}
