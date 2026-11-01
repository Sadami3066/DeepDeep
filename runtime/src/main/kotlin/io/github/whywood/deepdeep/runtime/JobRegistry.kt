package io.github.whywood.deepdeep.runtime

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class JobRegistry(
    private val scope: CoroutineScope,
    private val runner: BackgroundCommandRunner? = null,
    private val notifier: suspend (sessionId: String, notice: TurnNotice, prompt: String) -> Unit,
    private val wakeEnabled: () -> Boolean = { true },
    private val runnerFactory: ((String?) -> BackgroundCommandRunner)? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxRunningPerSession: () -> Int = { MAX_RUNNING_PER_SESSION },
) : JobController {
    private val lock = Any()
    private val records = LinkedHashMap<String, Record>()
    private val jobsState = MutableStateFlow<List<JobSnapshot>>(emptyList())
    private var sequence = 0L
    private val publishLock = Any()
    private var lastPublishAt = 0L
    private var pendingPublish: Job? = null

    override val jobs: StateFlow<List<JobSnapshot>> = jobsState.asStateFlow()

    override fun start(sessionId: String, command: String, label: String): JobStart =
        start(sessionId, command, label, null)

    fun start(sessionId: String, command: String, label: String, cwd: String?): JobStart {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return JobStart(null, "command is required")
        if (sessionId.isBlank()) return JobStart(null, "session is required")
        val activeRunner = runnerFor(cwd)
        val record = synchronized(lock) {
            val running = records.values.count { it.sessionId == sessionId && it.isRunning() }
            val limit = maxRunningPerSession()
            if (running >= limit) {
                return JobStart(null, "too many running jobs for this session (max $limit)")
            }
            val created = Record(
                id = newIdLocked(),
                sequence = ++sequence,
                sessionId = sessionId,
                label = label.trim().ifEmpty { commandPreview(trimmedCommand) },
                command = trimmedCommand,
                startedAt = System.currentTimeMillis(),
                runner = activeRunner,
            )
            records[created.id] = created
            pruneSettledLocked()
            created
        }
        publish()
        val coroutine = scope.launch(start = CoroutineStart.LAZY) { execute(record) }
        record.coroutine = coroutine
        coroutine.start()
        return JobStart(record.id)
    }

    fun readIncremental(jobId: String): JobRead = find(jobId).readIncremental()

    fun remove(jobId: String) {
        val removed = synchronized(lock) {
            val record = records[jobId] ?: return
            if (record.isRunning()) return
            records.remove(jobId)
        }
        publish()
    }

    suspend fun awaitRead(jobId: String, wait: Boolean, timeoutMs: Long = DEFAULT_WAIT_MS): JobRead {
        val record = find(jobId)
        if (!wait) return record.readIncremental()
        val limit = timeoutMs.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
        val immediate = record.beginWait()
        if (immediate != null) return immediate
        try {
            val read = withTimeoutOrNull(limit) {
                var current = record.readIncremental()
                while (current.output.isEmpty() && current.status == JobStatus.RUNNING) {
                    record.change.receive()
                    current = record.readIncremental()
                }
                current
            }
            return read ?: record.readIncremental()
        } finally {
            record.endWait()
        }
    }

    override suspend fun read(jobId: String): JobRead = find(jobId).readTail()

    override suspend fun kill(jobId: String, reason: String?): Boolean {
        val record = synchronized(lock) { records[jobId] } ?: return false
        val coroutine = synchronized(record.lock) {
            if (record.status != JobStatus.RUNNING) return false
            record.killRequested = true
            if (!reason.isNullOrBlank()) record.detail = reason.trim()
            record.coroutine
        }
        coroutine?.cancel()
        publish()
        return true
    }

    override fun clearSettled() {
        synchronized(lock) {
            records.values.removeAll { !it.isRunning() }
        }
        publish()
    }

    override fun clearSession(sessionId: String) {
        val removed = synchronized(lock) {
            val matches = records.values.filter { it.sessionId == sessionId }
            matches.forEach { records.remove(it.id) }
            matches
        }
        removed.forEach { record ->
            synchronized(record.lock) {
                record.killRequested = true
                record.awaited = true
            }
            record.coroutine?.cancel()
            record.change.trySend(Unit)
        }
        publish()
    }

    private fun runnerFor(cwd: String?): BackgroundCommandRunner =
        runnerFactory?.invoke(cwd)
            ?: runner
            ?: error("no background command runner configured")

    private fun find(jobId: String): Record =
        synchronized(lock) { records[jobId] }
            ?: throw IllegalArgumentException("job not found: $jobId")

    private fun newIdLocked(): String {
        while (true) {
            val id = "job-" + UUID.randomUUID().toString().replace("-", "").take(8)
            if (!records.containsKey(id)) return id
        }
    }

    private suspend fun execute(record: Record) {
        val runner = record.runner ?: return
        try {
            val exitCode = runner.run(record.command) { chunk ->
                record.append(chunk)
                publishOutput()
            }
            settle(record, exitCode, null, false)
        } catch (cancelled: CancellationException) {
            settle(record, null, null, true)
            throw cancelled
        } catch (error: Exception) {
            settle(record, null, error.message ?: error.toString(), false)
        }
    }

    private suspend fun settle(record: Record, exitCode: Int?, detail: String?, cancelled: Boolean) {
        val wake = wakeEnabled()
        var notify = false
        var snapshot: JobSnapshot? = null
        synchronized(record.lock) {
            if (record.status != JobStatus.RUNNING) return
            record.status = when {
                record.killRequested || cancelled -> JobStatus.KILLED
                detail != null -> JobStatus.FAILED
                exitCode == 0 -> JobStatus.DONE
                else -> JobStatus.FAILED
            }
            record.finishedAt = System.currentTimeMillis()
            record.exitCode = exitCode
            if (detail != null) record.detail = detail
            record.ring.seal(SETTLED_LIMIT)
            if (record.awaiters > 0) record.awaited = true
            notify = !record.awaited && !record.killRequested && !cancelled && wake
            snapshot = record.snapshot()
            record.change.trySend(Unit)
        }
        record.coroutine = null
        val registered = synchronized(lock) {
            val present = records[record.id] === record
            if (present) pruneSettledLocked()
            present
        }
        if (!registered) return
        publish()
        if (notify) {
            val finished = snapshot ?: return
            try {
                notifier(
                    record.sessionId,
                    TurnNotice.JobFinished(record.label, finished.status == JobStatus.FAILED),
                    finishPrompt(finished),
                )
            } catch (cancelledError: CancellationException) {
                throw cancelledError
            } catch (_: Exception) {
            }
        }
    }

    private fun pruneSettledLocked() {
        val settled = records.values.filter { !it.isRunning() }
        if (settled.size <= MAX_SETTLED_RECORDS) return
        settled.sortedBy { it.sequence }
            .take(settled.size - MAX_SETTLED_RECORDS)
            .forEach { record -> records.remove(record.id) }
    }

    private fun publish() {
        synchronized(publishLock) {
            pendingPublish?.cancel()
            pendingPublish = null
            lastPublishAt = now()
        }
        synchronized(lock) {
            jobsState.value = records.values
                .sortedWith(compareBy({ it.startedAt }, { it.sequence }))
                .map { it.snapshot() }
        }
    }

    private fun publishOutput() {
        var scheduled: Job? = null
        synchronized(publishLock) {
            if (pendingPublish != null) return
            val wait = (PUBLISH_INTERVAL_MS - (now() - lastPublishAt)).coerceAtLeast(0L)
            val job = scope.launch(start = CoroutineStart.LAZY) {
                delay(wait)
                synchronized(publishLock) {
                    pendingPublish = null
                    lastPublishAt = now()
                }
                publish()
            }
            pendingPublish = job
            scheduled = job
        }
        scheduled?.start()
    }

    private class Record(
        val id: String,
        val sequence: Long,
        val sessionId: String,
        val label: String,
        val command: String,
        val startedAt: Long,
        val runner: BackgroundCommandRunner?,
    ) {
        val lock = Any()
        val ring = OutputRing()
        val change = Channel<Unit>(Channel.CONFLATED)
        var status = JobStatus.RUNNING
        var finishedAt: Long? = null
        var exitCode: Int? = null
        var detail: String? = null
        var cursor = 0L
        var awaiters = 0
        var awaited = false
        var killRequested = false

        @Volatile
        var coroutine: Job? = null

        fun append(chunk: String) {
            ring.append(chunk)
            change.trySend(Unit)
        }

        fun isRunning(): Boolean = synchronized(lock) { status == JobStatus.RUNNING }

        fun readIncremental(): JobRead = synchronized(lock) { readIncrementalLocked() }

        fun readTail(): JobRead = synchronized(lock) {
            val (output, dropped) = ring.snapshot()
            JobRead(output, status, exitCode, dropped)
        }

        fun beginWait(): JobRead? = synchronized(lock) {
            val read = readIncrementalLocked()
            if (read.status != JobStatus.RUNNING) {
                awaited = true
                return read
            }
            if (read.output.isNotEmpty()) return read
            awaiters += 1
            null
        }

        fun endWait() {
            synchronized(lock) {
                if (awaiters > 0) awaiters -= 1
            }
        }

        fun snapshot(): JobSnapshot = synchronized(lock) {
            JobSnapshot(
                id = id,
                sessionId = sessionId,
                label = label,
                command = command,
                status = status,
                startedAt = startedAt,
                finishedAt = finishedAt,
                exitCode = exitCode,
                detail = detail,
                outputBytes = ring.totalBytes(),
            )
        }

        private fun readIncrementalLocked(): JobRead {
            val (output, offset, dropped) = ring.drain(cursor)
            cursor = offset
            return JobRead(output, status, exitCode, dropped)
        }
    }

    private class OutputRing {
        private val lock = Any()
        private val buffer = ByteArray(LIVE_LIMIT)
        private var head = 0
        private var size = 0
        private var startOffset = 0L
        private var endOffset = 0L
        private var dropped = false
        private var sealed = false

        fun append(chunk: String) {
            if (chunk.isEmpty()) return
            val bytes = chunk.toByteArray(Charsets.UTF_8)
            synchronized(lock) {
                if (sealed) return
                val count = bytes.size
                val total = size + count
                if (total <= buffer.size) {
                    write(bytes, 0, count)
                } else {
                    val overflow = total - buffer.size
                    dropped = true
                    startOffset += overflow
                    if (count >= buffer.size) {
                        head = 0
                        size = 0
                        write(bytes, count - buffer.size, buffer.size)
                    } else {
                        head = (head + overflow) % buffer.size
                        size -= overflow
                        write(bytes, 0, count)
                    }
                }
                endOffset += count
            }
        }

        fun seal(limit: Int) {
            synchronized(lock) {
                sealed = true
                if (size <= limit) return
                val drop = size - limit
                head = (head + drop) % buffer.size
                size = limit
                startOffset += drop
                dropped = true
            }
        }

        fun drain(offset: Long): Triple<String, Long, Boolean> = synchronized(lock) {
            val from = offset.coerceIn(startOffset, endOffset)
            val lost = offset < startOffset
            val count = (endOffset - from).toInt()
            val next = endOffset
            Triple(readAt(from, count), next, lost)
        }

        fun snapshot(): Pair<String, Boolean> = synchronized(lock) {
            readAt(startOffset, size) to dropped
        }

        fun markDropped() {
            synchronized(lock) {
                dropped = true
            }
        }

        fun totalBytes(): Long = synchronized(lock) { endOffset }

        private fun write(bytes: ByteArray, offset: Int, count: Int) {
            val tail = (head + size) % buffer.size
            val first = minOf(count, buffer.size - tail)
            System.arraycopy(bytes, offset, buffer, tail, first)
            if (count > first) System.arraycopy(bytes, offset + first, buffer, 0, count - first)
            size += count
        }

        private fun readAt(offset: Long, count: Int): String {
            if (count <= 0) return ""
            val bytes = ByteArray(count)
            val index = (head + (offset - startOffset)).toInt() % buffer.size
            val first = minOf(count, buffer.size - index)
            System.arraycopy(buffer, index, bytes, 0, first)
            if (count > first) System.arraycopy(buffer, 0, bytes, first, count - first)
            return decode(bytes)
        }

        private fun decode(bytes: ByteArray): String {
            var start = 0
            while (start < bytes.size && (bytes[start].toInt() and 0xC0) == 0x80) start++
            return String(bytes, start, bytes.size - start, Charsets.UTF_8)
        }
    }

}

private const val MAX_RUNNING_PER_SESSION = 10
private const val MAX_SETTLED_RECORDS = 50
private const val LIVE_LIMIT = 256 * 1024
private const val SETTLED_LIMIT = 16 * 1024
private const val MIN_WAIT_MS = 1L
private const val DEFAULT_WAIT_MS = 30_000L
private const val MAX_WAIT_MS = 600_000L
private const val PUBLISH_INTERVAL_MS = 100L

internal fun jobStatusText(status: JobStatus, exitCode: Int?): String = when (status) {
    JobStatus.RUNNING -> "running"
    JobStatus.DONE -> "done exit=${exitCode ?: 0}"
    JobStatus.FAILED -> exitCode?.let { "failed exit=$it" } ?: "failed"
    JobStatus.KILLED -> "killed"
    JobStatus.INTERRUPTED -> "interrupted unknown"
}

private fun finishPrompt(snapshot: JobSnapshot): String =
    "background job ${snapshot.id} (${snapshot.label}) finished " +
        "[status: ${jobStatusText(snapshot.status, snapshot.exitCode)}]; " +
        "read its output with job_output."

private fun commandPreview(command: String): String {
    val line = command.lineSequence().firstOrNull().orEmpty().trim()
    return if (line.length <= 48) line else line.take(48)
}
