package io.github.whywood.superpi.plugin.compat

import io.github.whywood.superpi.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.superpi.plugin.api.PluginCapability
import io.github.whywood.superpi.plugin.api.PluginPermission
import io.github.whywood.superpi.plugin.host.HostToolNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object SuperPICompatibilityFixer {
    private const val DEFAULT_ENTRY_CLASS = "io.github.whywood.superpi.plugin.compat.AdaptedPluginEntry"

    private val TYPE_MAPPINGS = mapOf(
        "str" to "string",
        "int" to "integer",
        "float" to "number",
        "double" to "number",
        "bool" to "boolean",
        "dict" to "object",
        "list" to "array",
    )

    fun applyAllRecommendedManifest(
        manifestJson: JsonObject,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
    ): JsonObject {
        var current = manifestJson
        val report = SuperPICompatibilityInspector.inspectManifest(current, hostApiLevel)
        for (issue in report.issues) {
            val rec = issue.options.firstOrNull { it.isRecommended } ?: continue
            current = applyManifestOption(current, issue.code, rec.id, hostApiLevel)
        }
        return current
    }

    fun applyManifestOption(
        manifestJson: JsonObject,
        issueCode: CompatibilityCode,
        optionId: String,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
    ): JsonObject {
        val mutable = manifestJson.toMutableMap()
        when (issueCode) {
            CompatibilityCode.INVALID_ID -> {
                val currentId = (mutable["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val sanitized = sanitizeToDomain(currentId)
                mutable["id"] = JsonPrimitive(sanitized)
            }
            CompatibilityCode.INVALID_VERSION -> {
                val currentVer = (mutable["version"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                mutable["version"] = JsonPrimitive(padSemver(currentVer))
            }
            CompatibilityCode.API_LEVEL_OUTDATED -> {
                mutable["apiLevel"] = JsonPrimitive(hostApiLevel)
            }
            CompatibilityCode.ENTRY_CLASS_MISSING -> {
                mutable["entryClass"] = JsonPrimitive(DEFAULT_ENTRY_CLASS)
            }
            CompatibilityCode.MISSING_DEFAULT_LOCALE -> {
                fixLocalizedKey(mutable, "name")
                fixLocalizedKey(mutable, "summary")
            }
            CompatibilityCode.MISSING_CAPABILITIES,
            CompatibilityCode.PERMISSION_WITHOUT_CAPABILITY,
            CompatibilityCode.COMPONENT_WITHOUT_CAPABILITY -> {
                val existingCaps = (mutable["capabilities"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.toMutableSet() ?: mutableSetOf()
                val perms = (mutable["permissions"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                for (p in perms) {
                    permissionCapability(p)?.let { existingCaps.add(it.wire) }
                }
                val comps = mutable["components"] as? JsonObject
                if (comps != null) {
                    if (comps.boolean("background") == true) existingCaps.add(PluginCapability.BACKGROUND.wire)
                    if (comps.boolean("foregroundService") == true) existingCaps.add(PluginCapability.BACKGROUND.wire)
                    if (comps.boolean("notificationListener") == true) existingCaps.add(PluginCapability.NOTIFICATIONS.wire)
                    if (comps.boolean("accessibility") == true) existingCaps.add(PluginCapability.ACCESSIBILITY.wire)
                    if (comps.boolean("share") == true) existingCaps.add(PluginCapability.SHARE.wire)
                }
                if (existingCaps.isEmpty()) {
                    existingCaps.add(PluginCapability.APP.wire)
                }
                mutable["capabilities"] = JsonArray(existingCaps.map(::JsonPrimitive))
            }
            CompatibilityCode.INVALID_TOOL_NAME,
            CompatibilityCode.RESERVED_TOOL_NAME -> {
                val provides = mutable["provides"] as? JsonObject
                if (provides != null) {
                    val tools = (provides["tools"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                    val fixedTools = tools.map { tool ->
                        var name = if (HostToolNames.isReserved(tool)) "ext_$tool" else tool
                        if (!Regex("^[a-z][a-z0-9_]*$").matches(name)) {
                            name = SuperPICompatibilityInspector.sanitizeToolName(name)
                        }
                        name
                    }
                    val newProvides = provides.toMutableMap()
                    newProvides["tools"] = JsonArray(fixedTools.map(::JsonPrimitive))
                    mutable["provides"] = JsonObject(newProvides)
                }
            }
            else -> Unit
        }
        return JsonObject(mutable)
    }

    fun applyAllRecommendedTool(toolJson: JsonObject): JsonObject {
        var current = toolJson
        val report = SuperPICompatibilityInspector.inspectTool(current)
        for (issue in report.issues) {
            val rec = issue.options.firstOrNull { it.isRecommended } ?: continue
            current = applyToolOption(current, issue.code, rec.id)
        }
        return current
    }

    fun applyToolOption(
        toolJson: JsonObject,
        issueCode: CompatibilityCode,
        optionId: String,
    ): JsonObject {
        val mutable = toolJson.toMutableMap()
        when (issueCode) {
            CompatibilityCode.INVALID_TOOL_NAME -> {
                val rawName = (mutable["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                mutable["name"] = JsonPrimitive(SuperPICompatibilityInspector.sanitizeToolName(rawName))
            }
            CompatibilityCode.RESERVED_TOOL_NAME -> {
                val rawName = (mutable["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                mutable["name"] = JsonPrimitive("ext_$rawName")
            }
            CompatibilityCode.INVALID_SCHEMA,
            CompatibilityCode.SCHEMA_MISSING_PROPERTIES -> {
                val existing = (mutable["parameters"] ?: mutable["input_schema"]) as? JsonObject
                val repaired = buildJsonObject {
                    put("type", "object")
                    val existingProps = existing?.get("properties") as? JsonObject
                    put("properties", existingProps ?: buildJsonObject {})
                    val req = existing?.get("required") as? JsonArray
                    if (req != null) put("required", req)
                }
                mutable.remove("input_schema")
                mutable["parameters"] = repaired
            }
            CompatibilityCode.SCHEMA_NON_STANDARD_TYPES -> {
                val params = (mutable["parameters"] ?: mutable["input_schema"]) as? JsonObject
                if (params != null) {
                    val props = params["properties"] as? JsonObject
                    if (props != null) {
                        val fixedProps = buildJsonObject {
                            for ((k, v) in props) {
                                val pObj = v as? JsonObject
                                if (pObj != null) {
                                    val t = (pObj["type"] as? JsonPrimitive)?.contentOrNull
                                    val mapped = TYPE_MAPPINGS[t] ?: t ?: "string"
                                    val newP = pObj.toMutableMap()
                                    newP["type"] = JsonPrimitive(mapped)
                                    put(k, JsonObject(newP))
                                } else {
                                    put(k, v)
                                }
                            }
                        }
                        val newParams = params.toMutableMap()
                        newParams["properties"] = fixedProps
                        mutable["parameters"] = JsonObject(newParams)
                    }
                }
            }
            CompatibilityCode.EXPOSURE_OVERFLOW -> {
                mutable["exposure"] = JsonPrimitive("DEFERRED")
            }
            else -> Unit
        }
        return JsonObject(mutable)
    }

    private fun fixLocalizedKey(mutable: MutableMap<String, JsonElement>, key: String) {
        val elem = mutable[key]
        if (elem == null) {
            mutable[key] = buildJsonObject { put("default", key) }
        } else if (elem is JsonPrimitive && elem.isString) {
            val content = elem.contentOrNull.orEmpty()
            mutable[key] = buildJsonObject { put("default", content) }
        } else if (elem is JsonObject && !elem.containsKey("default")) {
            val fallback = elem.values.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val newMap = elem.toMutableMap()
            newMap["default"] = JsonPrimitive(fallback)
            mutable[key] = JsonObject(newMap)
        }
    }

    private fun sanitizeToDomain(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        return if (cleaned.contains(".")) cleaned else "io.github.adapted.${cleaned.ifBlank { "plugin" }}"
    }

    private fun padSemver(raw: String): String {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".")
        return when (parts.size) {
            1 -> "${parts[0].ifBlank { "1" }}.0.0"
            2 -> "${parts[0]}.${parts[1]}.0"
            else -> cleaned.ifBlank { "1.0.0" }
        }
    }

    private fun permissionCapability(wire: String): PluginCapability? = when (wire) {
        PluginPermission.LOCATION_COARSE, PluginPermission.LOCATION_FINE -> PluginCapability.LOCATION
        PluginPermission.CONTACTS_READ, PluginPermission.CONTACTS_WRITE -> PluginCapability.CONTACTS
        PluginPermission.SMS_READ, PluginPermission.SMS_SEND -> PluginCapability.SMS
        PluginPermission.CALENDAR_READ, PluginPermission.CALENDAR_WRITE -> PluginCapability.CALENDAR
        PluginPermission.CAMERA, PluginPermission.MICROPHONE -> PluginCapability.UI
        PluginPermission.NOTIFICATIONS_POST, PluginPermission.NOTIFICATIONS_LISTEN -> PluginCapability.NOTIFICATIONS
        PluginPermission.ACCESSIBILITY -> PluginCapability.ACCESSIBILITY
        PluginPermission.OVERLAY, PluginPermission.USAGE_STATS, PluginPermission.BATTERY_OPTIMIZATION -> PluginCapability.PERMISSIONS
        PluginPermission.INSTALL_PACKAGES -> PluginCapability.PACKAGES
        PluginPermission.MANAGE_STORAGE -> PluginCapability.MEDIA
        else -> null
    }

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
