package io.github.whywood.deepdeep.plugin.micloud

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun galleryTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_gallery",
    description = "List Xiaomi Cloud gallery photos/videos or albums. Read-only.",
    parameters = galleryParameters,
    label = "mi_cloud_gallery",
    promptSnippet = "List Xiaomi Cloud gallery items or albums",
    promptGuidelines = listOf(
        "Use mi_cloud_gallery when the user asks about their Xiaomi Cloud photos, videos or albums.",
        "action=list returns media items, action=albums returns albums.",
        "This tool is read-only: it never uploads, edits or deletes media.",
        "Items may be truncated; ask for a smaller limit if you need a specific page.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            when (val action = args.argString("action") ?: "list") {
                "list" -> {
                    val limit = limitArg(args)
                    val response = client.getJson(galleriesPath(now()))
                    val payload = response.payloadObject()
                    val items = response.payloadArray("galleries")
                        .mapNotNull { it as? JsonObject }
                        .map(::galleryItem)
                        .take(limit)
                    val extra = buildJsonObject {
                        payload.boolOrNull("isLastPage")?.let { put("isLastPage", it) }
                        payload.stringOrNull("indexHash")?.let { put("nextIndexHash", it) }
                    }
                    itemsResult(items, extra)
                }

                "albums" -> {
                    val limit = limitArg(args)
                    val response = client.getJson(albumsPath(now()))
                    val albums = response.payloadArray("albums")
                        .mapNotNull { it as? JsonObject }
                        .map(::compactObject)
                        .take(limit)
                    itemsResult(albums)
                }

                else -> throw IllegalArgumentException("action 无效 / Invalid action: $action")
            }
        }
    },
)

internal fun recordingsTool(
    client: MiCloudReadApi,
    downloadDir: File,
    downloadLinuxDir: String,
): CodingTool = CodingTool(
    name = "mi_cloud_recordings",
    description = "List or download call recordings stored in Xiaomi Cloud. Downloads are saved into the workspace so the user can open them.",
    parameters = recordingsParameters,
    label = "mi_cloud_recordings",
    promptSnippet = "List or download Xiaomi Cloud call recordings",
    promptGuidelines = listOf(
        "Use mi_cloud_recordings when the user asks about their Xiaomi Cloud call recordings.",
        "Use action=list to browse, action=download with fileId to save one recording into the workspace.",
        "This tool is read-only for the cloud account; downloading never deletes or modifies the cloud file.",
        "Report the returned workspace path so the user can find the downloaded file.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            when (val action = args.argString("action") ?: "list") {
                "list" -> {
                    val userId = requiredUserId(client)
                    val limit = limitArg(args)
                    val offset = args.argInt("offset") ?: 0
                    if (offset < 0) {
                        throw IllegalArgumentException("offset 不能为负 / offset must not be negative")
                    }
                    val response = client.getJson(recordingsPath(userId, now(), offset, limit))
                    val items = response.payloadArray("list")
                        .mapNotNull { it as? JsonObject }
                        .map(::recordingItem)
                    val extra = buildJsonObject {
                        put("nextOffset", offset + items.size)
                    }
                    itemsResult(items, extra)
                }

                "download" -> {
                    val userId = requiredUserId(client)
                    val fileId = args.requiredArgString("fileId")
                    val name = args.argString("fileName") ?: recordingName(client, userId, fileId)
                    downloadDir.mkdirs()
                    val target = uniqueFile(downloadDir, safeFileName(name))
                    val downloaded = client.download(recordingDownloadPath(userId, fileId), target)
                    val linuxDirectory = downloadLinuxDir.trimEnd('/')
                    val result = buildJsonObject {
                        put("fileId", fileId)
                        put("name", downloaded.name)
                        put("size", downloaded.length())
                        put("path", "$linuxDirectory/${downloaded.name}")
                        put("hostPath", downloaded.absolutePath)
                    }
                    textResult(result.toString())
                }

                else -> throw IllegalArgumentException("action 无效 / Invalid action: $action")
            }
        }
    },
)

private suspend fun recordingName(client: MiCloudReadApi, userId: String, fileId: String): String {
    val response = runCatching { client.getJson(recordingsPath(userId, now(), 0, MAX_LIST_LIMIT)) }.getOrNull()
    val items: List<JsonElement> = response?.payloadArray("list").orEmpty()
    val match = items.mapNotNull { it as? JsonObject }
        .firstOrNull { it.stringOrNull("id") == fileId || it.stringOrNull("fileId") == fileId }
    return match?.let(::recordingItem)?.stringOrNull("name") ?: "recording-$fileId"
}

private fun galleryItem(item: JsonObject): JsonObject = buildJsonObject {
    put("id", item.stringOrNull("id") ?: "")
    put("fileName", item.stringOrNull("fileName") ?: item.stringOrNull("name") ?: "")
    item.longOrNull("size")?.let { put("size", it) }
    item.stringOrNull("mimeType")?.let { put("mimeType", it) }
    item.longOrNull("dateTaken")?.let { put("dateTaken", it) }
    item.longOrNull("createTime")?.let { put("createTime", it) }
    item.boolOrNull("isFavorite")?.let { put("favorite", it) }
    item.stringOrNull("type")?.let { put("type", it) }
}

private fun recordingItem(item: JsonObject): JsonObject {
    val id = item.stringOrNull("id") ?: item.stringOrNull("fileId") ?: item.stringOrNull("sid") ?: ""
    val name = item.stringOrNull("fileName") ?: item.stringOrNull("name") ?: item.stringOrNull("title") ?: id
    return buildJsonObject {
        put("id", id)
        put("name", name)
        item.longOrNull("size")?.let { put("size", it) }
        item.longOrNull("duration")?.let { put("duration", it) }
        item.longOrNull("createTime")?.let { put("createTime", it) }
    }
}

private fun uniqueFile(directory: File, name: String): File {
    var candidate = File(directory, name)
    if (!candidate.exists()) return candidate
    val base = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "")
    var index = 2
    while (candidate.exists()) {
        val next = if (extension.isEmpty()) "$base-$index" else "$base-$index.$extension"
        candidate = File(directory, next)
        index++
    }
    return candidate
}

internal fun safeFileName(raw: String): String {
    val normalized = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
    return normalized.take(96).ifBlank { "recording" }
}

internal fun galleriesPath(ts: Long): String = "/gallery/user/galleries?ts=$ts"

internal fun albumsPath(ts: Long): String = "/gallery/user/album/list?isShared=false&ts=$ts"

internal fun recordingsPath(userId: String, ts: Long, offset: Int, limit: Int): String =
    "/sfs/${encodeComponent(userId)}/ns/recorder/dir/0/list?offset=$offset&limit=$limit&ts=$ts"

internal fun recordingDownloadPath(userId: String, fileId: String): String =
    "/sfs/${encodeComponent(userId)}/ns/recorder/file/${encodeComponent(fileId)}/storage"

private val galleryParameters: JsonObject = toolParams {
    put(
        "action",
        propEnum("Action to perform: list media items or albums (default list)", listOf("list", "albums")),
    )
    put("limit", propInt("Number of items to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = 50))
}

private val recordingsParameters: JsonObject = toolParams(required = listOf("action")) {
    put(
        "action",
        propEnum("Action to perform: list or download", listOf("list", "download")),
    )
    put("fileId", propString("Recording file id, required for download"))
    put("fileName", propString("Optional file name to use when downloading"))
    put("offset", propInt("Pagination offset for list", minimum = 0))
    put("limit", propInt("Number of recordings to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT))
}
