package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.StateFlow

enum class JobStatus {
    RUNNING,
    DONE,
    FAILED,
    KILLED,
}

data class JobSnapshot(
    val id: String,
    val sessionId: String,
    val label: String,
    val command: String,
    val status: JobStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exitCode: Int? = null,
    val detail: String? = null,
    val outputBytes: Long = 0,
)

data class JobStart(
    val id: String?,
    val error: String? = null,
)

data class JobRead(
    val output: String,
    val status: JobStatus,
    val exitCode: Int? = null,
    val dropped: Boolean = false,
)

interface JobController {
    val jobs: StateFlow<List<JobSnapshot>>

    fun start(sessionId: String, command: String, label: String): JobStart

    suspend fun read(jobId: String): JobRead

    suspend fun kill(jobId: String, reason: String?): Boolean

    fun clearSettled()

    fun clearSession(sessionId: String)
}

fun interface BackgroundCommandRunner {
    suspend fun run(command: String, onOutput: (String) -> Unit): Int
}
