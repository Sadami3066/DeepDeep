package io.github.whywood.deepdeep.plugin.micloud

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val DEFAULT_LIST_LIMIT = 20
private const val MAX_LIST_LIMIT = 200
private const val MAX_RESULT_CHARS = 12_000
private const val MAX_SNIPPET_CHARS = 200
private const val MAX_BODY_CHARS = 4_000
private const val COMPACT_MAX_DEPTH = 3
private const val COMPACT_MAX_ARRAY = 12
private const val TOOL_NOTE_WRITE_OFF =
    "笔记写入未开启，请先在插件详情打开写开关 / Notes writing is off, enable the write switch in the plugin settings"
private const val TOOL_TODO_WRITE_OFF =
    "待办写入未开启，请先在插件详情打开写开关 / To-do writing is off, enable the write switch in the plugin settings"
private const val TOOL_AUTH_ERROR = "小米云登录已失效，请重新登录 / Xiaomi Cloud session expired, sign in again"
private const val TOOL_REQUEST_FAILED = "请求小米云失败 / Xiaomi Cloud request failed: "

internal interface MiCloudReadApi {
    suspend fun userId(): String?
    suspend fun getJson(pathWithQuery: String): JsonElement
    suspend fun download(pathWithQuery: String, dest: File): File
}

internal interface MiCloudWriteApi {
    suspend fun post(path: String, form: Map<String, String>): String
}

private class ClientReadApi(private val client: MiCloudClient) : MiCloudReadApi {
    override suspend fun userId(): String? = client.userId()
    override suspend fun getJson(pathWithQuery: String): JsonElement = client.getJson(pathWithQuery)
    override suspend fun download(pathWithQuery: String, dest: File): File = client.download(pathWithQuery, dest)
}

private class WriterWriteApi(private val writer: MiCloudWebWriter) : MiCloudWriteApi {
    override suspend fun post(path: String, form: Map<String, String>): String = writer.post(path, form)
}

fun miCloudTools(
    client: MiCloudClient,
    settings: MiCloudSettings,
    writer: MiCloudWebWriter,
    downloadDir: File,
    downloadLinuxDir: String,
): List<CodingTool> = buildMiCloudTools(
    client = ClientReadApi(client),
    settings = settings,
    writer = WriterWriteApi(writer),
    downloadDir = downloadDir,
    downloadLinuxDir = downloadLinuxDir,
)

internal fun buildMiCloudTools(
    client: MiCloudReadApi,
    settings: MiCloudSettings,
    writer: MiCloudWriteApi,
    downloadDir: File,
    downloadLinuxDir: String,
): List<CodingTool> {
    val capabilities = settings.capabilities.value
    return buildList {
        if (capabilities.accountRead) add(accountTool(client))
        if (capabilities.notesRead) add(notesTool(client, settings, writer))
        if (capabilities.todosRead) add(todoTool(client, settings, writer))
        if (capabilities.smsRead) add(smsTool(client))
        if (capabilities.contactsRead) add(contactsTool(client))
        if (capabilities.galleryRead) add(galleryTool(client))
        if (capabilities.recordingsRead) add(recordingsTool(client, downloadDir, downloadLinuxDir))
    }
}

private fun accountTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_account",
    description = "Read the signed-in Xiaomi Cloud account profile or storage quota. Read-only.",
    parameters = accountParameters,
    label = "mi_cloud_account",
    promptSnippet = "Read Xiaomi Cloud profile or storage quota",
    promptGuidelines = listOf(
        "Use mi_cloud_account only when the user asks about their Xiaomi account or cloud storage.",
        "This tool is read-only: it never changes the account or files.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            val action = args.argString("action") ?: "profile"
            val target = when (action) {
                "profile" -> "/status/lite/profile?ts=${now()}"
                "quota" -> "/status/vip/quota/summary?ts=${now()}"
                else -> throw IllegalArgumentException("action 无效 / Invalid action: $action")
            }
            textResult(compactPayload(client.getJson(target)))
        }
    },
)

private fun notesTool(
    client: MiCloudReadApi,
    settings: MiCloudSettings,
    writer: MiCloudWriteApi,
): CodingTool = CodingTool(
    name = "mi_cloud_notes",
    description = "List, read, create, update or delete notes in Xiaomi Cloud. Reads and writes the user's own notes; writes are only allowed when the notes write switch is on.",
    parameters = notesParameters,
    label = "mi_cloud_notes",
    promptSnippet = "List, read or edit Xiaomi Cloud notes",
    promptGuidelines = listOf(
        "Use mi_cloud_notes for Xiaomi Cloud notes: list and read are read-only, create/update/delete change the account.",
        "List or read before updating or deleting so you have the note id and tag.",
        "Confirm with the user before creating, updating or deleting a note.",
        "If a write is refused because the write switch is off, ask the user to enable notes writing in the Xiaomi Cloud plugin settings.",
        "Note text is private data: only quote what the user asked for.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            when (val action = args.argString("action") ?: "list") {
                "list" -> {
                    val limit = limitArg(args)
                    val response = client.getJson(notesPagePath(now(), limit, args.argString("syncTag")))
                    val payload = response.payloadObject()
                    val entries = payload.arrayOrNull("entries").orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .map(::noteEntry)
                    val extra = buildJsonObject {
                        payload.stringOrNull("syncTag")?.let { put("nextSyncTag", it) }
                        payload.boolOrNull("lastPage")?.let { put("lastPage", it) }
                    }
                    itemsResult(entries, extra)
                }

                "read" -> {
                    val id = args.requiredArgString("id")
                    val entry = noteDetail(client, id)
                    textResult(compactPayload(entry))
                }

                "create" -> {
                    requireWrite(settings, MiCloudFeature.NOTES, TOOL_NOTE_WRITE_OFF)
                    val subject = args.requiredArgString("subject")
                    val content = args.argString("content").orEmpty()
                    val entry = buildNoteEntry(
                        subject = subject,
                        content = content,
                        folderId = args.argString("folderId") ?: "0",
                        colorId = args.argInt("colorId") ?: 0,
                        now = now(),
                    )
                    writeResult(writer.post(noteCreatePath(), noteCreateForm(entry)))
                }

                "update" -> {
                    requireWrite(settings, MiCloudFeature.NOTES, TOOL_NOTE_WRITE_OFF)
                    val id = args.requiredArgString("id")
                    val subject = args.argString("subject")
                    val content = args.argString("content")
                    if (subject == null && content == null) {
                        throw IllegalArgumentException(
                            "update 需要 subject 或 content / update needs subject or content",
                        )
                    }
                    val existing = noteDetail(client, id)
                    val updated = updatedNoteEntry(existing, subject, content, now())
                    writeResult(writer.post(noteUpdatePath(id), noteUpdateForm(updated)))
                }

                "delete" -> {
                    requireWrite(settings, MiCloudFeature.NOTES, TOOL_NOTE_WRITE_OFF)
                    val id = args.requiredArgString("id")
                    val entry = noteDetail(client, id)
                    val tag = entry.stringOrNull("tag")
                        ?: throw IllegalArgumentException("笔记缺少 tag，无法删除 / Note has no tag, cannot delete")
                    val purge = args.argBool("purge") ?: true
                    writeResult(writer.post(noteDeletePath(id), noteDeleteForm(tag, purge)))
                }

                else -> throw IllegalArgumentException("action 无效 / Invalid action: $action")
            }
        }
    },
)

private fun todoTool(
    client: MiCloudReadApi,
    settings: MiCloudSettings,
    writer: MiCloudWriteApi,
): CodingTool = CodingTool(
    name = "mi_cloud_todo",
    description = "List, create, update or delete to-do items in Xiaomi Cloud. Reads and writes the user's own to-do list; writes are only allowed when the to-do write switch is on.",
    parameters = todoParameters,
    label = "mi_cloud_todo",
    promptSnippet = "List or edit Xiaomi Cloud to-do items",
    promptGuidelines = listOf(
        "Use mi_cloud_todo for Xiaomi Cloud to-do items: list is read-only, create/update/delete change the account.",
        "List before updating or deleting so you have the item id.",
        "Confirm with the user before creating, updating or deleting a to-do item.",
        "If a write is refused because the write switch is off, ask the user to enable to-do writing in the Xiaomi Cloud plugin settings.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            when (val action = args.argString("action") ?: "list") {
                "list" -> {
                    val limit = limitArg(args)
                    val response = client.getJson(
                        todoRecordsPath(now(), limit, args.argString("cursor")),
                    )
                    val payload = response.payloadObject()
                    val records = payload.arrayOrNull("records").orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .map(::todoEntry)
                    val syncToken = payload?.get("syncToken")
                    val extra = buildJsonObject {
                        payload.boolOrNull("hasMore")?.let { put("hasMore", it) }
                        if (syncToken != null && syncToken !is JsonNull) put("next", syncToken)
                    }
                    itemsResult(records, extra)
                }

                "create" -> {
                    requireWrite(settings, MiCloudFeature.TODOS, TOOL_TODO_WRITE_OFF)
                    val content = args.requiredArgString("content")
                    val record = buildTodoRecord(
                        content = content,
                        now = now(),
                        remindAt = args.argLong("remindAt"),
                    )
                    writeResult(writer.post(todoCreatePath(), todoCreateForm(record)))
                }

                "update" -> {
                    requireWrite(settings, MiCloudFeature.TODOS, TOOL_TODO_WRITE_OFF)
                    val id = args.requiredArgString("id")
                    val content = args.argString("content")
                    val done = args.argBool("done")
                    val remindAt = args.argLong("remindAt")
                    if (content == null && done == null && remindAt == null) {
                        throw IllegalArgumentException(
                            "update 需要 content、done 或 remindAt / update needs content, done or remindAt",
                        )
                    }
                    val existing = todoRecord(client, id)
                    val eTag = etagOf(existing)
                        ?: throw IllegalArgumentException("待办缺少 eTag，无法更新 / To-do has no eTag, cannot update")
                    val updated = updatedTodoRecord(existing, content, done, remindAt, now())
                    writeResult(writer.post(todoUpdatePath(id), todoUpdateForm(eTag, updated)))
                }

                "delete" -> {
                    requireWrite(settings, MiCloudFeature.TODOS, TOOL_TODO_WRITE_OFF)
                    val id = args.requiredArgString("id")
                    val existing = todoRecord(client, id)
                    val eTag = etagOf(existing)
                        ?: throw IllegalArgumentException("待办缺少 eTag，无法删除 / To-do has no eTag, cannot delete")
                    writeResult(writer.post(todoDeletePath(id), todoDeleteForm(eTag)))
                }

                else -> throw IllegalArgumentException("action 无效 / Invalid action: $action")
            }
        }
    },
)

private fun smsTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_sms",
    description = "List recent SMS threads (optionally with call records) from Xiaomi Cloud. Read-only.",
    parameters = smsParameters,
    label = "mi_cloud_sms",
    promptSnippet = "List Xiaomi Cloud SMS threads",
    promptGuidelines = listOf(
        "Use mi_cloud_sms only when the user asks about their text messages or call records.",
        "This tool is read-only: it never sends, deletes or modifies messages.",
        "Message content is private data: only quote what the user asked for.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            val userId = requiredUserId(client)
            val limit = limitArg(args)
            val response = client.getJson(
                smsThreadPath(
                    userId = userId,
                    ts = now(),
                    limit = limit,
                    withPhoneCall = args.argBool("withPhoneCall") ?: true,
                    syncTag = args.argString("syncTag") ?: "0",
                    syncThreadTag = args.argString("syncThreadTag") ?: "0",
                ),
            )
            val payload = response.payloadObject()
            val entries = response.payloadArray("entries")
                .mapNotNull { it as? JsonObject }
                .map(::compactObject)
            val watermark = payload?.get("watermark")
            val extra = buildJsonObject {
                if (watermark != null && watermark !is JsonNull) put("next", watermark)
            }
            itemsResult(entries, extra)
        }
    },
)

private fun contactsTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_contacts",
    description = "List or search contacts in Xiaomi Cloud. Read-only.",
    parameters = contactsParameters,
    label = "mi_cloud_contacts",
    promptSnippet = "List or search Xiaomi Cloud contacts",
    promptGuidelines = listOf(
        "Use mi_cloud_contacts when the user asks about their contacts.",
        "Use action=list to browse, action=search with query to filter by name, phone or email.",
        "This tool is read-only: it never adds, edits or deletes contacts.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            val userId = requiredUserId(client)
            val limit = limitArg(args)
            val response = client.getJson(contactsPath(userId, now(), limit))
            val query = args.argString("query")
            val contacts = contactObjects(response)
                .map(::compactObject)
                .filter { contact ->
                    if (query == null) {
                        true
                    } else {
                        val needle = query.lowercase(Locale.ROOT)
                        contact.toString().lowercase(Locale.ROOT).contains(needle)
                    }
                }
            itemsResult(contacts)
        }
    },
)

private fun galleryTool(client: MiCloudReadApi): CodingTool = CodingTool(
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

private fun recordingsTool(
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

private suspend fun noteDetail(client: MiCloudReadApi, id: String): JsonObject {
    val payload = client.getJson(noteDetailPath(id, now())).payloadObject()
    return payload.objectOrNull("entry") ?: payload ?: throw IOException("unexpected note response")
}

private suspend fun todoRecord(client: MiCloudReadApi, id: String): JsonObject {
    val payload = client.getJson(todoRecordPath(id)).payloadObject()
    return payload.objectOrNull("record") ?: payload ?: throw IOException("unexpected to-do response")
}

private suspend fun recordingName(client: MiCloudReadApi, userId: String, fileId: String): String {
    val response = runCatching { client.getJson(recordingsPath(userId, now(), 0, MAX_LIST_LIMIT)) }.getOrNull()
    val items: List<JsonElement> = response?.payloadArray("list").orEmpty()
    val match = items.mapNotNull { it as? JsonObject }
        .firstOrNull { it.stringOrNull("id") == fileId || it.stringOrNull("fileId") == fileId }
    return match?.let(::recordingItem)?.stringOrNull("name") ?: "recording-$fileId"
}

private suspend fun requiredUserId(client: MiCloudReadApi): String =
    client.userId() ?: throw MiCloudAuthException("userId is missing, sign in again")

private fun requireWrite(settings: MiCloudSettings, feature: MiCloudFeature, message: String) {
    if (!settings.capabilities.value.writeEnabled(feature)) {
        throw IllegalArgumentException(message)
    }
}

private suspend fun miCloudToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: MiCloudAuthException) {
    textResult(TOOL_AUTH_ERROR, isError = true)
} catch (error: IllegalArgumentException) {
    textResult(error.message ?: "参数无效 / Invalid arguments", isError = true)
} catch (error: IOException) {
    textResult(TOOL_REQUEST_FAILED + error.message.orEmpty(), isError = true)
} catch (error: Exception) {
    textResult(TOOL_REQUEST_FAILED + (error.message ?: error.javaClass.simpleName), isError = true)
}

private fun now(): Long = System.currentTimeMillis()

private fun JsonElement.payloadElement(): JsonElement {
    val root = this as? JsonObject ?: return this
    val data = root["data"] ?: root["D"]
    return if (data != null && data !is JsonNull) data else this
}

private fun JsonElement.payloadObject(): JsonObject? = payloadElement() as? JsonObject

private fun JsonElement.payloadArray(vararg keys: String): JsonArray {
    val element = payloadElement()
    if (element is JsonArray) return element
    val obj = element as? JsonObject ?: return JsonArray(emptyList())
    keys.forEach { key -> (obj[key] as? JsonArray)?.let { return it } }
    return JsonArray(emptyList())
}

private fun JsonObject?.objectOrNull(key: String): JsonObject? = (this?.get(key) as? JsonObject)

private fun JsonObject?.arrayOrNull(key: String): JsonArray? = (this?.get(key) as? JsonArray)

private fun JsonObject?.stringOrNull(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

private fun JsonObject?.longOrNull(key: String): Long? =
    stringOrNull(key)?.toDoubleOrNull()?.toLong()

private fun JsonObject?.intOrNull(key: String): Int? =
    stringOrNull(key)?.toDoubleOrNull()?.toInt()

private fun JsonObject?.boolOrNull(key: String): Boolean? = when (stringOrNull(key)) {
    "true", "1" -> true
    "false", "0" -> false
    else -> null
}

private fun JsonObject.argString(name: String): String? =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.requiredArgString(name: String): String =
    argString(name) ?: throw IllegalArgumentException("缺少参数 / Missing argument: $name")

private fun JsonObject.argInt(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.argLong(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.argBool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun limitArg(args: JsonObject): Int {
    if (!args.containsKey("limit")) return DEFAULT_LIST_LIMIT
    val limit = args.argInt("limit")
        ?: throw IllegalArgumentException("limit 必须是整数 / limit must be an integer")
    if (limit !in 1..MAX_LIST_LIMIT) {
        throw IllegalArgumentException(
            "limit 必须在 1-$MAX_LIST_LIMIT / limit must be between 1 and $MAX_LIST_LIMIT",
        )
    }
    return limit
}

private fun itemsResult(items: List<JsonObject>, extra: JsonObject = JsonObject(emptyMap())): AgentToolResult {
    val kept = mutableListOf<JsonObject>()
    var chars = 0
    for (item in items) {
        val size = item.toString().length + 2
        if (kept.isNotEmpty() && chars + size > MAX_RESULT_CHARS) break
        kept += item
        chars += size
    }
    val result = buildJsonObject {
        put("count", kept.size)
        if (kept.size < items.size) put("truncated", true)
        putJsonArray("items") {
            kept.forEach { item -> add(item) }
        }
        extra.forEach { (key, value) -> put(key, value) }
    }
    return textResult(result.toString())
}

private fun writeResult(body: String): AgentToolResult {
    val trimmed = body.trim()
    val parsed = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
    val text = if (parsed != null) {
        compactJson(parsed.payloadObject() ?: parsed).toString()
    } else {
        trimmed
    }
    return textResult(text.take(MAX_BODY_CHARS))
}

private fun compactPayload(element: JsonElement): String =
    compactJson(element.payloadObject() ?: element).toString()

private fun compactObject(element: JsonObject): JsonObject =
    compactJson(element) as? JsonObject ?: JsonObject(emptyMap())

private fun compactJson(element: JsonElement, depth: Int = 0): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (_, value) ->
            if (depth >= COMPACT_MAX_DEPTH) {
                if (value is JsonPrimitive) value else JsonPrimitive("[...]")
            } else {
                compactJson(value, depth + 1)
            }
        },
    )

    is JsonArray -> JsonArray(
        element.take(COMPACT_MAX_ARRAY).map { item -> compactJson(item, depth + 1) },
    )

    else -> element
}

private fun contactObjects(element: JsonElement): List<JsonObject> {
    val payload = element.payloadElement()
    val source = when (payload) {
        is JsonArray -> payload
        is JsonObject -> payload["content"] ?: payload["contacts"] ?: payload["list"]
        else -> null
    }
    return when (source) {
        is JsonArray -> source.mapNotNull { it as? JsonObject }
        is JsonObject -> source.values.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }
}

private fun noteEntry(item: JsonObject): JsonObject = buildJsonObject {
    put("id", item.stringOrNull("id") ?: "")
    put("subject", item.stringOrNull("subject") ?: "")
    put("snippet", item.stringOrNull("snippet") ?: "")
    item.stringOrNull("tag")?.let { put("tag", it) }
    item.longOrNull("modifyDate")?.let { put("modifyDate", it) }
    item.longOrNull("createDate")?.let { put("createDate", it) }
    item.stringOrNull("folderId")?.let { put("folderId", it) }
}

private fun todoEntry(item: JsonObject): JsonObject {
    val content = item.objectOrNull("contentJson")
    val id = item.stringOrNull("id") ?: content.stringOrNull("id") ?: ""
    val done = content.boolOrNull("is_finish") ?: ((content.intOrNull("isFinish") ?: 0) != 0)
    return buildJsonObject {
        put("id", id)
        put("content", content.stringOrNull("content") ?: content.stringOrNull("plainText") ?: "")
        put("done", done)
        content.longOrNull("remindTime")?.let { put("remindTime", it) }
        content.longOrNull("lastModifiedTime")?.let { put("modifyTime", it) }
        item.stringOrNull("eTag")?.let { put("eTag", it) }
    }
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

private fun etagOf(record: JsonObject): String? =
    (record["eTag"] as? JsonPrimitive)?.contentOrNull
        ?: (record["etag"] as? JsonPrimitive)?.contentOrNull

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

internal fun encodeComponent(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun notesPagePath(ts: Long, limit: Int, syncTag: String?): String {
    val base = "/note/full/page/?ts=$ts&limit=$limit"
    return if (syncTag == null) base else "$base&syncTag=${encodeComponent(syncTag)}"
}

internal fun noteDetailPath(id: String, ts: Long): String = "/note/note/${encodeComponent(id)}/?ts=$ts"

internal fun noteCreatePath(): String = "/note/note"

internal fun noteUpdatePath(id: String): String = "/note/note/${encodeComponent(id)}"

internal fun noteDeletePath(id: String): String = "/note/full/${encodeComponent(id)}/delete"

internal fun todoRecordsPath(ts: Long, limit: Int, cursor: String?): String {
    val base = "/todo/v1/user/records?ts=$ts&limit=$limit"
    return if (cursor == null) base else "$base&syncToken=${encodeComponent(cursor)}"
}

internal fun todoRecordPath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}"

internal fun todoCreatePath(): String = "/todo/v1/user/records"

internal fun todoUpdatePath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}/update"

internal fun todoDeletePath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}/delete"

internal fun smsThreadPath(
    userId: String,
    ts: Long,
    limit: Int,
    withPhoneCall: Boolean,
    syncTag: String,
    syncThreadTag: String,
): String = "/sms/${encodeComponent(userId)}/full/thread" +
    "?limit=$limit&readMode=older&withPhoneCall=$withPhoneCall&_dc=$ts" +
    "&syncTag=${encodeComponent(syncTag)}&syncThreadTag=${encodeComponent(syncThreadTag)}"

internal fun contactsPath(userId: String, ts: Long, limit: Int): String =
    "/contacts/${encodeComponent(userId)}/initdata?limit=$limit&syncTag=0&syncIgnoreTag=0&ts=$ts"

internal fun galleriesPath(ts: Long): String = "/gallery/user/galleries?ts=$ts"

internal fun albumsPath(ts: Long): String = "/gallery/user/album/list?isShared=false&ts=$ts"

internal fun recordingsPath(userId: String, ts: Long, offset: Int, limit: Int): String =
    "/sfs/${encodeComponent(userId)}/ns/recorder/dir/0/list?offset=$offset&limit=$limit&ts=$ts"

internal fun recordingDownloadPath(userId: String, fileId: String): String =
    "/sfs/${encodeComponent(userId)}/ns/recorder/file/${encodeComponent(fileId)}/storage"

internal fun noteCreateForm(entry: JsonObject): Map<String, String> = mapOf("entry" to entry.toString())

internal fun noteUpdateForm(entry: JsonObject): Map<String, String> = mapOf("entry" to entry.toString())

internal fun noteDeleteForm(tag: String, purge: Boolean): Map<String, String> = mapOf(
    "tag" to tag,
    "purge" to if (purge) "1" else "0",
)

internal fun todoCreateForm(record: JsonObject): Map<String, String> = mapOf("record" to record.toString())

internal fun todoUpdateForm(previousETag: String, record: JsonObject): Map<String, String> = mapOf(
    "previousETag" to previousETag,
    "record" to record.toString(),
)

internal fun todoDeleteForm(prevETag: String): Map<String, String> = mapOf("prevETag" to prevETag)

internal fun buildNoteEntry(
    subject: String,
    content: String,
    folderId: String,
    colorId: Int,
    now: Long,
): JsonObject = buildJsonObject {
    put("subject", subject)
    put("snippet", content.take(MAX_SNIPPET_CHARS))
    put("content", content)
    put("createDate", now)
    put("modifyDate", now)
    put("colorId", colorId)
    put("folderId", folderId)
    put("status", "normal")
    put("alertDate", 0)
    putJsonObject("setting") {
        put("themeId", 0)
        put("stickyTime", 0)
        put("version", 0)
    }
}

internal fun updatedNoteEntry(
    existing: JsonObject,
    subject: String?,
    content: String?,
    now: Long,
): JsonObject {
    val values = existing.toMutableMap()
    if (subject != null) values["subject"] = JsonPrimitive(subject)
    if (content != null) {
        values["content"] = JsonPrimitive(content)
        values["snippet"] = JsonPrimitive(content.take(MAX_SNIPPET_CHARS))
    }
    values["modifyDate"] = JsonPrimitive(now)
    return JsonObject(values)
}

internal fun buildTodoRecord(
    content: String,
    now: Long,
    remindAt: Long?,
    done: Boolean = false,
    finishTime: Long = 0,
): JsonObject = buildJsonObject {
    put("type", "entity")
    putJsonObject("contentJson") {
        put("listType", 0)
        put("content", content)
        put("plainText", content)
        put("isFinish", if (done) 1 else 0)
        put("markFinishTime", finishTime)
        put("remindType", if (remindAt != null) 2 else 0)
        put("remindRepeatType", 0)
        put("remindTime", remindAt ?: 0)
        put("firstRemindTime", remindAt ?: 0)
        put("audioFileField", "")
        put("audioFileName", "")
        put("audioFileSize", 0)
        put("createTime", now)
        put("lastModifiedTime", now)
        put("inputType", 0)
        put("id", 0)
        put("customSortId", 0)
        put("expireTime", 0)
        put("hideType", 0)
        put("folderId", 0)
        put("colorLabel", 0)
        put("source", 0)
        put("localStatus", 0)
        put("serverStatus", 0)
        put("is_finish", done)
        putJsonArray("assets") {}
    }
}

internal fun updatedTodoRecord(
    existing: JsonObject,
    content: String?,
    done: Boolean?,
    remindAt: Long?,
    now: Long,
): JsonObject {
    val record = existing.toMutableMap()
    val contentJson = (existing["contentJson"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    if (content != null) {
        contentJson["content"] = JsonPrimitive(content)
        contentJson["plainText"] = JsonPrimitive(content)
    }
    if (done != null) {
        contentJson["isFinish"] = JsonPrimitive(if (done) 1 else 0)
        contentJson["markFinishTime"] = JsonPrimitive(if (done) now else 0)
        contentJson["is_finish"] = JsonPrimitive(done)
    }
    if (remindAt != null) {
        contentJson["remindType"] = JsonPrimitive(2)
        contentJson["remindTime"] = JsonPrimitive(remindAt)
        contentJson["firstRemindTime"] = JsonPrimitive(remindAt)
    }
    contentJson["lastModifiedTime"] = JsonPrimitive(now)
    record["contentJson"] = JsonObject(contentJson)
    return JsonObject(record)
}

private fun toolParams(
    required: List<String> = emptyList(),
    block: JsonObjectBuilder.() -> Unit,
): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties", block)
    if (required.isNotEmpty()) {
        putJsonArray("required") {
            required.forEach { name -> add(JsonPrimitive(name)) }
        }
    }
}

private fun propString(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun propInt(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
    default: Int? = null,
): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
}

private fun propLong(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun propBool(description: String, default: Boolean? = null): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
    default?.let { put("default", it) }
}

private fun propEnum(description: String, values: List<String>): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") {
        values.forEach { value -> add(JsonPrimitive(value)) }
    }
}

private val accountParameters: JsonObject = toolParams {
    put(
        "action",
        propEnum("What to read: profile or quota (default profile)", listOf("profile", "quota")),
    )
}

private val notesParameters: JsonObject = toolParams(required = listOf("action")) {
    put(
        "action",
        propEnum(
            "Action to perform: list, read, create, update or delete",
            listOf("list", "read", "create", "update", "delete"),
        ),
    )
    put("id", propString("Note id, required for read, update and delete"))
    put("subject", propString("Note title, required for create"))
    put("content", propString("Note body text in plain text or Markdown"))
    put("folderId", propString("Note folder id (default 0)"))
    put("colorId", propInt("Note color id (default 0)", minimum = 0))
    put("purge", propBool("Delete permanently instead of moving to trash (default true)", default = true))
    put("syncTag", propString("Sync tag returned by a previous list call for incremental sync"))
    put("limit", propInt("Number of notes to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT))
}

private val todoParameters: JsonObject = toolParams(required = listOf("action")) {
    put(
        "action",
        propEnum(
            "Action to perform: list, create, update or delete",
            listOf("list", "create", "update", "delete"),
        ),
    )
    put("id", propString("To-do item id, required for update and delete"))
    put("content", propString("To-do text, required for create"))
    put("done", propBool("Mark the item finished or unfinished"))
    put("remindAt", propLong("Reminder time in epoch milliseconds"))
    put("cursor", propString("Pagination cursor returned as next by a previous list call"))
    put("limit", propInt("Number of items to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT))
}

private val smsParameters: JsonObject = toolParams {
    put("limit", propInt("Number of threads to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT))
    put("withPhoneCall", propBool("Include call records in the response (default true)", default = true))
    put("syncTag", propString("Sync tag returned by a previous call (default 0)"))
    put("syncThreadTag", propString("Thread sync tag returned by a previous call (default 0)"))
}

private val contactsParameters: JsonObject = toolParams {
    put(
        "action",
        propEnum("Action to perform: list or search (default list)", listOf("list", "search")),
    )
    put("query", propString("Text to match against contact fields, used with action=search"))
    put("limit", propInt("Number of contacts to fetch", minimum = 1, maximum = MAX_LIST_LIMIT, default = 100))
}

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
