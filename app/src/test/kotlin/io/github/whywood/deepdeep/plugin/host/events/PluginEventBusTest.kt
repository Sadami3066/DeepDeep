package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginEventBusTest {
    private val reporter = RecordingReporter()

    private val event = PluginEvent.RunStarted("session-1", "run-1")

    private fun bus(source: PluginHookSource, timeoutMs: Long = 1_000): PluginEventBus =
        PluginEventBus(source, reporter, timeoutMs)

    @Test
    fun dispatchesToAllHooksInOrder() = runTest {
        val first = EventRecordingHooks()
        val second = EventRecordingHooks()
        val source = TestHookSource(listOf(hookEntry("first", first), hookEntry("second", second)))
        val failures = bus(source).emit(event)
        assertTrue(failures.isEmpty())
        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
        assertEquals(1, source.reads)
    }

    @Test
    fun routesEveryEventKindToMatchingHookMethod() = runTest {
        val hooks = EventRecordingHooks()
        val eventBus = bus(TestHookSource(listOf(hookEntry("probe", hooks))))
        val events = allPluginEvents()
        for (item in events) eventBus.emit(item)
        assertEquals(events, hooks.events)
        assertEquals(pluginEventMethodNames, hooks.calls)
    }

    @Test
    fun snapshotIsTakenBeforeDispatch() = runTest {
        val source = TestHookSource()
        val first = object : PluginHooks {
            override suspend fun sessionStarted(event: PluginEvent.SessionStarted) {
                source.list = emptyList()
            }
        }
        val second = EventRecordingHooks()
        source.list = listOf(hookEntry("first", first), hookEntry("second", second))
        val failures = bus(source).emit(PluginEvent.SessionStarted("session-1"))
        assertTrue(failures.isEmpty())
        assertEquals(1, second.events.size)
        assertEquals(1, source.reads)
    }

    @Test
    fun failingHookIsIsolatedAndReported() = runTest {
        val failing = object : PluginHooks {
            override suspend fun sessionStarted(event: PluginEvent.SessionStarted) {
                throw IllegalStateException("boom")
            }
        }
        val healthy = EventRecordingHooks()
        val source = TestHookSource(listOf(hookEntry("failing", failing), hookEntry("healthy", healthy)))
        val failures = bus(source).emit(PluginEvent.SessionStarted("session-1"))
        assertEquals(1, failures.size)
        assertEquals("failing", failures.single().pluginId)
        assertEquals(PluginHookStage.OBSERVE, failures.single().stage)
        assertEquals("boom", failures.single().message)
        assertEquals(failures, reporter.failures)
        assertEquals(1, healthy.events.size)
    }

    @Test
    fun slowHookTimesOutAndIsIsolated() = runTest {
        val slow = object : PluginHooks {
            override suspend fun sessionStarted(event: PluginEvent.SessionStarted) {
                delay(60_000)
            }
        }
        val healthy = EventRecordingHooks()
        val source = TestHookSource(listOf(hookEntry("slow", slow), hookEntry("healthy", healthy)))
        val failures = bus(source, timeoutMs = 100).emit(PluginEvent.SessionStarted("session-1"))
        assertEquals(1, failures.size)
        assertEquals("slow", failures.single().pluginId)
        assertTrue(failures.single().message.startsWith("SessionStarted timed out"))
        assertEquals(1, healthy.events.size)
    }

    @Test
    fun cancellationIsNotReportedAsFailure() = runTest {
        val hooks = object : PluginHooks {
            override suspend fun sessionStarted(event: PluginEvent.SessionStarted) {
                throw CancellationException("cancelled")
            }
        }
        val source = TestHookSource(listOf(hookEntry("cancelled", hooks)))
        val thrown = runCatching { bus(source).emit(PluginEvent.SessionStarted("session-1")) }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun emptySourceProducesNoFailures() = runTest {
        val failures = bus(TestHookSource()).emit(event)
        assertTrue(failures.isEmpty())
        assertTrue(reporter.failures.isEmpty())
    }
}
