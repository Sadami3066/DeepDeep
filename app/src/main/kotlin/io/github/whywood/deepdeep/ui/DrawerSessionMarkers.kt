package io.github.whywood.deepdeep.ui

import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.Timer

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
