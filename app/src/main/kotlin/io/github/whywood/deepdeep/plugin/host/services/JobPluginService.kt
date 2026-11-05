package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.JobInfo
import io.github.whywood.deepdeep.plugin.api.JobRead
import io.github.whywood.deepdeep.plugin.api.JobService
import io.github.whywood.deepdeep.plugin.api.JobStatus
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.JobStatus as RuntimeJobStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal class JobPluginService(
    private val jobs: JobRegistry,
    private val activeSessionId: StateFlow<String?>?,
) : JobService {
    override fun observe(): Flow<List<JobInfo>> = jobs.jobs.map { snapshots ->
        snapshots.map { snapshot ->
            JobInfo(
                id = snapshot.id,
                label = snapshot.label,
                command = snapshot.command,
                status = snapshot.status.toPluginStatus(),
                startedAt = snapshot.startedAt,
                finishedAt = snapshot.finishedAt,
                exitCode = snapshot.exitCode,
                outputBytes = snapshot.outputBytes,
            )
        }
    }

    override suspend fun start(command: String, label: String, cwd: String?, sessionId: String?): String? {
        val target = sessionId ?: activeSessionId?.value ?: return null
        return jobs.start(target, command, label, cwd).id
    }

    override suspend fun read(jobId: String): JobRead {
        val read = jobs.read(jobId)
        return JobRead(
            output = read.output,
            status = read.status.toPluginStatus(),
            exitCode = read.exitCode,
            dropped = read.dropped,
        )
    }

    override suspend fun kill(jobId: String): Boolean = jobs.kill(jobId, null)

    private fun RuntimeJobStatus.toPluginStatus(): JobStatus = when (this) {
        RuntimeJobStatus.RUNNING -> JobStatus.RUNNING
        RuntimeJobStatus.DONE -> JobStatus.DONE
        RuntimeJobStatus.FAILED -> JobStatus.FAILED
        RuntimeJobStatus.KILLED -> JobStatus.KILLED
        RuntimeJobStatus.INTERRUPTED -> JobStatus.INTERRUPTED
    }
}
