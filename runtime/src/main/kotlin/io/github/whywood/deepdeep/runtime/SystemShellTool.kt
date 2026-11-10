package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.ceil

private val systemShellParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to run with Android shell privileges")
        }
        putJsonObject("timeoutMs") {
            put("type", "number")
            put("description", "Maximum run time in milliseconds (default 15000, clamped to 1..120000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

fun systemShellTool(executor: ShellExecutor): CodingTool {
    val delegate = deepDeepBashTool(executor)
    return CodingTool(
        name = "system_shell",
        description = "Run a command in the Android system shell and return the exit code with merged stdout and stderr. Use it for Android system operations such as pm, cmd, settings, dumpsys, am and getprop; use bash for ordinary Linux work.",
        parameters = systemShellParameters,
        label = "system_shell",
        promptGuidelines = listOf(SYSTEM_SHELL_GUIDELINE),
        execute = { toolCallId, args, signal, onUpdate ->
            signal?.throwIfAborted()
            try {
                delegate.execute(toolCallId, args.shellArguments(), signal, onUpdate)
            } catch (aborted: RequestAbortedException) {
                throw aborted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                textResult(error.message ?: "system_shell execution failed", isError = true)
            }
        },
    )
}

private fun JsonObject.shellArguments(): JsonObject {
    val command = (this["command"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (command.isEmpty()) throw IllegalArgumentException("command must not be empty")
    val timeoutMillis = shellTimeoutMillis().coerceIn(1, MAX_SHELL_TIMEOUT_MILLIS)
    return buildJsonObject {
        put("command", command)
        put("timeout", ceil(timeoutMillis / 1000.0))
    }
}

private fun JsonObject.shellTimeoutMillis(): Long {
    val primitive = this["timeoutMs"] as? JsonPrimitive ?: this["timeout_ms"] as? JsonPrimitive ?: return DEFAULT_SHELL_TIMEOUT_MILLIS
    val raw = primitive.contentOrNull ?: return DEFAULT_SHELL_TIMEOUT_MILLIS
    return raw.toLongOrNull() ?: throw IllegalArgumentException("timeoutMs must be an integer")
}

private const val DEFAULT_SHELL_TIMEOUT_MILLIS = 15_000L
private const val MAX_SHELL_TIMEOUT_MILLIS = 120_000L

private const val SYSTEM_SHELL_GUIDELINE =
    "System shell output may already be truncated upstream; never claim a complete log when none is available."
