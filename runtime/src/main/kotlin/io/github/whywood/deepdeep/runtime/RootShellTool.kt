package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class RootToolStatus {
    Unsupported,
    NotRequested,
    Granted,
    Denied,
    Timeout,
    Error,
}

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val truncated: Boolean,
)

fun interface RootCommandExecutor {
    suspend fun execute(command: String, timeoutMillis: Long): RootCommandResult
}

private val rootShellParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to run as root on the Android device")
        }
        putJsonObject("timeout_ms") {
            put("type", "number")
            put("description", "Maximum run time in milliseconds (default 15000, clamped to 1..120000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

fun rootShellTool(
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    foreground: () -> Boolean,
    userTurn: () -> Boolean,
    status: () -> RootToolStatus,
): CodingTool = CodingTool(
    name = "root_shell",
    description = "Run a shell command as root (su) on the Android device and return its exit code, stdout and stderr. Only works while the app is in the foreground and the user has granted root access.",
    parameters = rootShellParameters,
    label = "root_shell",
    promptSnippet = "Run a command as root",
    promptGuidelines = listOf(
        "Use root_shell only when the user explicitly asks for a system-level operation that requires root; prefer the regular bash tool for all other commands.",
        "root_shell runs only while the app is in the foreground and root access is granted; it never persists authorization.",
        "Do not run destructive root commands unless the user explicitly asked for that exact command.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        when {
            !enabled() -> textResult("root_disabled", isError = true)
            !foreground() -> textResult("root_foreground_required", isError = true)
            !userTurn() -> textResult("root_user_turn_required", isError = true)
            status() == RootToolStatus.Unsupported -> textResult("root_unavailable", isError = true)
            else -> executeRootCommand(executor, args)
        }
    },
)

private suspend fun executeRootCommand(executor: RootCommandExecutor, args: JsonObject): AgentToolResult =
    try {
        val command = (args["command"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (command.isEmpty()) throw IllegalArgumentException("command 不能为空")
        val timeoutMillis = args.rootTimeoutMillis().coerceIn(1, MAX_ROOT_TIMEOUT_MILLIS)
        val result = executor.execute(command, timeoutMillis)
        textResult(rootResultJson(result).toString())
    } catch (aborted: RequestAbortedException) {
        throw aborted
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        textResult(error.message ?: "root_shell 执行失败", isError = true)
    }

private fun rootResultJson(result: RootCommandResult): JsonObject = buildJsonObject {
    put("exitCode", result.exitCode)
    put("stdout", result.stdout)
    put("stderr", result.stderr)
    put("truncated", result.truncated)
}

private fun JsonObject.rootTimeoutMillis(): Long {
    val primitive = this["timeout_ms"] as? JsonPrimitive ?: return DEFAULT_ROOT_TIMEOUT_MILLIS
    val raw = primitive.contentOrNull ?: return DEFAULT_ROOT_TIMEOUT_MILLIS
    return raw.toLongOrNull() ?: throw IllegalArgumentException("timeout_ms 必须是整数")
}

private const val DEFAULT_ROOT_TIMEOUT_MILLIS = 15_000L
private const val MAX_ROOT_TIMEOUT_MILLIS = 120_000L
