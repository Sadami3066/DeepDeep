package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenCorrelationTest {
    private val correlator = ResponseCorrelator()

    @Test
    fun matchingIdIsRoutedToPendingCommand() {
        correlator.expect(7)
        assertEquals(ResponseMatch.MATCHED, correlator.resolve(7))
        assertEquals(ResponseCorrelator.NO_PENDING, correlator.pendingId)
    }

    @Test
    fun unrelatedIdIsRejectedWithoutTouchingPendingCommand() {
        correlator.expect(7)
        assertEquals(ResponseMatch.UNKNOWN, correlator.resolve(8))
        assertTrue(correlator.isPending(7))
    }

    @Test
    fun lateResponseAfterTimeoutCannotCompleteTheNextCommand() {
        correlator.expect(7)
        correlator.abandon(7)
        correlator.expect(8)
        assertEquals(ResponseMatch.LATE, correlator.resolve(7))
        assertTrue(correlator.isPending(8))
        assertEquals(ResponseMatch.MATCHED, correlator.resolve(8))
    }

    @Test
    fun heartbeatPongNeverSatisfiesPendingCommand() {
        val pong = buildJsonObject {
            put("id", -3)
            put("ok", true)
            put("pong", true)
        }
        assertTrue(isPongResponse(pong))
        correlator.expect(7)
        assertEquals(ResponseMatch.UNKNOWN, correlator.resolve(pong.intField("id")!!))
        assertTrue(correlator.isPending(7))
    }

    @Test
    fun businessResponseIsNotMisreadAsPong() {
        val response = buildJsonObject {
            put("id", 7)
            put("ok", true)
        }
        assertFalse(isPongResponse(response))
    }

    @Test
    fun resetClearsPendingAndAbandonedIds() {
        correlator.expect(7)
        correlator.abandon(7)
        correlator.expect(8)
        correlator.reset()
        assertFalse(correlator.isPending(8))
        assertEquals(ResponseMatch.UNKNOWN, correlator.resolve(7))
    }

    @Test
    fun screenshotFrameCompletesArmedEpoch() = runTest {
        val gate = ScreenshotGate()
        val epoch = gate.open()
        assertTrue(gate.arm(epoch))
        var result: ByteArray? = null
        val job = launch { result = gate.await(epoch, 1_000L) }
        assertTrue(gate.onFrame(byteArrayOf(1, 2, 3)))
        job.join()
        assertArrayEquals(byteArrayOf(1, 2, 3), result)
        gate.close(epoch)
    }

    @Test
    fun screenshotFrameBeforeResponseIsDropped() {
        val gate = ScreenshotGate()
        gate.open()
        assertFalse(gate.onFrame(byteArrayOf(9)))
    }

    @Test
    fun staleEpochIsRejected() {
        val gate = ScreenshotGate()
        val first = gate.open()
        assertTrue(gate.arm(first))
        val second = gate.open()
        assertFalse(gate.onFrame(byteArrayOf(1)))
        assertFalse(gate.arm(first))
        assertTrue(gate.arm(second))
        assertTrue(gate.onFrame(byteArrayOf(2)))
    }

    @Test
    fun screenshotTimeoutDropsLateFrames() = runTest {
        val gate = ScreenshotGate()
        val epoch = gate.open()
        assertTrue(gate.arm(epoch))
        var result: ByteArray? = byteArrayOf(0)
        val job = launch { result = gate.await(epoch, 1_000L) }
        advanceTimeBy(1_001L)
        job.join()
        assertNull(result)
        gate.close(epoch)
        assertFalse(gate.onFrame(byteArrayOf(5)))
    }
}
