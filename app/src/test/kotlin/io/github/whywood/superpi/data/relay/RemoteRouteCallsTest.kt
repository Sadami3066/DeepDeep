package io.github.whywood.superpi.data.relay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteRouteCallsTest {
    private val routed = JsonObject(mapOf("sessionId" to JsonPrimitive("session-1")))

    @Test
    fun settlesMatchingCallWithResult() = runTest {
        val tracker = RemoteCallTracker()
        val pending = tracker.open("route-1")

        assertTrue(tracker.settle("route-1", RemoteCallOutcome.Ok(routed)))

        assertEquals(RemoteCallOutcome.Ok(routed), pending.await())
        assertEquals(0, tracker.pendingCount())
    }

    @Test
    fun settlesMatchingCallWithError() = runTest {
        val tracker = RemoteCallTracker()
        val pending = tracker.open("route-1")
        val error = RemoteCallError("device_offline", "device is offline")

        assertTrue(tracker.settle("route-1", RemoteCallOutcome.Failed(error)))

        assertEquals(RemoteCallOutcome.Failed(error), pending.await())
        assertEquals(0, tracker.pendingCount())
    }

    @Test
    fun ignoresFramesForUnknownRoute() {
        val tracker = RemoteCallTracker()

        assertFalse(tracker.settle("route-unknown", RemoteCallOutcome.Ok(routed)))
        assertEquals(0, tracker.pendingCount())
    }

    @Test
    fun ignoresSecondSettleForSameRoute() = runTest {
        val tracker = RemoteCallTracker()
        val pending = tracker.open("route-1")

        assertTrue(tracker.settle("route-1", RemoteCallOutcome.Ok(routed)))
        assertFalse(tracker.settle("route-1", RemoteCallOutcome.Ok(JsonObject(emptyMap()))))

        assertEquals(RemoteCallOutcome.Ok(routed), pending.await())
    }

    @Test
    fun keepsIndependentCallsCorrelated() = runTest {
        val tracker = RemoteCallTracker()
        val first = tracker.open("route-1")
        val second = tracker.open("route-2")
        val firstResult = JsonObject(mapOf("sessionId" to JsonPrimitive("first")))
        val secondResult = JsonObject(mapOf("sessionId" to JsonPrimitive("second")))

        tracker.settle("route-2", RemoteCallOutcome.Ok(secondResult))
        tracker.settle("route-1", RemoteCallOutcome.Ok(firstResult))

        assertEquals(RemoteCallOutcome.Ok(firstResult), first.await())
        assertEquals(RemoteCallOutcome.Ok(secondResult), second.await())
    }

    @Test
    fun dropsAllPendingCallsWhenConnectionIsLost() = runTest {
        val tracker = RemoteCallTracker()
        val first = tracker.open("route-1")
        val second = tracker.open("route-2")
        val error = RemoteCallError(RemoteCallCodes.DISCONNECTED, "relay disconnected")

        tracker.failAll(error)

        assertEquals(RemoteCallOutcome.Failed(error), first.await())
        assertEquals(RemoteCallOutcome.Failed(error), second.await())
        assertEquals(0, tracker.pendingCount())
        assertFalse(tracker.settle("route-1", RemoteCallOutcome.Ok(routed)))
    }

    @Test
    fun timedOutCallIsRemovedBeforeLateAnswer() = runTest {
        val tracker = RemoteCallTracker()
        tracker.open("route-1")

        val error = RemoteCallError(RemoteCallCodes.TIMEOUT, "the device did not answer")
        assertTrue(tracker.settle("route-1", RemoteCallOutcome.Failed(error)))
        assertFalse(tracker.settle("route-1", RemoteCallOutcome.Ok(routed)))
        assertEquals(0, tracker.pendingCount())
    }

    @Test
    fun callErrorCarriesCodeAndMessage() {
        val error = RemoteCallError("timeout", "no answer")

        assertEquals("timeout", error.code)
        assertEquals("no answer", error.message)
        assertNull(RemoteCallOutcome.Ok(null).result)
    }
}
