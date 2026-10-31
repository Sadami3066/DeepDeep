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

private val shizukuShellParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to run as the ADB shell (uid 2000) through Shizuku on the Android device")
        }
        putJsonObject("timeout_ms") {
            put("type", "number")
            put("description", "Maximum run time in milliseconds (default 15000, clamped to 1..120000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

internal class ShellGate(
    val allowed: () -> Boolean,
    val message: String,
)

internal fun privilegedShellTool(
    name: String,
    description: String,
    promptSnippet: String,
    promptGuidelines: List<String>,
    parameters: JsonObject,
    executor: RootCommandExecutor,
    gates: List<ShellGate>,
    status: () -> RootToolStatus,
    unavailableMessage: String,
    failureMessage: String,
): CodingTool {
    val delegate = bashTool("/".toPath(), RootShellAdapter(executor))
    return CodingTool(
        name = name,
        description = description,
        parameters = parameters,
        label = name,
        promptSnippet = promptSnippet,
        promptGuidelines = promptGuidelines,
        execute = { toolCallId, args, signal, onUpdate ->
            signal?.throwIfAborted()
            val denied = gates.firstOrNull { gate -> !gate.allowed() }
            when {
                denied != null -> textResult(denied.message, isError = true)
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

fun shizukuShellTool(
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    foreground: () -> Boolean,
    userTurn: () -> Boolean,
    status: () -> RootToolStatus,
): CodingTool = privilegedShellTool(
    name = "shizuku_shell",
    description = "Run a shell command as the ADB shell (uid 2000) through Shizuku on the Android device and return its exit code, stdout and stderr. Only works while the app is in the foreground, Shizuku is running and the user has granted Shizuku permission.",
    promptSnippet = "Run a command as the ADB shell",
    promptGuidelines = listOf(
        "Use shizuku_shell only when the user explicitly asks for a system-level operation that needs ADB shell privileges; prefer the regular bash tool for all other commands.",
        "shizuku_shell runs only while the app is in the foreground and Shizuku permission is granted; it never persists an authorization.",
        "ADB shell (uid 2000) is not root: do not use it for operations that require uid 0.",
        "Do not run destructive commands unless the user explicitly asked for that exact command.",
    ),
    parameters = shizukuShellParameters,
    executor = executor,
    gates = listOf(
        ShellGate({ enabled() }, "shizuku_disabled"),
        ShellGate({ foreground() }, "shizuku_foreground_required"),
        ShellGate({ userTurn() }, "shizuku_user_turn_required"),
    ),
    status = status,
    unavailableMessage = "shizuku_unavailable",
    failureMessage = "shizuku_shell 执行失败",
)

internal fun JsonObject.shellArguments(): JsonObject {
    val command = (this["command"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (command.isEmpty()) throw IllegalArgumentException("command 不能为空")
    val timeoutMillis = shellTimeoutMillis().coerceIn(1, MAX_SHELL_TIMEOUT_MILLIS)
    return buildJsonObject {
        put("command", command)
        put("timeout", ceil(timeoutMillis / 1000.0))
    }
}

private fun JsonObject.shellTimeoutMillis(): Long {
    val primitive = this["timeout_ms"] as? JsonPrimitive ?: return DEFAULT_SHELL_TIMEOUT_MILLIS
    val raw = primitive.contentOrNull ?: return DEFAULT_SHELL_TIMEOUT_MILLIS
    return raw.toLongOrNull() ?: throw IllegalArgumentException("timeout_ms 必须是整数")
}

internal const val DEFAULT_SHELL_TIMEOUT_MILLIS = 15_000L
internal const val MAX_SHELL_TIMEOUT_MILLIS = 120_000L
internal const val DEFAULT_SHELL_TIMEOUT_SECONDS = 15L
internal const val MIN_SHELL_TIMEOUT_SECONDS = 1L
internal const val MAX_SHELL_TIMEOUT_SECONDS = 120L
