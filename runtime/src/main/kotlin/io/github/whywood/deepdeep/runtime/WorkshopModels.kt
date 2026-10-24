package io.github.whywood.deepdeep.runtime

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

enum class WorkshopType(val wire: String) {
    BOARD("board"),
    WORKSTATION("workstation"),
    WORKFLOW("workflow"),
    ;

    companion object {
        fun fromWire(value: String?): WorkshopType? {
            val normalized = value?.trim() ?: return null
            return entries.firstOrNull { it.wire == normalized }
        }
    }
}

object WorkshopPermissions {
    const val DATA_KV = "data.kv"
    const val AGENT_RUN = "agent.run"
    val KNOWN: Set<String> = setOf(DATA_KV, AGENT_RUN)
}

class WorkshopManifestException(message: String) : Exception(message)

data class WorkshopManifest(
    val id: String,
    val name: String,
    val type: WorkshopType,
    val icon: String = WorkshopSpec.defaultIcon(type),
    val version: String = DEFAULT_VERSION,
    val entry: String = DEFAULT_ENTRY,
    val description: String = "",
    val permissions: Set<String> = emptySet(),
) {
    fun toJson(): String = JSON.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("id", id)
            put("name", name)
            put("type", type.wire)
            put("icon", icon)
            put("version", version)
            put("entry", entry)
            put("description", description)
            putJsonArray("permissions") {
                permissions.sorted().forEach { permission -> add(JsonPrimitive(permission)) }
            }
        },
    )

    fun withName(name: String): WorkshopManifest = copy(name = normalizedName(name))

    companion object {
        const val DEFAULT_ENTRY = "index.html"
        const val DEFAULT_VERSION = "1.0.0"
        const val MAX_BYTES = 64 * 1024
        val ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

        private val PERMISSION_REGEX = Regex("[a-z][a-z0-9_.-]{0,40}")
        private val JSON = Json { prettyPrint = true }

        fun parse(text: String): WorkshopManifest {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                throw WorkshopManifestException("manifest.json exceeds $MAX_BYTES bytes")
            }
            val root = try {
                Json.parseToJsonElement(text)
            } catch (error: Exception) {
                throw WorkshopManifestException("manifest.json is not valid JSON")
            } as? JsonObject ?: throw WorkshopManifestException("manifest.json must be a JSON object")
            val id = requiredString(root, "id")
            if (!ID_REGEX.matches(id)) {
                throw WorkshopManifestException("id must be 1-48 lowercase letters, digits, '-' or '_' and start with a letter or digit")
            }
            val name = normalizedName(requiredString(root, "name"))
            val typeValue = requiredString(root, "type")
            val type = WorkshopType.fromWire(typeValue)
                ?: throw WorkshopManifestException("type must be board, workstation or workflow")
            val icon = optionalString(root, "icon") ?: WorkshopSpec.defaultIcon(type)
            if (icon.length > 8) {
                throw WorkshopManifestException("icon must be at most 8 characters")
            }
            val version = optionalString(root, "version") ?: DEFAULT_VERSION
            if (version.length > 20) {
                throw WorkshopManifestException("version must be at most 20 characters")
            }
            val entry = optionalString(root, "entry") ?: DEFAULT_ENTRY
            validateEntry(entry)
            val description = optionalString(root, "description").orEmpty()
            if (description.length > 300) {
                throw WorkshopManifestException("description must be at most 300 characters")
            }
            val permissions = parsePermissions(root, "permissions")
            return WorkshopManifest(
                id = id,
                name = name,
                type = type,
                icon = icon,
                version = version,
                entry = entry,
                description = description,
                permissions = permissions,
            )
        }

        internal fun normalizedName(raw: String): String {
            val name = raw.trim()
            if (name.isEmpty() || name.length > 40) {
                throw WorkshopManifestException("name must be 1-40 characters")
            }
            return name
        }

        internal fun validateEntry(entry: String) {
            if (entry.length > 120) {
                throw WorkshopManifestException("entry must be at most 120 characters")
            }
            if (entry.startsWith("/") || entry.startsWith("\\") || entry.contains("..") || entry.contains('\\')) {
                throw WorkshopManifestException("entry must be a relative path inside the app folder")
            }
            if (!entry.endsWith(".html")) {
                throw WorkshopManifestException("entry must be an .html file")
            }
        }

        internal fun parsePermissions(root: JsonObject, field: String): Set<String> {
            val element = root[field] ?: return emptySet()
            if (element is JsonNull) return emptySet()
            val array = element as? JsonArray
                ?: throw WorkshopManifestException("$field must be an array of strings")
            val permissions = LinkedHashSet<String>()
            array.forEachIndexed { index, item ->
                val primitive = item as? JsonPrimitive
                if (primitive == null || !primitive.isString) {
                    throw WorkshopManifestException("$field[$index] must be a string")
                }
                val permission = primitive.content.trim()
                if (permission.isEmpty()) return@forEachIndexed
                if (!PERMISSION_REGEX.matches(permission)) {
                    throw WorkshopManifestException("$field[$index] has an invalid permission name")
                }
                permissions.add(permission)
            }
            return permissions
        }

        private fun requiredString(root: JsonObject, field: String): String =
            optionalString(root, field) ?: throw WorkshopManifestException("$field is required")

        private fun optionalString(root: JsonObject, field: String): String? {
            val element = root[field] ?: return null
            if (element is JsonNull) return null
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw WorkshopManifestException("$field must be a string")
            }
            return primitive.content.trim().takeIf { it.isNotEmpty() }
        }
    }
}

data class WorkshopCreation(
    val manifest: WorkshopManifest,
    val directory: File,
)

enum class WorkshopInvalidReason {
    MANIFEST_MISSING,
    MANIFEST_UNREADABLE,
    MANIFEST_INVALID,
    ENTRY_MISSING,
    ID_MISMATCH,
}

data class WorkshopInvalid(
    val id: String,
    val directory: File,
    val reason: WorkshopInvalidReason,
    val detail: String?,
)

data class WorkshopScan(
    val creations: List<WorkshopCreation>,
    val invalid: List<WorkshopInvalid>,
)

sealed interface WorkshopEntry {
    data class Valid(val creation: WorkshopCreation) : WorkshopEntry

    data class Invalid(
        val id: String,
        val reason: WorkshopInvalidReason,
        val detail: String?,
    ) : WorkshopEntry

    data object Missing : WorkshopEntry
}

data class WorkshopDraft(
    val directory: File,
    val manifest: WorkshopManifest?,
)

class WorkshopInstallException(message: String) : Exception(message)
