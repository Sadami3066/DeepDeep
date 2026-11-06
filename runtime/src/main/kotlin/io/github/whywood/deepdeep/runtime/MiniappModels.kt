package io.github.whywood.deepdeep.runtime

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MiniappManifestException(message: String) : Exception(message)

data class MiniappManifest(
    val id: String,
    val name: String,
    val icon: String = "",
    val version: String = DEFAULT_VERSION,
    val entry: String = DEFAULT_ENTRY,
    val description: String = "",
) {
    fun toJson(): String = JSON.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("id", id)
            put("name", name)
            put("icon", icon)
            put("version", version)
            put("entry", entry)
            put("description", description)
        },
    )

    fun withName(name: String): MiniappManifest = copy(name = normalizedName(name))

    companion object {
        const val DEFAULT_ENTRY = "index.html"
        const val DEFAULT_VERSION = "1.0.0"
        const val MAX_BYTES = 64 * 1024
        val ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

        private val JSON = Json { prettyPrint = true }

        fun parse(text: String): MiniappManifest {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                throw MiniappManifestException("manifest.json exceeds $MAX_BYTES bytes")
            }
            val root = try {
                Json.parseToJsonElement(text)
            } catch (error: Exception) {
                throw MiniappManifestException("manifest.json is not valid JSON")
            } as? JsonObject ?: throw MiniappManifestException("manifest.json must be a JSON object")
            val id = requiredString(root, "id")
            if (!ID_REGEX.matches(id)) {
                throw MiniappManifestException("id must be 1-48 lowercase letters, digits, '-' or '_' and start with a letter or digit")
            }
            val name = normalizedName(requiredString(root, "name"))
            val icon = optionalString(root, "icon").orEmpty()
            if (icon.length > 8) {
                throw MiniappManifestException("icon must be at most 8 characters")
            }
            val version = optionalString(root, "version") ?: DEFAULT_VERSION
            if (version.length > 20) {
                throw MiniappManifestException("version must be at most 20 characters")
            }
            val entry = optionalString(root, "entry") ?: DEFAULT_ENTRY
            validateEntry(entry)
            val description = optionalString(root, "description").orEmpty()
            if (description.length > 300) {
                throw MiniappManifestException("description must be at most 300 characters")
            }
            return MiniappManifest(
                id = id,
                name = name,
                icon = icon,
                version = version,
                entry = entry,
                description = description,
            )
        }

        internal fun normalizedName(raw: String): String {
            val name = raw.trim()
            if (name.isEmpty() || name.length > 40) {
                throw MiniappManifestException("name must be 1-40 characters")
            }
            return name
        }

        internal fun validateEntry(entry: String) {
            if (entry.length > 120) {
                throw MiniappManifestException("entry must be at most 120 characters")
            }
            if (entry.startsWith("/") || entry.startsWith("\\") || entry.contains("..") || entry.contains('\\')) {
                throw MiniappManifestException("entry must be a relative path inside the app folder")
            }
            if (!entry.endsWith(".html")) {
                throw MiniappManifestException("entry must be an .html file")
            }
        }

        private fun requiredString(root: JsonObject, field: String): String =
            optionalString(root, field) ?: throw MiniappManifestException("$field is required")

        private fun optionalString(root: JsonObject, field: String): String? {
            val element = root[field] ?: return null
            if (element is JsonNull) return null
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw MiniappManifestException("$field must be a string")
            }
            return primitive.content.trim().takeIf { it.isNotEmpty() }
        }
    }
}

data class MiniappCreation(
    val manifest: MiniappManifest,
    val directory: File,
)

enum class MiniappInvalidReason {
    MANIFEST_MISSING,
    MANIFEST_UNREADABLE,
    MANIFEST_INVALID,
    ENTRY_MISSING,
    ID_MISMATCH,
}

data class MiniappInvalid(
    val id: String,
    val directory: File,
    val reason: MiniappInvalidReason,
    val detail: String?,
)

data class MiniappScan(
    val creations: List<MiniappCreation>,
    val invalid: List<MiniappInvalid>,
)

sealed interface MiniappEntry {
    data class Valid(val creation: MiniappCreation) : MiniappEntry

    data class Invalid(
        val id: String,
        val reason: MiniappInvalidReason,
        val detail: String?,
    ) : MiniappEntry

    data object Missing : MiniappEntry
}

data class MiniappDraft(
    val directory: File,
    val manifest: MiniappManifest?,
)

class MiniappLoadException(message: String) : Exception(message)
