package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEventPumpTest {
    private val snapshot = HookSnapshot(version = 1L, entries = listOf(PluginHooksEntry("plugin", probeHooks())))
    private val emptySnapshot = HookSnapshot(version = 1L, entries = emptyList())

    private fun event(name: String): PluginEvent = PluginEvent.RunStarted("session", name)

    private fun delta(name: String): PluginEvent = PluginEvent.MessageDelta("session", "message", name)

    @Test
    fun dispatchesInSubmitOrderWithinSession() = runTest {
        val dispatched = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event -> dispatched += label(event) }
        pump.updateSnapshot("session", snapshot)

        pump.submit("session", event("one"))
        pump.submit("session", event("two"))
        pump.submit("session", event("three"))

        assertEquals(listOf("one", "two", "three"), dispatched)
    }

    @Test
    fun structuralEventsOvertakePendingDeltas() = runTest {
        val dispatched = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val gateEntered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event ->
            if (label(event) == "delta-1") {
                gateEntered.complete(Unit)
                gate.await()
            }
            dispatched += label(event)
        }
        pump.updateSnapshot("session", snapshot)
        pump.submit("session", delta("delta-1"))
        gateEntered.await()
        pump.submit("session", delta("delta-2"))
        pump.submit("session", event("structural-1"))
        gate.complete(Unit)

        assertEquals(listOf("delta-1", "structural-1", "delta-2"), dispatched)
    }

    @Test
    fun keepsSessionsIndependent() = runTest {
        val dispatched = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val gateEntered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { sessionId, event ->
            if (sessionId == "session-a" && label(event) == "a1") {
                gateEntered.complete(Unit)
                gate.await()
            }
            dispatched += "$sessionId:${label(event)}"
        }
        pump.updateSnapshot("session-a", snapshot)
        pump.updateSnapshot("session-b", snapshot)

        pump.submit("session-a", event("a1"))
        gateEntered.await()
        pump.submit("session-b", event("b1"))
        pump.submit("session-a", event("a2"))
        pump.submit("session-b", event("b2"))
        gate.complete(Unit)

        assertEquals(listOf("session-b:b1", "session-b:b2", "session-a:a1", "session-a:a2"), dispatched)
    }

    @Test
    fun dropsEventsWhileHooksSnapshotIsEmpty() = runTest {
        val dispatched = mutableListOf<PluginEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event -> dispatched += event }
        pump.updateSnapshot("session", emptySnapshot)

        pump.submit("session", event("dropped"))

        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun resumesDispatchAfterSnapshotGainsHooks() = runTest {
        val dispatched = mutableListOf<PluginEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event -> dispatched += event }
        pump.updateSnapshot("session", emptySnapshot)
        pump.submit("session", event("dropped"))
        pump.updateSnapshot("session", snapshot)

        pump.submit("session", event("kept"))

        assertEquals(listOf(event("kept")), dispatched)
    }

    @Test
    fun closeDrainsQueuedEventsThenStops() = runTest {
        val dispatched = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val gateEntered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event ->
            if (label(event) == "one") {
                gateEntered.complete(Unit)
                gate.await()
            }
            dispatched += label(event)
        }
        pump.updateSnapshot("session", snapshot)
        pump.submit("session", event("one"))
        gateEntered.await()
        pump.submit("session", event("two"))
        pump.close("session")
        gate.complete(Unit)
        pump.submit("session", event("after-close"))

        assertEquals(listOf("one", "two"), dispatched)
    }

    @Test
    fun closeAndDrainWaitsUntilQueuedEventsAreDispatched() = runTest {
        val dispatched = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val gateEntered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pump = SessionEventPump(scope) { _, event ->
            if (label(event) == "one") {
                gateEntered.complete(Unit)
                gate.await()
            }
            dispatched += label(event)
        }
        pump.updateSnapshot("session", snapshot)
        pump.submit("session", event("one"))
        gateEntered.await()
        pump.submit("session", event("two"))
        pump.submit("session", PluginEvent.SessionClosed("session"))
        val closing = scope.launch { pump.closeAndDrain("session") }
        gate.complete(Unit)
        closing.join()

        assertEquals(3, dispatched.size)
        assertEquals("one", dispatched[0])
        assertEquals("two", dispatched[1])
        assertTrue(dispatched[2].startsWith("SessionClosed"))
    }

    private fun label(event: PluginEvent): String = (event as? PluginEvent.RunStarted)?.runId
        ?: (event as? PluginEvent.MessageDelta)?.delta
        ?: event.toString()
}
