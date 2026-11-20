package io.github.whywood.superpi.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal object MarketRegistryParser {
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val SUPPORTED_SCHEMA = 2..3

    fun parse(bytes: ByteArray): MarketRegistry {
        if (bytes.size > MarketDefaults.MAX_REGISTRY_BYTES) error("market registry too large")
        val root = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }
            .getOrElse { error("invalid market registry format") }
        val schemaVersion = root.int("schemaVersion") ?: error("market registry is missing schemaVersion")
        if (schemaVersion !in SUPPORTED_SCHEMA) error("unsupported market registry version")
        val generatedAt = root.string("generatedAt").orEmpty()
        val keyId = root.string("keyId").orEmpty()
        val array = root["entries"] as? JsonArray ?: error("market registry is missing entries")
        if (array.size > MarketDefaults.MAX_ENTRIES) error("too many market entries")
        val seen = HashSet<String>(array.size)
        val entries = array.mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("entry #$index has an invalid format")
            val entry = parseEntry(obj)
            if (!seen.add(entry.id)) error("duplicate market entry: ${entry.id}")
            entry
        }
        return MarketRegistry(schemaVersion, generatedAt, keyId, entries)
    }

    private fun parseEntry(obj: JsonObject): MarketEntry {
        val type = when (obj.string("type")) {
            "plugin" -> MarketEntryType.Plugin
            "skill" -> MarketEntryType.Skill
            "mcp" -> MarketEntryType.Mcp
            else -> error("unknown entry type")
        }
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: error("entry is missing id")
        val category = obj.string("category")?.trim()?.takeIf { it.isNotEmpty() }
        val name = parseLocalized(obj, "name")
        val summary = parseLocalized(obj, "summary")
        val description = parseLocalized(obj, "description")
        val icon = obj.string("icon")
        val tags = parseTags(obj["tags"])
        val version = obj.string("version").orEmpty()
        val artifact = (obj["artifact"] as? JsonObject)?.let { parseArtifact(it, type, id) }
        val plugin = (obj["plugin"] as? JsonObject)?.let {
            MarketPluginMeta(
                apiLevel = it.int("apiLevel") ?: 0,
                minHostVersion = it.string("minHostVersion"),
                publisherKeyId = it.string("publisherKeyId"),
                publisherName = it.string("publisherName"),
                requiredAccess = when (it.string("requiredAccess")) {
                    "adb" -> MarketRequiredAccess.Adb
                    "root" -> MarketRequiredAccess.Root
                    else -> MarketRequiredAccess.Normal
                },
                previewUrls = (it["previews"] as? JsonArray)
                    ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
                    ?.filter { url -> url.startsWith("https://") }
                    ?.distinct()
                    ?.take(6)
                    .orEmpty(),
            )
        }
        val skill = (obj["skill"] as? JsonObject)?.let {
            MarketSkillMeta(license = it.string("license"), source = it.string("source"))
        }
        val mcp = (obj["mcp"] as? JsonObject)?.let {
            val url = it.string("url").orEmpty()
            if (url.isBlank()) error("MCP entry is missing url")
            val transport = it.string("transport") ?: "http"
            if (transport != "http") error("unsupported MCP transport: $transport")
            MarketMcpMeta(
                url = url,
                transport = transport,
                headers = (it["headers"] as? JsonObject)?.mapValues { (_, value) ->
                    (value as? JsonPrimitive)?.contentOrNull.orEmpty()
                }.orEmpty(),
                authHint = (it["authHint"] as? JsonObject)?.let { hint -> parseLocalized(hint) }
                    ?: it.string("authHint")?.let { hint -> LocalizedText(mapOf("default" to hint)) },
                auth = (it["auth"] as? JsonObject)?.let { auth -> parseAuth(auth) },
            )
        }
        if (type == MarketEntryType.Plugin && artifact == null) error("plugin entry is missing artifact")
        if (type == MarketEntryType.Skill && artifact == null) error("skill entry is missing artifact")
        if (type == MarketEntryType.Mcp && mcp == null) error("MCP entry is missing configuration")
        return MarketEntry(
            type = type,
            id = id,
            category = category,
            name = name,
            summary = summary,
            description = description,
            icon = icon,
            tags = tags,
            version = version,
            artifact = artifact,
            plugin = plugin,
            skill = skill,
            mcp = mcp,
        )
    }

    private fun parseAuth(obj: JsonObject): MarketMcpAuth {
        val type = when (obj.string("type")) {
            "apiKey" -> MarketMcpAuthType.ApiKey
            "oauth" -> MarketMcpAuthType.OAuth
            "none", null -> MarketMcpAuthType.None
            else -> error("unknown connector authorization type")
        }
        val header = obj.string("header")
        if (type == MarketMcpAuthType.ApiKey && header.isNullOrBlank()) error("API key connector is missing header")
        return MarketMcpAuth(
            type = type,
            header = header,
            prefix = obj.string("prefix"),
            placeholder = (obj["placeholder"] as? JsonObject)?.let { parseLocalized(it) },
            helpUrl = obj.string("helpUrl"),
        )
    }

    private fun parseTags(element: kotlinx.serialization.json.JsonElement?): Map<String, List<String>> {
        when (element) {
            is JsonArray -> {
                val plain = element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                return if (plain.isEmpty()) emptyMap() else mapOf("default" to plain)
            }
            is JsonObject -> return element.mapNotNull { (language, value) ->
                val array = value as? JsonArray ?: return@mapNotNull null
                val plain = array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (plain.isEmpty()) null else language to plain
            }.toMap()
            else -> return emptyMap()
        }
    }

    private fun parseLocalized(obj: JsonObject, key: String): LocalizedText {
        val element = obj[key]
        if (element is JsonPrimitive) {
            return LocalizedText(mapOf("default" to element.contentOrNull.orEmpty()))
        }
        val objectValue = element as? JsonObject ?: return LocalizedText(emptyMap())
        return parseLocalized(objectValue)
    }

    private fun parseLocalized(obj: JsonObject): LocalizedText {
        val values = obj.mapNotNull { (language, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (text.isBlank()) null else language to text
        }.toMap()
        if (values.isEmpty()) error("missing localized text")
        return LocalizedText(values)
    }

    private fun parseArtifact(obj: JsonObject, type: MarketEntryType, id: String): MarketArtifact {
        val url = obj.string("url").orEmpty()
        if (url.isBlank()) error("entry $id is missing artifact url")
        val sha = obj.string("sha256").orEmpty()
        if (!SHA256.matches(sha)) error("entry $id has an invalid sha256")
        val size = obj.long("size") ?: 0L
        if (size <= 0) error("entry $id is missing file size")
        val limit = if (type == MarketEntryType.Skill) MarketDefaults.MAX_SKILL_BYTES else MarketDefaults.MAX_PLUGIN_BYTES
        if (size > limit) error("entry $id file is too large")
        return MarketArtifact(url, sha, size)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
