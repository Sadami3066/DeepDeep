package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobRegistryTest {
    @Test
    fun incrementalReadConsumesOnlyNewOutput() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val id = registry.start("session-1", "printf", "printf").id!!
        runCurrent()
        val call = runner.calls.single()

        call.output("one\n")
        assertEquals("one\n", registry.readIncremental(id).output)
        call.output("two\n")
        assertEquals("two\n", registry.readIncremental(id).output)
        assertEquals("", registry.readIncremental(id).output)
        assertEquals(JobStatus.RUNNING, registry.readIncremental(id).status)

        call.exit.complete(0)
        runCurrent()
        val settled = registry.readIncremental(id)
        assertEquals("", settled.output)
        assertEquals(JobStatus.DONE, settled.status)
        assertEquals(0, settled.exitCode)
    }

    @Test
    fun ringKeepsBoundedTailAndMarksDroppedOutput() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val id = registry.start("session-1", "yes", "yes").id!!
        runCurrent()
        val call = runner.calls.single()

        call.output("a".repeat(160_000))
        call.output("b".repeat(160_000))
        val incremental = registry.readIncremental(id)
        assertEquals(256 * 1024, incremental.output.length)
        assertTrue(incremental.dropped)

        call.exit.complete(0)
        runCurrent()
        val settled = registry.read(id)
        assertEquals(16 * 1024, settled.output.length)
        assertTrue(settled.dropped)
        assertEquals(320_000, registry.jobs.value.single().outputBytes)
    }

    @Test
    fun killSettlesWaitingReadAsKilled() = runTest {
        val runner = FakeRunner()
        val prompts = mutableListOf<String>()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, prompt -> prompts += prompt })
        val id = registry.start("session-1", "sleep 100", "sleep").id!!
        runCurrent()
        val waiter = async { registry.awaitRead(id, wait = true, timeoutMs = 60_000) }
        runCurrent()

        assertTrue(registry.kill(id, "stop"))
        runCurrent()
        val read = waiter.await()
        assertEquals(JobStatus.KILLED, read.status)
        assertEquals("stop", registry.jobs.value.single().detail)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun waitForSettlementSuppressesNotification() = runTest {
        val runner = FakeRunner()
        val prompts = mutableListOf<String>()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, prompt -> prompts += prompt })
        val id = registry.start("session-1", "make", "build").id!!
        runCurrent()
        val waiter = async { registry.awaitRead(id, wait = true) }
        runCurrent()

        runner.calls.single().exit.complete(0)
        runCurrent()
        val read = waiter.await()
        assertEquals(JobStatus.DONE, read.status)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun clearSettledAndClearSessionRemoveRecords() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val first = registry.start("session-1", "a", "a").id!!
        val second = registry.start("session-1", "b", "b").id!!
        val third = registry.start("session-2", "c", "c").id!!
        runCurrent()
        assertEquals(listOf(first, second, third), registry.jobs.value.map { it.id })

        runner.calls.first().exit.complete(0)
        runCurrent()
        assertEquals(JobStatus.DONE, registry.jobs.value.first { it.id == first }.status)

        registry.clearSettled()
        assertEquals(setOf(second, third), registry.jobs.value.map { it.id }.toSet())

        registry.clearSession("session-1")
        assertEquals(listOf(third), registry.jobs.value.map { it.id })

        registry.clearSession("session-2")
        assertTrue(registry.jobs.value.isEmpty())
    }

    @Test
    fun limitsRunningJobsPerSession() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        repeat(10) { index ->
            assertNotNull(registry.start("session-1", "cmd-$index", "job-$index").id)
        }
        runCurrent()

        val rejected = registry.start("session-1", "cmd-10", "job-10")
        assertNull(rejected.id)
        assertNotNull(rejected.error)

        runCurrent()
        assertNotNull(registry.start("session-2", "cmd-11", "job-11").id)
        runCurrent()
        assertEquals(11, runner.calls.size)
        assertEquals(10, registry.jobs.value.count { it.sessionId == "session-1" && it.status == JobStatus.RUNNING })
    }

    @Test
    fun settlingNotifiesTheBoundSession() = runTest {
        val runner = FakeRunner()
        val notices = mutableListOf<TurnNotice>()
        val prompts = mutableListOf<String>()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, notice, prompt ->
            notices += notice
            prompts += prompt
        })
        val id = registry.start("session-1", "./gradlew build", "build").id!!
        runCurrent()
        runner.calls.single().exit.complete(0)
        runCurrent()

        val prompt = prompts.single()
        assertEquals(TurnNotice.JobFinished("build", false), notices.single())
        assertEquals(
            "background job $id (build) finished [status: done exit=0]; read its output with job_output.",
            prompt,
        )
    }

    @Test
    fun awaitReadTimesOutWhileJobStaysRunning() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val id = registry.start("session-1", "tail -f log", "tail").id!!
        runCurrent()

        val read = registry.awaitRead(id, wait = true, timeoutMs = 0)
        assertEquals("", read.output)
        assertEquals(JobStatus.RUNNING, read.status)
        assertTrue(registry.jobs.value.single().status == JobStatus.RUNNING)
    }

    @Test
    fun keepsOnlyRecentSettledRecords() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val ids = (0 until 55).map { index ->
            val id = registry.start("session-$index", "cmd-$index", "job-$index").id!!
            runCurrent()
            runner.calls.last().exit.complete(0)
            runCurrent()
            id
        }

        val remaining = registry.jobs.value.map { it.id }
        assertEquals(50, remaining.size)
        assertEquals(ids.takeLast(50), remaining)
        assertTrue(registry.read(ids.last()).status == JobStatus.DONE)
        val error = runCatching { registry.read(ids.first()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun prunesSettledRecordsWithoutTouchingRunningJobs() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> })
        val running = registry.start("session-running", "sleep 30", "sleep").id!!
        runCurrent()
        val settledIds = (0 until 55).map { index ->
            val id = registry.start("session-$index", "cmd-$index", "job-$index").id!!
            runCurrent()
            runner.calls.last { it.command == "cmd-$index" }.exit.complete(0)
            runCurrent()
            id
        }

        val remaining = registry.jobs.value
        assertEquals(51, remaining.size)
        assertEquals(JobStatus.RUNNING, remaining.first { it.id == running }.status)
        assertEquals(settledIds.takeLast(50), remaining.filter { it.id != running }.map { it.id })
        assertTrue(runner.calls.size == 56)
    }

    @Test
    fun restoresRunningJobsAsInterrupted() = runTest {
        val directory = Files.createTempDirectory("deepdeep-jobs").toFile()
        try {
            val first = JobRegistry(
                backgroundScope,
                FakeRunner(),
                notifier = { _, _, _ -> },
                persistence = JobPersistence(directory, Dispatchers.Unconfined),
            )
            val id = first.start("session-1", "sleep 100", "sleep").id!!
            runCurrent()

            val second = JobRegistry(
                backgroundScope,
                FakeRunner(),
                notifier = { _, _, _ -> },
                persistence = JobPersistence(directory, Dispatchers.Unconfined),
            )
            val snapshot = second.jobs.value.single { it.id == id }
            assertEquals(JobStatus.INTERRUPTED, snapshot.status)
            val read = second.read(id)
            assertEquals(JobStatus.INTERRUPTED, read.status)
            assertTrue(read.output.contains("上次运行被中断，结果未知"))
            assertTrue(second.readIncremental(id).output.contains("上次运行被中断，结果未知"))

            first.kill(id, "test cleanup")
            runCurrent()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun keepsAtMostFiftyPersistedJobs() = runTest {
        val directory = Files.createTempDirectory("deepdeep-jobs").toFile()
        try {
            val runner = FakeRunner()
            val persistence = JobPersistence(directory, Dispatchers.Unconfined)
            val first = JobRegistry(backgroundScope, runner, notifier = { _, _, _ -> }, persistence = persistence)
            val ids = (0 until 55).map { index ->
                val id = first.start("session-$index", "cmd-$index", "job-$index").id!!
                runCurrent()
                runner.calls.last { it.command == "cmd-$index" }.exit.complete(0)
                runCurrent()
                id
            }
            runCurrent()

            val second = JobRegistry(backgroundScope, FakeRunner(), notifier = { _, _, _ -> }, persistence = persistence)
            assertEquals(ids.takeLast(50), second.jobs.value.map { it.id })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptPersistenceFileIsRebuiltWithoutCrash() = runTest {
        val directory = Files.createTempDirectory("deepdeep-jobs").toFile()
        try {
            val file = File(directory, "jobs.json")
            file.writeText("{not json")
            val runner = FakeRunner()
            val registry = JobRegistry(
                backgroundScope,
                runner,
                notifier = { _, _, _ -> },
                persistence = JobPersistence(directory, Dispatchers.Unconfined),
            )
            assertTrue(registry.jobs.value.isEmpty())

            val id = registry.start("session-1", "echo hi", "echo").id!!
            runCurrent()
            runner.calls.single().exit.complete(0)
            runCurrent()

            val root = Json.parseToJsonElement(file.readText()) as JsonObject
            assertEquals("1", root["schemaVersion"].toString())
            assertEquals(JobStatus.DONE, registry.read(id).status)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeRunner : BackgroundCommandRunner {
        val calls = mutableListOf<Call>()

        class Call(val command: String) {
            lateinit var emit: (String) -> Unit
            val exit = CompletableDeferred<Int>()

            fun output(text: String) {
                emit(text)
            }
        }

        override suspend fun run(command: String, onOutput: (String) -> Unit): Int {
            val call = Call(command)
            call.emit = onOutput
            calls += call
            return call.exit.await()
        }
    }
}
