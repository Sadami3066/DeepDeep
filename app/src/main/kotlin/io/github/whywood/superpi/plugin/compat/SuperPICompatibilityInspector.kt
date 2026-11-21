package io.github.whywood.superpi.plugin.compat

import io.github.whywood.superpi.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.superpi.plugin.api.PluginCapability
import io.github.whywood.superpi.plugin.api.PluginPermission
import io.github.whywood.superpi.plugin.host.HostToolNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

object SuperPICompatibilityInspector {
    private val PLUGIN_ID_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val TOOL_NAME_REGEX = Regex("^[a-z][a-z0-9_]*$")
    private val SEMVER_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$""")

    private val STANDARD_JSON_SCHEMA_TYPES = setOf(
        "string", "number", "integer", "boolean", "object", "array", "null",
    )

    fun inspectManifest(
        manifestJson: JsonObject,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
    ): CompatibilityReport {
        val issues = mutableListOf<CompatibilityIssue>()

        val id = manifestJson.string("id").orEmpty()
        if (!PLUGIN_ID_REGEX.matches(id)) {
            val sanitized = sanitizeToDomain(id)
            issues += CompatibilityIssue(
                code = CompatibilityCode.INVALID_ID,
                field = "id",
                message = "Plugin ID '$id' must be lowercase reverse domain with at least two segments (^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$).",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "NORMALIZE_ID_PREFIX",
                        title = "Normalize with domain prefix",
                        description = "Change to '$sanitized'",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val version = manifestJson.string("version").orEmpty()
        if (!SEMVER_REGEX.matches(version)) {
            val semver = padSemver(version)
            issues += CompatibilityIssue(
                code = CompatibilityCode.INVALID_VERSION,
                field = "version",
                message = "Version '$version' does not follow SemVer format major.minor.patch.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "PAD_SEMVER",
                        title = "Format as SemVer",
                        description = "Pad version to '$semver'",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val apiLevel = manifestJson.int("apiLevel")
        if (apiLevel == null || apiLevel != hostApiLevel) {
            issues += CompatibilityIssue(
                code = CompatibilityCode.API_LEVEL_OUTDATED,
                field = "apiLevel",
                message = "Plugin apiLevel '${apiLevel ?: "missing"}' does not match host API level $hostApiLevel.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "UPGRADE_API_LEVEL",
                        title = "Upgrade to API Level $hostApiLevel",
                        description = "Set apiLevel to $hostApiLevel for full host contract compliance.",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val entryClass = manifestJson.string("entryClass")
        if (entryClass.isNullOrBlank()) {
            issues += CompatibilityIssue(
                code = CompatibilityCode.ENTRY_CLASS_MISSING,
                field = "entryClass",
                message = "Missing entryClass implementing SuperPIPlugin.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "USE_DEFAULT_BRIDGE_ENTRY",
                        title = "Assign default adapter entry class",
                        description = "Use default synthetic bridge entry class.",
                        isRecommended = true,
                    ),
                ),
            )
        }

        checkLocalizedText(manifestJson, "name", issues)
        checkLocalizedText(manifestJson, "summary", issues)

        val declaredCaps = manifestJson["capabilities"]?.let { elem ->
            (elem as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }
        val validCaps = declaredCaps?.mapNotNull(PluginCapability::fromWire)?.toSet() ?: emptySet()
        if (declaredCaps == null) {
            issues += CompatibilityIssue(
                code = CompatibilityCode.MISSING_CAPABILITIES,
                field = "capabilities",
                message = "Manifest must explicitly declare capabilities.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "ADD_BASE_CAPABILITIES",
                        title = "Add base capabilities",
                        description = "Add required capabilities inferred from permissions and components.",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val permissions = manifestJson.stringList("permissions")
        val missingCapsForPerms = mutableSetOf<PluginCapability>()
        for (wire in permissions) {
            val req = permissionCapability(wire)
            if (req != null && req !in validCaps) {
                missingCapsForPerms += req
            }
        }
        if (missingCapsForPerms.isNotEmpty()) {
            val wires = missingCapsForPerms.map { it.wire }
            issues += CompatibilityIssue(
                code = CompatibilityCode.PERMISSION_WITHOUT_CAPABILITY,
                field = "capabilities",
                message = "Declared permissions require missing capabilities: $wires",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "ADD_REQUIRED_CAPABILITIES",
                        title = "Add required capabilities to capabilities array",
                        description = "Append missing capabilities: $wires",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val components = manifestJson.objectOrNull("components")
        if (components != null) {
            val missingCapsForComponents = mutableSetOf<PluginCapability>()
            if (components.boolean("background") == true && PluginCapability.BACKGROUND !in validCaps) {
                missingCapsForComponents += PluginCapability.BACKGROUND
            }
            if (components.boolean("foregroundService") == true && PluginCapability.BACKGROUND !in validCaps) {
                missingCapsForComponents += PluginCapability.BACKGROUND
            }
            if (components.boolean("notificationListener") == true && PluginCapability.NOTIFICATIONS !in validCaps) {
                missingCapsForComponents += PluginCapability.NOTIFICATIONS
            }
            if (components.boolean("accessibility") == true && PluginCapability.ACCESSIBILITY !in validCaps) {
                missingCapsForComponents += PluginCapability.ACCESSIBILITY
            }
            if (components.boolean("share") == true && PluginCapability.SHARE !in validCaps) {
                missingCapsForComponents += PluginCapability.SHARE
            }
            if (missingCapsForComponents.isNotEmpty()) {
                val wires = missingCapsForComponents.map { it.wire }
                issues += CompatibilityIssue(
                    code = CompatibilityCode.COMPONENT_WITHOUT_CAPABILITY,
                    field = "capabilities",
                    message = "Enabled components require missing capabilities: $wires",
                    severity = CompatibilitySeverity.ERROR,
                    options = listOf(
                        RemediationOption(
                            id = "ADD_COMPONENT_CAPABILITIES",
                            title = "Add required capabilities for components",
                            description = "Append: $wires",
                            isRecommended = true,
                        ),
                    ),
                )
            }
        }

        val provides = manifestJson.objectOrNull("provides")
        val tools = provides?.stringList("tools").orEmpty()
        for (tool in tools) {
            if (!TOOL_NAME_REGEX.matches(tool)) {
                val fixed = sanitizeToolName(tool)
                issues += CompatibilityIssue(
                    code = CompatibilityCode.INVALID_TOOL_NAME,
                    field = "provides.tools",
                    message = "Tool name '$tool' must be lowercase snake_case (^[a-z][a-z0-9_]*$).",
                    severity = CompatibilitySeverity.ERROR,
                    options = listOf(
                        RemediationOption(
                            id = "RENAME_TOOL_$tool",
                            title = "Rename tool to '$fixed'",
                            description = "Convert to compliant snake_case.",
                            isRecommended = true,
                        ),
                    ),
                )
            } else if (HostToolNames.isReserved(tool)) {
                val prefixed = "ext_$tool"
                issues += CompatibilityIssue(
                    code = CompatibilityCode.RESERVED_TOOL_NAME,
                    field = "provides.tools",
                    message = "Tool name '$tool' conflicts with SuperPI host reserved tool name.",
                    severity = CompatibilitySeverity.ERROR,
                    options = listOf(
                        RemediationOption(
                            id = "PREFIX_RESERVED_$tool",
                            title = "Prefix reserved tool name to '$prefixed'",
                            description = "Prepend 'ext_' to avoid collisions with SuperPI engine tools.",
                            isRecommended = true,
                        ),
                    ),
                )
            }
        }

        return CompatibilityReport(
            isCompatible = issues.none { it.severity == CompatibilitySeverity.ERROR },
            issues = issues,
        )
    }

    fun inspectTool(
        toolJson: JsonObject,
        allToolsInPack: List<JsonObject> = emptyList(),
    ): CompatibilityReport {
        val issues = mutableListOf<CompatibilityIssue>()

        val rawName = toolJson.string("name").orEmpty()
        if (!TOOL_NAME_REGEX.matches(rawName)) {
            val snake = sanitizeToolName(rawName)
            issues += CompatibilityIssue(
                code = CompatibilityCode.INVALID_TOOL_NAME,
                field = "name",
                message = "Tool name '$rawName' must be lowercase snake_case (^[a-z][a-z0-9_]*$).",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "CONVERT_SNAKE_CASE",
                        title = "Convert to snake_case ('$snake')",
                        description = "Replace dashes, spaces, and camelCase with lower snake_case.",
                        isRecommended = true,
                    ),
                ),
            )
        } else if (HostToolNames.isReserved(rawName)) {
            val prefixed = "ext_$rawName"
            issues += CompatibilityIssue(
                code = CompatibilityCode.RESERVED_TOOL_NAME,
                field = "name",
                message = "Tool name '$rawName' conflicts with SuperPI host reserved tool name.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "PREFIX_RESERVED_NAME",
                        title = "Prefix with namespace ('$prefixed')",
                        description = "Prepend 'ext_' to avoid engine collisions.",
                        isRecommended = true,
                    ),
                ),
            )
        }

        val paramsElem = toolJson["parameters"] ?: toolJson["input_schema"]
        val paramsObj = paramsElem as? JsonObject
        if (paramsObj == null) {
            issues += CompatibilityIssue(
                code = CompatibilityCode.INVALID_SCHEMA,
                field = "parameters",
                message = "Tool parameters must be a valid JSON Schema object.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "CREATE_EMPTY_SCHEMA",
                        title = "Wrap as standard empty JSON Schema",
                        description = "Create {\"type\": \"object\", \"properties\": {}}",
                        isRecommended = true,
                    ),
                ),
            )
        } else {
            val typeStr = paramsObj.string("type")
            if (typeStr != "object") {
                issues += CompatibilityIssue(
                    code = CompatibilityCode.INVALID_SCHEMA,
                    field = "parameters.type",
                    message = "Root parameters schema type must be 'object'.",
                    severity = CompatibilitySeverity.ERROR,
                    options = listOf(
                        RemediationOption(
                            id = "SET_TYPE_OBJECT",
                            title = "Set type to 'object'",
                            description = "Add or change type to 'object'.",
                            isRecommended = true,
                        ),
                    ),
                )
            }
            val propsObj = paramsObj.objectOrNull("properties")
            if (propsObj == null) {
                issues += CompatibilityIssue(
                    code = CompatibilityCode.SCHEMA_MISSING_PROPERTIES,
                    field = "parameters.properties",
                    message = "Parameters schema is missing 'properties' object.",
                    severity = CompatibilitySeverity.ERROR,
                    options = listOf(
                        RemediationOption(
                            id = "ADD_PROPERTIES_BLOCK",
                            title = "Add empty properties object",
                            description = "Set properties to {}",
                            isRecommended = true,
                        ),
                    ),
                )
            } else {
                val nonStandardProps = mutableMapOf<String, String>()
                for ((propName, propVal) in propsObj) {
                    val propObj = propVal as? JsonObject ?: continue
                    val pType = propObj.string("type")
                    if (pType != null && pType !in STANDARD_JSON_SCHEMA_TYPES) {
                        nonStandardProps[propName] = pType
                    }
                }
                if (nonStandardProps.isNotEmpty()) {
                    issues += CompatibilityIssue(
                        code = CompatibilityCode.SCHEMA_NON_STANDARD_TYPES,
                        field = "parameters.properties",
                        message = "Properties use non-standard types: $nonStandardProps",
                        severity = CompatibilitySeverity.WARNING,
                        options = listOf(
                            RemediationOption(
                                id = "CONVERT_STANDARD_TYPES",
                                title = "Normalize to standard JSON Schema types",
                                description = "Convert python/custom types (str->string, int->integer, etc.).",
                                isRecommended = true,
                            ),
                        ),
                    )
                }
            }
        }

        if (allToolsInPack.size > 5) {
            val currentIndex = allToolsInPack.indexOfFirst { it.string("name") == rawName }
            if (currentIndex >= 3) {
                issues += CompatibilityIssue(
                    code = CompatibilityCode.EXPOSURE_OVERFLOW,
                    field = "exposure",
                    message = "Tool pack has ${allToolsInPack.size} tools. Loading all as DIRECT exhausts prompt budget.",
                    severity = CompatibilitySeverity.INFO,
                    options = listOf(
                        RemediationOption(
                            id = "SET_DEFERRED_EXPOSURE",
                            title = "Set exposure to DEFERRED",
                            description = "Load on-demand via SuperPI tool_search to preserve context window.",
                            isRecommended = true,
                        ),
                    ),
                )
            }
        }

        return CompatibilityReport(
            isCompatible = issues.none { it.severity == CompatibilitySeverity.ERROR },
            issues = issues,
        )
    }

    private fun checkLocalizedText(
        json: JsonObject,
        key: String,
        issues: MutableList<CompatibilityIssue>,
    ) {
        val elem = json[key]
        if (elem == null) {
            issues += CompatibilityIssue(
                code = CompatibilityCode.MISSING_DEFAULT_LOCALE,
                field = key,
                message = "Missing localized text field '$key'.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "SYNTHESIZE_DEFAULT_$key",
                        title = "Synthesize default text for '$key'",
                        description = "Provide default empty or placeholder text.",
                        isRecommended = true,
                    ),
                ),
            )
            return
        }
        val hasDefault = (elem as? JsonObject)?.containsKey("default") == true
        if (!hasDefault) {
            val fallback = if (elem is JsonPrimitive) elem.contentOrNull.orEmpty()
            else (elem as? JsonObject)?.values?.firstOrNull()?.stringOrNull().orEmpty()
            issues += CompatibilityIssue(
                code = CompatibilityCode.MISSING_DEFAULT_LOCALE,
                field = key,
                message = "Localized text '$key' must contain a 'default' language key.",
                severity = CompatibilitySeverity.ERROR,
                options = listOf(
                    RemediationOption(
                        id = "SYNTHESIZE_DEFAULT_$key",
                        title = "Promote translation to 'default'",
                        description = "Set 'default' to '$fallback'.",
                        isRecommended = true,
                    ),
                ),
            )
        }
    }

    private fun sanitizeToDomain(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        return if (cleaned.contains(".")) cleaned else "io.github.adapted.${cleaned.ifBlank { "plugin" }}"
    }

    fun sanitizeToolName(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        s = s.lowercase()
        s = s.replace(Regex("[^a-z0-9_]"), "_")
        s = s.replace(Regex("_+"), "_")
        s = s.trim('_')
        if (s.isEmpty()) s = "custom_tool"
        if (s.first().isDigit()) s = "tool_$s"
        return s
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

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? {
        val element = this[key] as? JsonPrimitive ?: return null
        return element.intOrNull ?: element.contentOrNull?.toIntOrNull()
    }
    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
}
