package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.JsonValue
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import io.github.whywood.deepdeep.sdk.coding.tools.Truncated
import io.github.whywood.deepdeep.sdk.coding.tools.formatSize
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import io.github.whywood.deepdeep.sdk.coding.tools.truncateTail
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val BASH_MAX_LINES = 2000
private const val BASH_MAX_BYTES = 51200
private const val BASH_MAX_TIMEOUT_SECONDS = 600

private val bashParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to execute")
        }
        putJsonObject("timeout") {
            put("type", "number")
            put("minimum", 1)
            put("maximum", BASH_MAX_TIMEOUT_SECONDS)
            put("description", "Timeout in seconds (1-$BASH_MAX_TIMEOUT_SECONDS, optional, default 60)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

internal fun deepDeepBashTool(shellExecutor: ShellExecutor): CodingTool = CodingTool(
    name = "bash",
    description = "Execute a command in the Linux shell for the current working directory. Returns merged stdout and stderr, ending with the exit code as [exit: N]. The active shell mode, user, and path layout are described in the system prompt environment section. Output is truncated to the last $BASH_MAX_LINES lines or ${BASH_MAX_BYTES / 1024}KB (whichever is hit first). Oversized output is saved to a complete log when available. Cancellation stops execution and is not retried. Optionally provide a timeout in seconds.",
    parameters = bashParameters,
    label = "bash",
    promptGuidelines = listOf(
        "Check the [exit: N] marker on every bash result; investigate failures before moving on.",
        "bash runs the session Linux shell; read the system prompt environment section for its current mode and path layout.",
    ),
    execute = { _toolCallId, args, signal, _onUpdate ->
        val command = args.requiredString("command")
        val timeoutSeconds = args.timeoutSeconds()
        signal?.throwIfAborted()
        var failure: String? = null
        val result = try {
            if (shellExecutor is DetailedShellExecutor) shellExecutor.runDetailed(command, timeoutSeconds, signal)
            else shellExecutor.run(command, timeoutSeconds, signal).let { CommandResult(it.output, it.exitCode, truncated = it.truncated) }
        } catch (aborted: RequestAbortedException) {
            throw aborted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            failure = error.message ?: error.toString()
            null
        }
        if (result == null) {
            textResult(failure ?: "Command failed", null, true)
        } else {
            formatBashResult(result)
        }
    },
)

private fun formatBashResult(result: CommandResult): AgentToolResult {
    val truncation = truncateTail(result.output, maxLines = BASH_MAX_LINES, maxBytes = BASH_MAX_BYTES)
    var text = truncation.text.ifEmpty { "(no output)" }
    val notices = mutableListOf<String>()
    if (truncation.truncated) {
        val totalLines = countOutputLines(result.output)
        val shownLines = countOutputLines(truncation.text)
        val startLine = (totalLines - shownLines + 1).coerceAtLeast(1)
        notices += if (truncation.truncatedBy == "bytes") {
            "Showing lines $startLine-$totalLines of $totalLines (${formatSize(BASH_MAX_BYTES)} limit)"
        } else {
            "Showing lines $startLine-$totalLines of $totalLines"
        }
    }
    if (result.fullOutputPath != null) notices += "Full output: ${result.fullOutputPath}"
    else if (result.truncated) notices += "Output incomplete: upstream truncated it; no complete log is available"
    if (result.timedOut) notices += "Command timed out; it was terminated and was not retried"
    if (notices.isNotEmpty()) {
        text += "\n\n[" + notices.joinToString(". ") + "]"
    }
    val exitCode = result.exitCode
    if (exitCode == null) {
        text += "\n\nCommand terminated without an exit code"
    } else {
        text += "\n\n[exit: $exitCode]"
    }
    val isError = result.timedOut || exitCode == null || exitCode != 0
    return textResult(text, bashDetails(truncation, result), isError)
}

private fun bashDetails(truncation: Truncated, result: CommandResult): JsonValue? {
    if (!truncation.truncated && !result.truncated && !result.timedOut && result.fullOutputPath == null) return null
    return buildJsonObject {
        put("timedOut", result.timedOut)
        result.fullOutputPath?.let { put("fullOutputPath", it) }
        if (truncation.truncated) {
            putJsonObject("truncation") {
                put("truncated", true)
                truncation.truncatedBy?.let { put("truncatedBy", it) }
            }
        }
        if (result.truncated) {
            put("executorTruncated", true)
        }
    }
}

private fun JsonObject.requiredString(name: String): String {
    val value = (this[name] as? JsonPrimitive)?.contentOrNull
    require(!value.isNullOrEmpty()) { "Missing required argument: $name" }
    return value
}

private fun JsonObject.timeoutSeconds(): Long? {
    val primitive = this["timeout"] as? JsonPrimitive ?: return null
    val seconds = primitive.doubleOrNull ?: return null
    if (seconds.isNaN() || seconds.isInfinite() || seconds <= 0.0) {
        throw IllegalArgumentException("Invalid timeout: must be a finite number of seconds")
    }
    if (seconds > BASH_MAX_TIMEOUT_SECONDS) {
        throw IllegalArgumentException("Invalid timeout: maximum is $BASH_MAX_TIMEOUT_SECONDS seconds")
    }
    return kotlin.math.ceil(seconds).toLong()
}

private fun countOutputLines(text: String): Int {
    if (text.isEmpty()) {
        return 0
    }
    val lines = text.split("\n")
    return if (text.endsWith("\n")) lines.size - 1 else lines.size
}
