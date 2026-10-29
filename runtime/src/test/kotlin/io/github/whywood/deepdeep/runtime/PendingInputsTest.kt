package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingInputsTest {
    @Test
    fun enqueueKeepsKindAndOrder() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.STEER, "a")
        pending.enqueue(InputQueueKind.FOLLOW_UP, "b")
        pending.enqueue(InputQueueKind.STEER, "c")

        val snapshot = pending.snapshot()
        assertEquals(listOf("a", "c"), snapshot.steering)
        assertEquals(listOf("b"), snapshot.followUp)
        assertFalse(pending.isEmpty())
    }

    @Test
    fun admitPopsSteeringHeadOnlyOnExactMatch() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.STEER, "第一")
        pending.enqueue(InputQueueKind.STEER, "第二")

        assertNull(pending.admit("第二"))
        assertEquals(listOf("第一", "第二"), pending.snapshot().steering)
        assertEquals(InputQueueKind.STEER, pending.admit("第一"))
        assertEquals(listOf("第二"), pending.snapshot().steering)
        assertEquals(InputQueueKind.STEER, pending.admit("第二"))
        assertTrue(pending.isEmpty())
    }

    @Test
    fun steeringQueueBlocksFollowUpAdmission() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.STEER, "提醒正文")
        pending.enqueue(InputQueueKind.FOLLOW_UP, "追问")

        assertNull(pending.admit("追问"))
        assertEquals(listOf("追问"), pending.snapshot().followUp)
        assertEquals(InputQueueKind.STEER, pending.admit("提醒正文"))
        assertEquals(InputQueueKind.FOLLOW_UP, pending.admit("追问"))
    }

    @Test
    fun duplicateTextsAreAdmittedOneByOne() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.STEER, "重复")
        pending.enqueue(InputQueueKind.STEER, "重复")

        assertEquals(InputQueueKind.STEER, pending.admit("重复"))
        assertEquals(listOf("重复"), pending.snapshot().steering)
        assertEquals(InputQueueKind.STEER, pending.admit("重复"))
        assertTrue(pending.isEmpty())
        assertNull(pending.admit("重复"))
    }

    @Test
    fun drainReturnsSteeringBeforeFollowUpAndClears() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.FOLLOW_UP, "f1")
        pending.enqueue(InputQueueKind.STEER, "s1")
        pending.enqueue(InputQueueKind.STEER, "s2")
        pending.enqueue(InputQueueKind.FOLLOW_UP, "f2")

        assertEquals(
            listOf(
                QueuedInput(InputQueueKind.STEER, "s1"),
                QueuedInput(InputQueueKind.STEER, "s2"),
                QueuedInput(InputQueueKind.FOLLOW_UP, "f1"),
                QueuedInput(InputQueueKind.FOLLOW_UP, "f2"),
            ),
            pending.drain(),
        )
        assertTrue(pending.isEmpty())
    }

    @Test
    fun discardRemovesSingleMatchingEntry() {
        val pending = PendingInputs()
        pending.enqueue(InputQueueKind.FOLLOW_UP, "x")
        pending.enqueue(InputQueueKind.FOLLOW_UP, "x")
        pending.enqueue(InputQueueKind.FOLLOW_UP, "y")

        assertTrue(pending.discard(InputQueueKind.FOLLOW_UP, "x"))
        assertEquals(listOf("x", "y"), pending.snapshot().followUp)
        assertFalse(pending.discard(InputQueueKind.STEER, "x"))
    }
}
