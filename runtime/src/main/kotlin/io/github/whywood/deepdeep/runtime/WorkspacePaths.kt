package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.truncateHead
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath

private val FILE_TOOL_NAMES = setOf("read", "write", "edit", "ls", "grep", "find")

internal const val ABSOLUTE_PATH_GUIDELINE =
    "File tool arguments need explicit absolute paths; the workspace is only a suggested location, not an access boundary."

internal fun requireAbsolutePath(path: String): String {
    require(path.startsWith("/")) { "path must be an explicit absolute path: $path" }
    return path.toPath(normalize = true).toString()
}

internal fun CodingTool.withAbsolutePaths(): CodingTool {
    if (name !in FILE_TOOL_NAMES) return this
    val original = this
    val properties = parameters.getValue("properties").jsonObject.toMutableMap()
    properties["path"] = buildJsonObject {
        put("type", "string")
        put("description", "Absolute path in the Android filesystem.")
    }
    val required = (parameters["required"] as? JsonArray).orEmpty().toMutableList()
    if (JsonPrimitive("path") !in required) required += JsonPrimitive("path")
    val schema = JsonObject(parameters + mapOf("properties" to JsonObject(properties), "required" to JsonArray(required)))
    fun prepare(args: JsonObject): JsonObject {
        val prepared = original.prepareArguments?.invoke(args) ?: args
        val raw = (prepared["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("path is required and must be an absolute path")
        return JsonObject(prepared + ("path" to JsonPrimitive(requireAbsolutePath(raw))))
    }
    return CodingTool(
        name = name,
        description = description.replace("relative to the search directory", "as absolute paths"),
        parameters = schema,
        label = label,
        promptSnippet = original.promptSnippet,
        promptGuidelines = original.promptGuidelines.orEmpty() + ABSOLUTE_PATH_GUIDELINE,
        prepareArguments = ::prepare,
        execute = { id, args, signal, update ->
            val prepared = prepare(args)
            val result = original.execute(id, prepared, signal, update)
            if (name !in setOf("ls", "find", "grep") || result.isError == true) {
                result
            } else {
                val path = (prepared.getValue("path") as JsonPrimitive).content
                val base = if (File(path).isFile) File(path).parent.orEmpty() else path
                AgentToolResult(details = result.details, isError = result.isError, content = result.content.map { block ->
                    if (block !is TextContent || block.text.startsWith("No ") || block.text == "(empty directory)") block
                    else {
                        val split = block.text.indexOf("\n\n[")
                        val body = if (split < 0) block.text else block.text.substring(0, split)
                        val notice = if (split < 0) "" else block.text.substring(split)
                        val absolute = body.lineSequence().joinToString("\n") { line -> "${base.trimEnd('/')}/$line" }
                        val bounded = truncateHead(absolute + notice, maxLines = Int.MAX_VALUE)
                        block.copy(text = bounded.text + if (bounded.truncated) "\n\n[Absolute-path output truncated; narrow the query.]" else "")
                    }
                })
            }
        },
    )
}
