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
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "mkv", "m4v", "3gp")

data class PresentedMiniapp(
    val id: String,
    val name: String,
    val icon: String = "",
    val version: String = "",
)

fun miniappLookup(store: MiniappStore): suspend (String) -> PresentedMiniapp? = { id ->
    when (val entry = store.load(id)) {
        is MiniappEntry.Valid -> entry.creation.let { creation ->
            PresentedMiniapp(
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
                            add(JsonPrimitive("video"))
                            add(JsonPrimitive("html"))
                            add(JsonPrimitive("miniapp"))
                        }
                        put(
                            "description",
                            "file: any generated file; image: picture shown inline; video: clip shown with a cover and player; html: web page shown inline; miniapp: installed mini app",
                        )
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "For file, image, video and html: explicit absolute path in the Android filesystem",
                        )
                    }
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "For miniapp: miniapp id, from miniapp_load")
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
    miniappLookup: (suspend (String) -> PresentedMiniapp?)? = null,
    session: () -> BoundSession?,
): CodingTool = CodingTool(
    name = "present",
    description = "Deliver one to four finished works to the user: files, images, videos, web pages, or installed miniapps. Chat shows a delivery card for every item; images and web pages render inline, videos show a cover that opens a player.",
    parameters = presentParameters,
    label = "present",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        val bound = session()
        if (bound == null) {
            textResult("Session is not bound", isError = true)
        } else {
            try {
                val items = parsePresentedItems(args, File(bound.cwd), miniappLookup)
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
    miniappLookup: (suspend (String) -> PresentedMiniapp?)?,
): List<JsonObject> {
    val items = args["items"] as? JsonArray
    if (items != null) {
        if (items.size !in 1..MAX_PRESENTED_ITEMS) {
            throw IllegalArgumentException("items needs 1..$MAX_PRESENTED_ITEMS entries")
        }
        return items.mapIndexed { index, element ->
            normalizeItem(index, element, cwd, miniappLookup)
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
    miniappLookup: (suspend (String) -> PresentedMiniapp?)?,
): JsonObject {
    val item = element as? JsonObject
        ?: throw IllegalArgumentException("items[$index] must be an object")
    val type = item.argumentString("type")?.lowercase(Locale.US)
        ?: throw IllegalArgumentException("items[$index].type is required")
    val title = item.argumentString("title")
    return when (type) {
        "file" -> fileItem(index, item, cwd, title, PresentKind.FILE)
        "image" -> fileItem(index, item, cwd, title, PresentKind.IMAGE)
        "video" -> fileItem(index, item, cwd, title, PresentKind.VIDEO)
        "html" -> fileItem(index, item, cwd, title, PresentKind.HTML)
        "miniapp" -> miniappItem(index, item, title, miniappLookup)
        else -> throw IllegalArgumentException(
            "items[$index].type must be one of file, image, video, html, miniapp",
        )
    }
}

private enum class PresentKind(val json: String) {
    FILE("file"),
    IMAGE("image"),
    VIDEO("video"),
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
        if (kind == PresentKind.VIDEO && extension !in VIDEO_EXTENSIONS) {
            throw IllegalArgumentException("items[$index].path is not a video: $rawPath")
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
        extension in VIDEO_EXTENSIONS -> PresentKind.VIDEO
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

private suspend fun miniappItem(
    index: Int,
    item: JsonObject,
    title: String?,
    miniappLookup: (suspend (String) -> PresentedMiniapp?)?,
): JsonObject {
    val id = item.argumentString("id")
        ?: throw IllegalArgumentException("items[$index].id is required for miniapp")
    if (miniappLookup == null) {
        throw IllegalArgumentException("presenting miniapp items is not available")
    }
    val miniapp = miniappLookup(id)
        ?: throw IllegalArgumentException("miniapp not found: $id")
    return buildJsonObject {
        put("type", "miniapp")
        put("id", miniapp.id)
        put("name", miniapp.name.ifBlank { id })
        miniapp.icon.trim().takeIf { it.isNotEmpty() }?.let { put("icon", it) }
        miniapp.version.trim().takeIf { it.isNotEmpty() }?.let { put("version", it) }
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
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "m4v" -> "video/x-m4v"
    "3gp" -> "video/3gpp"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
}

private fun JsonObject.argumentString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
