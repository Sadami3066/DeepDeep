package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.BASELINE_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.Provides
import io.github.whywood.deepdeep.plugin.api.assetIcon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object PluginManifestCodec {
    sealed interface Result {
        val manifest: PluginManifest

        data class Valid(
            override val manifest: PluginManifest,
            val warnings: List<PluginValidation.Issue> = emptyList(),
        ) : Result

        data class Invalid(
            override val manifest: PluginManifest,
            val issues: List<PluginValidation.Issue>,
        ) : Result
    }

    fun parse(json: JsonObject): PluginManifest = PluginManifestFields.from(json).manifest

    fun validate(
        manifest: PluginManifest,
        declaredCapabilities: List<String>?,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): List<PluginValidation.Issue> =
        PluginValidation.validate(manifest, declaredCapabilities, hostApiLevel, hostVersion)

    fun decode(
        json: JsonObject,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): Result {
        val fields = PluginManifestFields.from(json)
        val issues = validate(fields.manifest, fields.declaredCapabilities, hostApiLevel, hostVersion)
        val errors = issues.filter { it.severity == PluginValidation.Severity.ERROR }
        val warnings = issues.filter { it.severity == PluginValidation.Severity.WARNING }
        return if (errors.isEmpty()) Result.Valid(fields.manifest, warnings) else Result.Invalid(fields.manifest, issues)
    }

    private class PluginManifestFields(
        val manifest: PluginManifest,
        val declaredCapabilities: List<String>?,
    ) {
        companion object {
            fun from(json: JsonObject): PluginManifestFields {
                val declaredCapabilities = json.takeIf { it.containsKey("capabilities") }?.let { it.stringList("capabilities") }
                val capabilities = declaredCapabilities.orEmpty().mapNotNull(PluginCapability::fromWire).toSet()
                val manifest = HostPluginManifest(
                    id = json.string("id").orEmpty(),
                    version = json.string("version").orEmpty(),
                    apiLevel = json.int("apiLevel") ?: BASELINE_API_LEVEL,
                    minHostVersion = json.string("minHostVersion"),
                    ui = json.objectOrNull("ui")?.let(::parseUiManifest),
                    name = json.text("name"),
                    summary = json.text("summary"),
                    description = json.optionalText("description"),
                    homepage = json.string("homepage"),
                    changelog = json.string("changelog"),
                    icon = parseIcon(json.string("icon")),
                    entryClass = json.string("entryClass"),
                    publisher = json.objectOrNull("publisher")?.let(::parsePublisher),
                    provides = json.objectOrNull("provides")?.let(::parseProvides) ?: HostProvides(),
                    capabilities = capabilities,
                    defaultEnabled = json.boolean("defaultEnabled") ?: true,
                )
                return PluginManifestFields(manifest, declaredCapabilities)
            }

            private fun parseUiManifest(json: JsonObject): HostUiManifest = HostUiManifest(
                apiLevel = json.int("apiLevel") ?: HOST_UI_API_LEVEL,
                kotlin = json.string("kotlin"),
                compose = json.string("compose"),
            )

            private fun parsePublisher(json: JsonObject): HostPublisherInfo = HostPublisherInfo(
                name = json.string("name").orEmpty(),
                keyId = json.string("keyId").orEmpty(),
            )

            private fun parseProvides(json: JsonObject): HostProvides = HostProvides(
                tools = json.stringList("tools"),
                skills = json.stringList("skills"),
                uiSlots = json.stringList("uiSlots"),
                pages = json.stringList("pages"),
            )

            private fun parseIcon(raw: String?): IconRef? = raw
                ?.takeIf { it.startsWith(ASSET_PREFIX) }
                ?.removePrefix(ASSET_PREFIX)
                ?.takeIf { it.isNotBlank() }
                ?.let(::assetIcon)

            private fun JsonObject.text(key: String): LocalizedText {
                val value = this[key] ?: return HostLocalizedText(emptyMap())
                return value.toText() ?: HostLocalizedText(emptyMap())
            }

            private fun JsonObject.optionalText(key: String): LocalizedText? = this[key]?.toText()

            private fun JsonElement.toText(): LocalizedText? = when (this) {
                is JsonObject -> HostLocalizedText(
                    entries.mapNotNull { (language, element) ->
                        element.stringOrNull()?.let { language to it }
                    }.toMap(),
                )

                is JsonPrimitive -> stringOrNull()?.let(::hostLocalizedText)
                else -> null
            }

            private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()

            private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

            private fun JsonObject.int(key: String): Int? {
                val element = this[key] as? JsonPrimitive ?: return null
                return element.intOrNull ?: element.contentOrNull?.toIntOrNull()
            }

            private fun JsonObject.boolean(key: String): Boolean? {
                val element = this[key] as? JsonPrimitive ?: return null
                return element.contentOrNull?.toBooleanStrictOrNull()
            }

            private fun JsonObject.stringList(key: String): List<String> {
                val array = this[key] as? JsonArray ?: return emptyList()
                return array.mapNotNull { it.stringOrNull() }
            }

            private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

            private const val ASSET_PREFIX = "asset:"
        }
    }
}
