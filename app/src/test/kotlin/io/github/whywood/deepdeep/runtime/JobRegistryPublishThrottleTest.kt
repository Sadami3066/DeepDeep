package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobRegistryPublishThrottleTest {
    @Test
    fun outputPublishesAreThrottledToTenPerSecond() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(
            scope = backgroundScope,
            runner = runner,
            notifier = { _, _, _ -> },
            now = { testScheduler.currentTime },
        )
        val published = mutableListOf<List<JobSnapshot>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.jobs.collect { snapshot -> published += snapshot }
        }
        runCurrent()

        registry.start("session-1", "yes", "yes").id!!
        runCurrent()
        val call = runner.calls.single()
        repeat(1_000) {
            call.output("x")
            advanceTimeBy(1)
        }
        advanceTimeBy(100)

        val outputPublishes = published.count { snapshot ->
            snapshot.singleOrNull()?.let { job -> job.status == JobStatus.RUNNING && job.outputBytes > 0 } == true
        }
        assertTrue("output publishes=$outputPublishes", outputPublishes in 1..10)
        assertTrue("total publishes=${published.size}", published.size <= 13)
        assertEquals(1_000L, published.last().single().outputBytes)

        call.exit.complete(0)
        runCurrent()
        assertEquals(JobStatus.DONE, registry.jobs.value.single().status)
        assertEquals(1_000L, registry.jobs.value.single().outputBytes)
    }

    @Test
    fun stateChangePublishesImmediatelyWhileOutputIsThrottled() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(
            scope = backgroundScope,
            runner = runner,
            notifier = { _, _, _ -> },
            now = { testScheduler.currentTime },
        )
        registry.start("session-1", "build", "build").id!!
        runCurrent()
        val call = runner.calls.single()
        call.output("hello")
        assertEquals(JobStatus.RUNNING, registry.jobs.value.single().status)

        call.exit.complete(0)
        runCurrent()

        val settled = registry.jobs.value.single()
        assertEquals(JobStatus.DONE, settled.status)
        assertEquals(5L, settled.outputBytes)
    }

    @Test
    fun throttledPublishCarriesLatestOutput() = runTest {
        val runner = FakeRunner()
        val registry = JobRegistry(
            scope = backgroundScope,
            runner = runner,
            notifier = { _, _, _ -> },
            now = { testScheduler.currentTime },
        )
        val published = mutableListOf<List<JobSnapshot>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.jobs.collect { snapshot -> published += snapshot }
        }
        runCurrent()

        registry.start("session-1", "emit", "emit").id!!
        runCurrent()
        val call = runner.calls.single()
        call.output("ab")
        advanceTimeBy(10)
        call.output("cdef")
        advanceTimeBy(99)

        val latest = published.last().single()
        assertEquals(JobStatus.RUNNING, latest.status)
        assertEquals(6L, latest.outputBytes)
    }

    private class FakeRunner : BackgroundCommandRunner {
        val calls = mutableListOf<Call>()

        class Call {
            var emit: (String) -> Unit = {}
            val exit = CompletableDeferred<Int>()

            fun output(text: String) = emit(text)
        }

        override suspend fun run(command: String, onOutput: (String) -> Unit): Int {
            val call = Call()
            call.emit = onOutput
            calls += call
            return call.exit.await()
        }
    }
}
