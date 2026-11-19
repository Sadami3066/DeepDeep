package io.github.whywood.superpi.ui

import io.github.whywood.superpi.runtime.JobSnapshot
import io.github.whywood.superpi.runtime.JobStatus
import io.github.whywood.superpi.runtime.Timer

internal fun sessionIdsWithEnabledTimers(timers: List<Timer>): Set<String> =
    timers.asSequence()
        .filter { it.enabled && it.sessionId.isNotBlank() }
        .map { it.sessionId }
        .toSet()

internal fun sessionIdsWithRunningJobs(jobs: List<JobSnapshot>): Set<String> =
    jobs.asSequence()
        .filter { it.status == JobStatus.RUNNING && it.sessionId.isNotBlank() }
        .map { it.sessionId }
        .toSet()
