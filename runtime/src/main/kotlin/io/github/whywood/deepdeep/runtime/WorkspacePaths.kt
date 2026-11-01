package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path

private const val WORKSPACE_ROOT = "/workspace"

private val FILE_TOOL_NAMES = setOf("read", "write", "edit", "ls", "grep", "find")

internal fun mapWorkspacePath(cwd: Path, path: String): String = when {
    path == WORKSPACE_ROOT -> cwd.toString()
    path.startsWith("$WORKSPACE_ROOT/") -> {
        val remainder = path.removePrefix("$WORKSPACE_ROOT/").trimStart('/')
        if (remainder.isEmpty()) cwd.toString() else cwd.resolve(remainder, normalize = true).toString()
    }
    else -> path
}

internal fun CodingTool.withWorkspacePaths(cwd: Path): CodingTool {
    if (name !in FILE_TOOL_NAMES) return this
    return CodingTool(
        name = name,
        description = "$description Paths beginning with $WORKSPACE_ROOT/ are resolved from the workspace root.",
        parameters = parameters,
        label = label,
        promptSnippet = null,
        promptGuidelines = null,
        prepareArguments = { args ->
            (prepareArguments?.invoke(args) ?: args).withWorkspacePath(cwd)
        },
        execute = execute,
    )
}

private fun JsonObject.withWorkspacePath(cwd: Path): JsonObject {
    val raw = (this["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return this
    val mapped = mapWorkspacePath(cwd, raw)
    if (mapped == raw) return this
    return buildJsonObject {
        forEach { (key, value) -> if (key != "path") put(key, value) }
        put("path", mapped)
    }
}
