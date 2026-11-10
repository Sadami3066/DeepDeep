package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object PluginManifestCodec {
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

    fun parse(json: JsonObject): PluginManifest = PluginManifest(
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
        provides = json.objectOrNull("provides")?.let(::parseProvides) ?: Provides(),
        capabilities = json.takeIf { it.containsKey("capabilities") }?.stringList("capabilities")?.toSet(),
        defaultEnabled = json.boolean("defaultEnabled") ?: true,
    )

    fun validate(
        manifest: PluginManifest,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): List<PluginValidation.Issue> = PluginValidation.validate(manifest, hostApiLevel, hostVersion)

    fun decode(
        json: JsonObject,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): Result {
        val manifest = parse(json)
        val issues = validate(manifest, hostApiLevel, hostVersion)
        val errors = issues.filter { it.severity == PluginValidation.Severity.ERROR }
        val warnings = issues.filter { it.severity == PluginValidation.Severity.WARNING }
        return if (errors.isEmpty()) Result.Valid(manifest, warnings) else Result.Invalid(manifest, issues)
    }

    private fun parseUiManifest(json: JsonObject): UiManifest = UiManifest(
        apiLevel = json.int("apiLevel") ?: HOST_UI_API_LEVEL,
        kotlin = json.string("kotlin"),
        compose = json.string("compose"),
    )

    private fun parsePublisher(json: JsonObject): PublisherInfo = PublisherInfo(
        name = json.string("name").orEmpty(),
        keyId = json.string("keyId").orEmpty(),
    )

    private fun parseProvides(json: JsonObject): Provides = Provides(
        tools = json.stringList("tools"),
        skills = json.stringList("skills"),
        uiSlots = json.stringList("uiSlots"),
        pages = json.stringList("pages"),
    )

    private fun parseIcon(raw: String?): IconRef? = raw
        ?.takeIf { it.startsWith(ASSET_PREFIX) }
        ?.removePrefix(ASSET_PREFIX)
        ?.takeIf { it.isNotBlank() }
        ?.let(IconRef::Asset)

    private fun JsonObject.text(key: String): LocalizedText {
        val value = this[key] ?: return LocalizedText.Literal(emptyMap())
        return value.toText() ?: LocalizedText.Literal(emptyMap())
    }

    private fun JsonObject.optionalText(key: String): LocalizedText? = this[key]?.toText()

    private fun JsonElement.toText(): LocalizedText? = when (this) {
        is JsonObject -> LocalizedText.Literal(
            entries.mapNotNull { (language, element) ->
                element.stringOrNull()?.let { language to it }
            }.toMap(),
        )
        is JsonPrimitive -> stringOrNull()?.let { LocalizedText.Literal(it) }
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
