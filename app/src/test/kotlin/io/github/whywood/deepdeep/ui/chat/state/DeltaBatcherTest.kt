package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.reduce
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaBatcherTest {
    private var activeState = ConversationState(
        messages = listOf(
            AssistantMessage(
                id = 0,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 1L,
                runId = "run-1",
            ),
        ),
    )
    private var reduceCalls = 0

    private fun target(): ConversationWriteTarget = ConversationWriteTarget(
        activeReader = { activeState },
        activeWriter = { activeState = it },
    ).apply { setActive("session-1") }

    private fun batcher(
        scope: kotlinx.coroutines.CoroutineScope,
        delayMillis: Long = 60_000L,
    ): DeltaBatcher = DeltaBatcher(
        scope = scope,
        writeTarget = target(),
        activeSessionId = { "session-1" },
        reduceEvent = { state, event ->
            reduceCalls += 1
            reduce(state, event, ChatUiStrings.fallback())
        },
        delayMillis = delayMillis,
    )

    private fun ConversationState.text(): String =
        messages.first().steps.filterIsInstance<AssistantStep.Text>().joinToString("") { it.text }

    private fun ConversationState.reasoning(): String =
        messages.first().steps.filterIsInstance<AssistantStep.Reasoning>().joinToString("") { it.text }

    @Test
    fun mergesConsecutiveTextDeltasWithoutLosingText() = runTest {
        val batcher = batcher(backgroundScope)
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "Hel"))
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "lo"))
        assertTrue(batcher.hasPending)

        batcher.flush()

        assertEquals("Hello", activeState.text())
        assertEquals(1, reduceCalls)
        assertFalse(batcher.hasPending)
    }

    @Test
    fun textAndReasoningMergePerKindKeptInOrder() = runTest {
        val batcher = batcher(backgroundScope)
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "a"))
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "b"))
        batcher.enqueue("run-1", RuntimeEvent.ReasoningDelta("run-1", "r1"))
        batcher.enqueue("run-1", RuntimeEvent.ReasoningDelta("run-1", "r2"))

        batcher.flush()

        assertEquals("ab", activeState.text())
        assertEquals("r1r2", activeState.reasoning())
        assertEquals(2, reduceCalls)
    }

    @Test
    fun structuralEventIsNotAcceptedAndPendingBatchFlushesFirst() = runTest {
        val batcher = batcher(backgroundScope)
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "streamed"))

        assertFalse(
            batcher.accept(
                "session-1",
                RuntimeEvent.RunFinished("run-1", "", RunStatus.DONE, finishedAt = 2L),
            ),
        )
        assertTrue(batcher.hasPending)

        batcher.flush()

        assertEquals("streamed", activeState.text())
        assertEquals(1, reduceCalls)
    }

    @Test
    fun clearDropsPendingDeltasWithoutLeaking() = runTest {
        val batcher = batcher(backgroundScope)
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "lost"))
        batcher.clear("session-1")

        assertFalse(batcher.hasPending)
        batcher.flush()
        assertEquals("", activeState.text())
        assertEquals(0, reduceCalls)
    }

    @Test
    fun flushWithEmptyBatchDoesNothing() = runTest {
        val batcher = batcher(backgroundScope)

        batcher.flush()

        assertEquals(0, reduceCalls)
        assertFalse(batcher.hasPending)
        assertEquals("", activeState.text())
    }

    @Test
    fun timerFlushesBatchAfterDelay() = runTest {
        val batcher = batcher(backgroundScope, delayMillis = 16L)
        batcher.enqueue("run-1", RuntimeEvent.TextDelta("run-1", "auto"))

        advanceTimeBy(20L)
        runCurrent()

        assertEquals("auto", activeState.text())
        assertEquals(1, reduceCalls)
        assertFalse(batcher.hasPending)
    }

    @Test
    fun deltasFromOtherSessionsAreNotBatched() = runTest {
        val batcher = batcher(backgroundScope)

        assertFalse(batcher.accept("session-2", RuntimeEvent.TextDelta("run-2", "other")))
        assertFalse(batcher.hasPending)
    }
}
