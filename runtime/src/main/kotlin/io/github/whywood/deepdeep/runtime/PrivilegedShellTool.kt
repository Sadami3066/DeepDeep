package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.bashTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okio.Path.Companion.toPath
import kotlin.math.ceil

private val adbShellParameters: JsonObject = buildJsonObject {
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

internal fun privilegedShellTool(
    name: String,
    description: String,
    parameters: JsonObject,
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    disabledMessage: String,
    status: () -> RootToolStatus,
    unavailableMessage: String,
    failureMessage: String,
): CodingTool {
    val delegate = deepDeepBashTool(RootShellAdapter(executor))
    return CodingTool(
        name = name,
        description = description,
        parameters = parameters,
        label = name,
        execute = { toolCallId, args, signal, onUpdate ->
            signal?.throwIfAborted()
            when {
                !enabled() -> textResult(disabledMessage, isError = true)
                status() == RootToolStatus.Unsupported -> textResult(unavailableMessage, isError = true)
                else -> try {
                    delegate.execute(toolCallId, args.shellArguments(), signal, onUpdate)
                } catch (aborted: RequestAbortedException) {
                    throw aborted
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    textResult(error.message ?: failureMessage, isError = true)
                }
            }
        },
    )
}

fun adbShellTool(
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    status: () -> RootToolStatus,
): CodingTool = privilegedShellTool(
    name = "adb_shell",
    description = "Run a shell command with Android shell privileges and return its exit code and combined output. On rooted devices the command can elevate with su.",
    parameters = adbShellParameters,
    executor = executor,
    enabled = enabled,
    disabledMessage = "ADB shell is unavailable: wireless debugging is not connected.",
    status = status,
    unavailableMessage = "ADB shell is unavailable on this device.",
    failureMessage = "adb_shell execution failed",
)

internal fun JsonObject.shellArguments(): JsonObject {
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

internal const val DEFAULT_SHELL_TIMEOUT_MILLIS = 15_000L
internal const val MAX_SHELL_TIMEOUT_MILLIS = 120_000L
internal const val DEFAULT_SHELL_TIMEOUT_SECONDS = 15L
internal const val MIN_SHELL_TIMEOUT_SECONDS = 1L
internal const val MAX_SHELL_TIMEOUT_SECONDS = 120L
