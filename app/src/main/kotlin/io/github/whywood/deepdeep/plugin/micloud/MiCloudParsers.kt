package io.github.whywood.deepdeep.plugin.micloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class MiCloudProfile(
    val nickname: String?,
    val userId: String?,
    val icon: String?,
)

internal data class MiCloudNote(
    val id: String,
    val subject: String,
    val snippet: String,
    val folderId: String?,
    val modifyDate: Long,
)

internal data class MiCloudNotesPage(
    val notes: List<MiCloudNote>,
    val lastPage: Boolean,
    val syncTag: String?,
)

internal data class MiCloudNoteContent(
    val id: String,
    val subject: String,
    val content: String,
)

internal data class MiCloudTodo(
    val id: String,
    val content: String,
    val finished: Boolean,
    val createTime: Long,
    val modifyTime: Long,
    val eTag: String?,
)

internal fun parseCookie(cookie: String): Map<String, String> = buildMap {
    cookie.split(';').forEach { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) return@forEach
        val name = part.substring(0, separator).trim()
        if (name.isEmpty()) return@forEach
        put(name, part.substring(separator + 1).trim())
    }
}

internal fun miCloudResponseData(raw: JsonElement): JsonObject? {
    val root = raw as? JsonObject ?: return null
    (root["data"] as? JsonObject)?.let { return it }
    (root["D"] as? JsonObject)?.let { return it }
    return root
}

internal fun isMiCloudAuthFailure(raw: JsonElement): Boolean {
    val root = raw as? JsonObject ?: return false
    val code = root.stringOrNull("R") ?: root.stringOrNull("code") ?: return false
    return code == "401" || code == "403"
}

internal fun parseProfile(raw: JsonElement): MiCloudProfile? {
    val data = miCloudResponseData(raw) ?: return null
    val nickname = data.stringOrNull("nickname")?.takeIf { it.isNotBlank() }
    val userId = data.stringOrNull("userId")
    if (nickname == null && userId == null) return null
    return MiCloudProfile(nickname, userId, data.stringOrNull("icon"))
}

internal fun parseNotesPage(raw: JsonElement): MiCloudNotesPage? {
    val data = miCloudResponseData(raw) ?: return null
    val entries = data["entries"] as? JsonArray ?: return null
    val notes = entries.mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val id = item.stringOrNull("id") ?: return@mapNotNull null
        MiCloudNote(
            id = id,
            subject = item.stringOrNull("subject").orEmpty(),
            snippet = item.stringOrNull("snippet").orEmpty(),
            folderId = item.stringOrNull("folderId"),
            modifyDate = item.longOrNull("modifyDate") ?: 0L,
        )
    }
    return MiCloudNotesPage(
        notes = notes,
        lastPage = data.boolOrNull("lastPage") ?: false,
        syncTag = data.stringOrNull("syncTag"),
    )
}

internal fun parseNote(raw: JsonElement): MiCloudNoteContent? {
    val data = miCloudResponseData(raw) ?: return null
    val entry = data["entry"] as? JsonObject ?: return null
    val id = entry.stringOrNull("id") ?: return null
    return MiCloudNoteContent(
        id = id,
        subject = entry.stringOrNull("subject").orEmpty(),
        content = entry.stringOrNull("content").orEmpty(),
    )
}

internal fun parseTodos(raw: JsonElement): List<MiCloudTodo> {
    val data = miCloudResponseData(raw) ?: return emptyList()
    val records = data["records"] as? JsonArray ?: return emptyList()
    return records.mapNotNull { record ->
        val item = record as? JsonObject ?: return@mapNotNull null
        val content = item["contentJson"] as? JsonObject ?: return@mapNotNull null
        val id = item.stringOrNull("id") ?: content.stringOrNull("id") ?: return@mapNotNull null
        MiCloudTodo(
            id = id,
            content = content.stringOrNull("content").orEmpty(),
            finished = content.boolOrNull("is_finish")
                ?: (content.longOrNull("isFinish") == 1L),
            createTime = content.longOrNull("createTime") ?: 0L,
            modifyTime = content.longOrNull("lastModifiedTime") ?: 0L,
            eTag = item.stringOrNull("eTag"),
        )
    }
}

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.boolOrNull(name: String): Boolean? = when (val value = (this[name] as? JsonPrimitive)?.contentOrNull) {
    "true", "1" -> true
    "false", "0" -> false
    else -> null
}
