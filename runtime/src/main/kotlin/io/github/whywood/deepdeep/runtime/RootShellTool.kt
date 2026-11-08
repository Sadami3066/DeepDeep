package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
    val timedOut: Boolean = false,
)

fun interface RootCommandExecutor {
    suspend fun execute(command: String, timeoutMillis: Long): RootCommandResult
}

private val rootShellParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to run with root privileges")
        }
        putJsonObject("timeoutMs") {
            put("type", "number")
            put("description", "Maximum run time in milliseconds (default 15000, clamped to 1..120000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

internal class RootShellAdapter(private val executor: RootCommandExecutor) : DetailedShellExecutor {
    override suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): CommandResult {
        signal?.throwIfAborted()
        val seconds = (timeoutSeconds ?: DEFAULT_SHELL_TIMEOUT_SECONDS)
            .coerceIn(MIN_SHELL_TIMEOUT_SECONDS, MAX_SHELL_TIMEOUT_SECONDS)
        val result = coroutineScope {
            val execution = async { executor.execute(command, seconds * 1000) }
            val unsubscribe = signal?.onAbort { execution.cancel() }
            try { execution.await() } catch (error: CancellationException) {
                if (signal?.isAborted == true) throw RequestAbortedException(signal.reason)
                throw error
            } finally { unsubscribe?.invoke() }
        }
        signal?.throwIfAborted()
        val output = listOfNotNull(
            result.stdout.takeIf { it.isNotEmpty() },
            result.stderr.takeIf { it.isNotEmpty() },
        ).joinToString("\n")
        return CommandResult(output = output, exitCode = result.exitCode.takeIf { it >= 0 }, truncated = result.truncated, timedOut = result.timedOut)
    }
}

fun rootShellTool(
    executor: RootCommandExecutor,
    enabled: () -> Boolean,
    status: () -> RootToolStatus,
): CodingTool = privilegedShellTool(
    name = "root_shell",
    description = "Run a shell command with root privileges and return its exit code, stdout and stderr. Use only when the user explicitly asks for a system-level operation that needs root; prefer the regular bash tool otherwise.",
    parameters = rootShellParameters,
    executor = executor,
    enabled = enabled,
    disabledMessage = "root_shell is unavailable: select the Root tier and grant root access first.",
    status = status,
    unavailableMessage = "root_shell is unavailable: this device does not provide root access.",
    failureMessage = "root_shell execution failed",
)
