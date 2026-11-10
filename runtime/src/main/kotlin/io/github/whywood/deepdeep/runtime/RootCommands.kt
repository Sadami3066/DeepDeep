package io.github.whywood.deepdeep.runtime

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
