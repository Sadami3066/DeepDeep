package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.FileMutationQueue
import dev.pi.coding.tools.defaultCodingTools
import dev.pi.coding.tools.textResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val OUTSIDE_WORKSHOP_MESSAGE = "path is outside this workshop app"

internal fun resolveInside(root: Path, input: String): Path? {
    if (input.isBlank()) return null
    val normalizedRoot = root.normalized()
    val candidate = runCatching {
        if (input.startsWith("/")) input.toPath() else normalizedRoot / input
    }.getOrNull() ?: return null
    val normalized = runCatching { candidate.normalized() }.getOrNull() ?: return null
    return normalized.takeIf { isInsideRoot(normalizedRoot, it) }
}

private fun isInsideRoot(root: Path, candidate: Path): Boolean {
    if (candidate.root != root.root) return false
    if (candidate.segments.size < root.segments.size) return false
    return root.segments.indices.all { index -> candidate.segments[index] == root.segments[index] }
}

internal fun workshopPathGuard(root: Path, args: JsonObject): AgentToolResult? {
    val raw = (args["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return if (resolveInside(root, raw) == null) {
        textResult(OUTSIDE_WORKSHOP_MESSAGE, isError = true)
    } else {
        null
    }
}

fun sandboxedFileTools(cwd: Path, fs: FileSystem): List<CodingTool> =
    defaultCodingTools(cwd, fs, FileMutationQueue())
        .filter { it.name != "bash" }
        .map { tool -> sandboxedTool(cwd, tool) }

private fun sandboxedTool(root: Path, tool: CodingTool): CodingTool = CodingTool(
    name = tool.name,
    description = tool.description,
    parameters = tool.parameters,
    label = tool.label,
    promptSnippet = tool.promptSnippet,
    promptGuidelines = tool.promptGuidelines,
    prepareArguments = tool.prepareArguments,
    execute = { toolCallId, args, signal, onUpdate ->
        workshopPathGuard(root, args) ?: tool.execute(toolCallId, args, signal, onUpdate)
    },
)
