package io.github.whywood.deepdeep.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCommandPlaneTest {
    @Test
    fun onlyRunOwnerClearsBusy() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!

        assertFalse(plane.finish("session", "stale-run").released)
        assertTrue(plane.snapshot("session")!!.busy)
        assertEquals(start.runId, plane.snapshot("session")!!.runId)
        assertNull(plane.begin("session", TurnSource.USER))

        assertTrue(plane.finish("session", start.runId).released)
        assertFalse(plane.snapshot("session")!!.busy)
        assertNull(plane.snapshot("session")!!.runId)
        assertNotNull(plane.begin("session", TurnSource.USER))
    }

    @Test
    fun concurrentBeginsStartSingleRun() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val threads = 8
        val gate = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val started = AtomicInteger(0)
        val runIds = mutableSetOf<String>()
        repeat(threads) {
            thread {
                gate.await()
                val start = plane.begin("session", TurnSource.USER)
                if (start != null) {
                    started.incrementAndGet()
                    synchronized(runIds) { runIds.add(start.runId) }
                }
                done.countDown()
            }
        }
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, started.get())
        val snapshot = plane.snapshot("session")!!
        assertTrue(snapshot.busy)
        assertEquals(runIds.single(), snapshot.runId)
    }

    @Test
    fun sendDuringRunDoesNotOverwriteRunId() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val first = plane.begin("session", TurnSource.USER)!!

        assertNull(plane.begin("session", TurnSource.USER))
        assertNull(plane.begin("session", TurnSource.RETRY))
        assertEquals(first.runId, plane.snapshot("session")!!.runId)
        assertEquals(first.epoch, plane.snapshot("session")!!.epoch)
    }

    @Test
    fun reminderOnBusySessionIsInjected() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!

        val injected = plane.deliver("session", TurnSource.REMINDER) { false }
        assertTrue(injected is SessionCommandPlane.Delivery.Injected)
        assertEquals(start.runId, (injected as SessionCommandPlane.Delivery.Injected).runId)
        assertEquals(start.runId, plane.snapshot("session")!!.runId)
        assertEquals(0L, plane.snapshot("session")!!.epoch)

        assertTrue(plane.finish("session", start.runId).released)
        val started = plane.deliver("session", TurnSource.REMINDER) { false }
        assertTrue(started is SessionCommandPlane.Delivery.Started)
        assertNotEquals(start.runId, (started as SessionCommandPlane.Delivery.Started).start.runId)
    }

    @Test
    fun streamingSessionIsInjectedWithoutRun() {
        val plane = SessionCommandPlane()
        plane.attach("session")

        val injected = plane.deliver("session", TurnSource.REMINDER) { true }
        assertTrue(injected is SessionCommandPlane.Delivery.Injected)
        assertEquals("", (injected as SessionCommandPlane.Delivery.Injected).runId)
        assertFalse(plane.snapshot("session")!!.busy)

        val started = plane.deliver("session", TurnSource.REMINDER) { false }
        assertTrue(started is SessionCommandPlane.Delivery.Started)
        assertTrue(plane.snapshot("session")!!.busy)
    }

    @Test
    fun stopInvalidatesDelayedGoalContinuation() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!
        val epoch = plane.snapshot("session")!!.epoch

        val stop = plane.stop("session") {}
        assertEquals(start.runId, stop!!.runId)
        assertEquals(epoch + 1, stop.epoch)
        assertTrue(plane.snapshot("session")!!.stopRequested)

        assertNull(plane.beginIfEpoch("session", TurnSource.GOAL, epoch))

        assertTrue(plane.finish("session", start.runId).released)
        assertFalse(plane.snapshot("session")!!.busy)
        assertFalse(plane.snapshot("session")!!.stopRequested)
        assertNotNull(plane.beginIfEpoch("session", TurnSource.GOAL, stop.epoch))
    }

    @Test
    fun stopAfterRunRejectsOldEpochReminder() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!
        val epoch = plane.snapshot("session")!!.epoch
        assertTrue(plane.finish("session", start.runId).released)

        assertNull(plane.stop("session") {})
        assertEquals(epoch + 1, plane.snapshot("session")!!.epoch)
        assertNull(plane.beginIfEpoch("session", TurnSource.REMINDER, epoch))
        assertNotNull(plane.begin("session", TurnSource.REMINDER))
    }

    @Test
    fun stopEmitsSingleStoppedTerminal() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!
        val emitted = mutableListOf<SessionCommandPlane.Stop>()

        assertEquals(start.runId, plane.stop("session") { emitted.add(it) }!!.runId)
        assertNull(plane.stop("session") { emitted.add(it) })
        assertEquals(1, emitted.size)
        assertFalse(plane.claimTerminal("session", start.runId))

        assertTrue(plane.finish("session", start.runId).released)
        assertTrue(plane.claimTerminal("session", plane.begin("session", TurnSource.USER)!!.runId))
    }

    @Test
    fun terminalIsClaimedOnce() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!

        assertTrue(plane.claimTerminal("session", start.runId))
        assertFalse(plane.claimTerminal("session", start.runId))
        assertFalse(plane.claimTerminal("session", "stale-run"))
    }

    @Test
    fun reconfigureDefersWhileBusyAndAppliesAfterRelease() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!

        assertEquals(0L, plane.claimReconfigure("session"))
        assertFalse(plane.snapshot("session")!!.reconfiguring)
        assertNull(plane.begin("session", TurnSource.USER))

        val finish = plane.finish("session", start.runId)
        assertTrue(finish.released)
        assertTrue(finish.reconfigure)

        val token = plane.claimReconfigure("session")
        assertNotEquals(0L, token)
        assertTrue(plane.snapshot("session")!!.reconfiguring)
        assertNull(plane.begin("session", TurnSource.USER))
        assertEquals(0L, plane.claimReconfigure("session"))
        assertTrue(plane.ownsReconfigure("session", token))
        assertTrue(plane.releaseReconfigure("session", token))
        assertFalse(plane.snapshot("session")!!.reconfiguring)

        assertNotNull(plane.begin("session", TurnSource.USER))
    }

    @Test
    fun reconfigureClaimedOnIdleSession() {
        val plane = SessionCommandPlane()
        plane.attach("session")

        val token = plane.claimReconfigure("session")
        assertNotEquals(0L, token)
        assertEquals(0L, plane.claimReconfigure("session"))
        assertTrue(plane.ownsReconfigure("session", token))
        assertFalse(plane.releaseReconfigure("session", token + 1))
        assertTrue(plane.ownsReconfigure("session", token))
        assertTrue(plane.releaseReconfigure("session", token))
        assertFalse(plane.snapshot("session")!!.reconfiguring)
        assertFalse(plane.releaseReconfigure("session", token))
    }

    @Test
    fun detachInvalidatesStaleContinuationsAndKeepsEpoch() = runTest {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.USER)!!

        plane.detach("session")

        assertFalse(plane.finish("session", start.runId).released)
        val snapshot = plane.snapshot("session")!!
        assertFalse(snapshot.busy)
        assertNull(snapshot.runId)
        assertEquals(start.epoch + 1, snapshot.epoch)
        assertTrue(plane.busySessionIds().isEmpty())

        val reattached = plane.attach("session")
        assertNull(plane.beginIfEpoch("session", TurnSource.GOAL, start.epoch))
        val next = plane.begin("session", TurnSource.REMINDER)!!
        assertEquals(start.epoch + 1, next.epoch)
        assertEquals(reattached.epoch, next.epoch)

        plane.forget("session")
        assertNull(plane.snapshot("session"))
        assertNull(plane.begin("session", TurnSource.USER))
    }

    @Test
    fun activeRunTracksOwner() {
        val plane = SessionCommandPlane()
        plane.attach("session")
        val start = plane.begin("session", TurnSource.JOB)!!

        assertEquals(start.runId, plane.snapshot("session")!!.runId)
        assertTrue(plane.snapshot("session")!!.runStartedAt > 0)
        assertEquals(TurnSource.JOB, plane.snapshot("session")!!.turnSource)
        assertTrue(plane.busySessionIds().contains("session"))

        assertTrue(plane.finish("session", start.runId).released)
        assertNull(plane.snapshot("session")!!.runId)
        assertEquals(0, plane.snapshot("session")!!.runStartedAt)
        assertTrue(plane.busySessionIds().isEmpty())
    }
}
