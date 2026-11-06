package io.github.whywood.deepdeep.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface ToolResultPayload {
    data class Text(val text: String) : ToolResultPayload

    data class Blocks(val blocks: List<Block>) : ToolResultPayload

    data object Empty : ToolResultPayload

    sealed interface Block {
        data class Text(val text: String) : Block

        data class Image(
            val data: String,
            val mimeType: String,
        ) : Block

        data class FileRef(
            val path: String,
            val mimeType: String? = null,
        ) : Block
    }
}

fun ToolResultPayload.text(): String = when (this) {
    is ToolResultPayload.Text -> text
    is ToolResultPayload.Blocks -> blocks.filterIsInstance<ToolResultPayload.Block.Text>()
        .joinToString("") { it.text }

    ToolResultPayload.Empty -> ""
}

fun fileRefDisplayText(path: String, mimeType: String?): String =
    if (mimeType.isNullOrBlank()) "file: $path" else "file: $path ($mimeType)"

object ToolResultDetails {
    const val FILE_REFS_KEY = "deepdeep.fileRefs"

    fun encodeFileRefs(fileRefs: List<ToolResultPayload.Block.FileRef>): JsonArray = buildJsonArray {
        fileRefs.forEach { fileRef ->
            add(
                buildJsonObject {
                    put("path", fileRef.path)
                    fileRef.mimeType?.takeIf { it.isNotBlank() }?.let { put("mimeType", it) }
                },
            )
        }
    }

    fun decodeFileRefs(details: JsonElement?): List<ToolResultPayload.Block.FileRef> {
        val array = (details as? JsonObject)?.get(FILE_REFS_KEY) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val path = item["path"].stringOrNull() ?: return@mapNotNull null
            ToolResultPayload.Block.FileRef(path, item["mimeType"].stringOrNull())
        }
    }

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
