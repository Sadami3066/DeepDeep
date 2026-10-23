package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MAX_PRESENTED_FILES = 4

private val presentParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("files") {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", MAX_PRESENTED_FILES)
            put("description", "Files to present to the user, one to four entries")
            put("items", buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Absolute path, or path relative to the current working directory")
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("path")) }
            })
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("files")) })
}

fun presentTool(session: () -> BoundSession?): CodingTool = CodingTool(
    name = "present",
    description = "Present one to four files you generated for the user, such as reports, slides, videos, web pages or images. The chat shows a delivery card with open and share actions for each file.",
    parameters = presentParameters,
    label = "present",
    promptSnippet = "Present generated files to the user",
    promptGuidelines = listOf(
        "Call present at the end of a task when you have produced files the user should see, such as reports, slides, videos, web pages or images.",
        "Paths may be absolute or relative to the current working directory.",
        "Do not repeat the file contents in your reply; the card already lists the files.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        if (session() == null) {
            textResult("Session is not bound", isError = true)
        } else {
            try {
                val files = parsePresentedFiles(args)
                textResult(presentedFilesJson(files).toString())
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

private fun parsePresentedFiles(args: JsonObject): List<String> {
    val array = args["files"] as? JsonArray
        ?: throw IllegalArgumentException("files must be an array")
    if (array.size !in 1..MAX_PRESENTED_FILES) {
        throw IllegalArgumentException("files needs 1..$MAX_PRESENTED_FILES entries")
    }
    return array.mapIndexed { index, element ->
        val item = element as? JsonObject
            ?: throw IllegalArgumentException("files[$index] must be an object")
        val path = item.argumentString("path").orEmpty()
        if (path.isEmpty()) {
            throw IllegalArgumentException("files[$index].path must not be empty")
        }
        path
    }
}

private fun presentedFilesJson(files: List<String>): JsonObject = buildJsonObject {
    putJsonArray("files") {
        files.forEach { path ->
            add(buildJsonObject { put("path", path) })
        }
    }
}

private fun JsonObject.argumentString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
