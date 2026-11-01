package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MAX_PRESENTED_ITEMS = 4

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif")
private val HTML_EXTENSIONS = setOf("html", "htm")

data class PresentedCreative(
    val id: String,
    val name: String,
    val icon: String = "",
    val version: String = "",
)

fun workshopCreativeLookup(store: WorkshopStore): suspend (String) -> PresentedCreative? = { id ->
    when (val entry = store.load(id)) {
        is WorkshopEntry.Valid -> entry.creation.let { creation ->
            PresentedCreative(
                id = creation.manifest.id,
                name = creation.manifest.name,
                icon = creation.manifest.icon,
                version = creation.manifest.version,
            )
        }

        else -> null
    }
}

private val presentParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("items") {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", MAX_PRESENTED_ITEMS)
            put("description", "One to four finished works to deliver to the user")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("file"))
                            add(JsonPrimitive("image"))
                            add(JsonPrimitive("html"))
                            add(JsonPrimitive("creative"))
                        }
                        put(
                            "description",
                            "file: any generated file; image: picture shown inline; html: web page shown inline; creative: installed Workshop mini app",
                        )
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "For file, image and html: explicit absolute path in the Android filesystem",
                        )
                    }
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "For creative: Workshop app id, from workshop_load")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Optional display title")
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("type")) }
            }
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("items")) })
}

fun presentTool(
    creativeLookup: (suspend (String) -> PresentedCreative?)? = null,
    session: () -> BoundSession?,
): CodingTool = CodingTool(
    name = "present",
    description = "Deliver one to four finished works to the user: files, images, web pages, or installed Workshop mini apps. Chat shows a delivery card for every item; images and web pages render inline.",
    parameters = presentParameters,
    label = "present",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        val bound = session()
        if (bound == null) {
            textResult("Session is not bound", isError = true)
        } else {
            try {
                val items = parsePresentedItems(args, File(bound.cwd), creativeLookup)
                textResult(presentedItemsJson(items).toString())
            } catch (error: RequestAbortedException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                textResult(error.message ?: "invalid present arguments", isError = true)
            }
        }
    },
)

private suspend fun parsePresentedItems(
    args: JsonObject,
    cwd: File,
    creativeLookup: (suspend (String) -> PresentedCreative?)?,
): List<JsonObject> {
    val items = args["items"] as? JsonArray
    if (items != null) {
        if (items.size !in 1..MAX_PRESENTED_ITEMS) {
            throw IllegalArgumentException("items needs 1..$MAX_PRESENTED_ITEMS entries")
        }
        return items.mapIndexed { index, element ->
            normalizeItem(index, element, cwd, creativeLookup)
        }
    }
    val files = args["files"] as? JsonArray
        ?: throw IllegalArgumentException("items must be an array")
    if (files.size !in 1..MAX_PRESENTED_ITEMS) {
        throw IllegalArgumentException("files needs 1..$MAX_PRESENTED_ITEMS entries")
    }
    return files.mapIndexed { index, element -> normalizeLegacyFile(index, element, cwd) }
}

private suspend fun normalizeItem(
    index: Int,
    element: JsonElement,
    cwd: File,
    creativeLookup: (suspend (String) -> PresentedCreative?)?,
): JsonObject {
    val item = element as? JsonObject
        ?: throw IllegalArgumentException("items[$index] must be an object")
    val type = item.argumentString("type")?.lowercase(Locale.US)
        ?: throw IllegalArgumentException("items[$index].type is required")
    val title = item.argumentString("title")
    return when (type) {
        "file" -> fileItem(index, item, cwd, title, PresentKind.FILE)
        "image" -> fileItem(index, item, cwd, title, PresentKind.IMAGE)
        "html" -> fileItem(index, item, cwd, title, PresentKind.HTML)
        "creative" -> creativeItem(index, item, title, creativeLookup)
        else -> throw IllegalArgumentException(
            "items[$index].type must be one of file, image, html, creative",
        )
    }
}

private enum class PresentKind(val json: String) {
    FILE("file"),
    IMAGE("image"),
    HTML("html"),
}

private fun fileItem(
    index: Int,
    item: JsonObject,
    cwd: File,
    title: String?,
    kind: PresentKind,
): JsonObject {
    val rawPath = item.argumentString("path")
        ?: throw IllegalArgumentException("items[$index].path is required")
    val file = resolvePresentedFile(rawPath, cwd)
    if (kind != PresentKind.FILE) {
        val extension = file.extension.lowercase(Locale.US)
        if (kind == PresentKind.IMAGE && extension !in IMAGE_EXTENSIONS) {
            throw IllegalArgumentException("items[$index].path is not an image: $rawPath")
        }
        if (kind == PresentKind.HTML && extension !in HTML_EXTENSIONS) {
            throw IllegalArgumentException("items[$index].path is not an html page: $rawPath")
        }
        if (!file.exists()) {
            throw IllegalArgumentException("items[$index].path does not exist: $rawPath")
        }
    }
    if (!file.exists()) throw IllegalArgumentException("path does not exist: $rawPath")
    if (!file.canRead()) throw IllegalArgumentException("path is not readable: $rawPath")
    if (!file.isFile) {
        throw IllegalArgumentException("items[$index].path is a directory: $rawPath")
    }
    return buildJsonObject {
        put("type", kind.json)
        put("path", file.absolutePath)
        put("name", file.name.ifBlank { rawPath.substringAfterLast('/') })
        put("mime", mimeOf(file.name))
        title?.let { put("title", it) }
    }
}

private fun normalizeLegacyFile(
    index: Int,
    element: JsonElement,
    cwd: File,
): JsonObject {
    val item = element as? JsonObject
        ?: throw IllegalArgumentException("files[$index] must be an object")
    val rawPath = item.argumentString("path")
        ?: throw IllegalArgumentException("files[$index].path must not be empty")
    val file = resolvePresentedFile(rawPath, cwd)
    if (!file.exists()) throw IllegalArgumentException("path does not exist: $rawPath")
    if (!file.canRead()) throw IllegalArgumentException("path is not readable: $rawPath")
    if (!file.isFile) {
        throw IllegalArgumentException("files[$index].path is a directory: $rawPath")
    }
    val extension = file.extension.lowercase(Locale.US)
    val kind = when {
        extension in IMAGE_EXTENSIONS -> PresentKind.IMAGE
        extension in HTML_EXTENSIONS -> PresentKind.HTML
        else -> PresentKind.FILE
    }
    return buildJsonObject {
        put("type", kind.json)
        put("path", file.absolutePath)
        put("name", file.name.ifBlank { rawPath.substringAfterLast('/') })
        put("mime", mimeOf(file.name))
    }
}

private suspend fun creativeItem(
    index: Int,
    item: JsonObject,
    title: String?,
    creativeLookup: (suspend (String) -> PresentedCreative?)?,
): JsonObject {
    val id = item.argumentString("id")
        ?: throw IllegalArgumentException("items[$index].id is required for creative")
    if (creativeLookup == null) {
        throw IllegalArgumentException("presenting creative items is not available")
    }
    val creative = creativeLookup(id)
        ?: throw IllegalArgumentException("workshop app not found: $id")
    return buildJsonObject {
        put("type", "creative")
        put("id", creative.id)
        put("name", creative.name.ifBlank { id })
        creative.icon.trim().takeIf { it.isNotEmpty() }?.let { put("icon", it) }
        creative.version.trim().takeIf { it.isNotEmpty() }?.let { put("version", it) }
        title?.let { put("title", it) }
    }
}

private fun resolvePresentedFile(rawPath: String, cwd: File): File {
    return File(requireAbsolutePath(rawPath))
}

private fun presentedItemsJson(items: List<JsonObject>): JsonObject = buildJsonObject {
    putJsonArray("items") {
        items.forEach { item -> add(item) }
    }
}

private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "avif" -> "image/avif"
    "txt", "log" -> "text/plain"
    "md", "markdown" -> "text/markdown"
    "json" -> "application/json"
    "xml" -> "application/xml"
    "yaml", "yml" -> "application/x-yaml"
    "toml" -> "application/toml"
    "csv" -> "text/csv"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "pdf" -> "application/pdf"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
}

private fun JsonObject.argumentString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
