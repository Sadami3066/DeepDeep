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
            put("description", "Shell command to run through the app's embedded wireless-debugging daemon on the Android device, with ADB shell privileges (uid 2000)")
        }
        putJsonObject("timeout_ms") {
            put("type", "number")
            put("description", "Maximum run time in milliseconds (default 15000, clamped to 1..120000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

internal fun privilegedShellTool(
    name: String,
    description: String,
    promptSnippet: String,
    promptGuidelines: List<String>,
    parameters: JsonObject,
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    disabledMessage: String,
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
    description = "Run a shell command through the app's embedded wireless-debugging daemon on the Android device and return its exit code, stdout and stderr. The daemon runs as the ADB shell (uid 2000); it works in any turn while the ADB tier is connected, with no external app or persistent authorization.",
    promptSnippet = "Run a command through the embedded ADB shell",
    promptGuidelines = listOf(
        "Use adb_shell only when the user explicitly asks for a system-level operation that needs ADB shell privileges; prefer the regular bash tool for all other commands.",
        "adb_shell works in foreground, background, scheduled and goal-continued runs alike; it never persists an authorization.",
        "Commands run as the ADB shell (uid 2000), never as root; use root_shell for root-only operations when root is available.",
        "Do not run destructive commands unless the user explicitly asked for that exact command.",
    ),
    parameters = adbShellParameters,
    executor = executor,
    enabled = enabled,
    disabledMessage = "adb_disabled",
    status = status,
    unavailableMessage = "adb_unavailable",
    failureMessage = "adb_shell 执行失败",
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
