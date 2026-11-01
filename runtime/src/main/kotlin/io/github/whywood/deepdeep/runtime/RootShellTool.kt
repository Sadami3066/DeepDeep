package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult
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

internal class RootShellAdapter(private val executor: RootCommandExecutor) : ShellExecutor {
    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
        signal?.throwIfAborted()
        val seconds = (timeoutSeconds ?: DEFAULT_SHELL_TIMEOUT_SECONDS)
            .coerceIn(MIN_SHELL_TIMEOUT_SECONDS, MAX_SHELL_TIMEOUT_SECONDS)
        val result = executor.execute(command, seconds * 1000)
        val output = listOfNotNull(
            result.stdout.takeIf { it.isNotEmpty() },
            result.stderr.takeIf { it.isNotEmpty() },
        ).joinToString("\n")
        return ShellResult(output = output, exitCode = result.exitCode, truncated = result.truncated)
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
