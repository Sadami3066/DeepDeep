package io.github.whywood.deepdeep.runtime.host

data class StreamingShellResult(
    val exitCode: Int,
    val output: String,
    val truncated: Boolean,
    val timedOut: Boolean,
    val durationMillis: Long,
    val fullOutputPath: String? = null,
)

fun interface StreamingShellExecutor {
    suspend fun execute(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): StreamingShellResult
}
