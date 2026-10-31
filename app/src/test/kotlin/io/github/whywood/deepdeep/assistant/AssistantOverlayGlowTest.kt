package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.ui.chat.ConversationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantOverlayGlowTest {
    private fun assistant(
        id: Long = 1L,
        text: String = "",
        streaming: Boolean = false,
        status: MessageStatus = MessageStatus.DONE,
        runId: String = "",
        steps: List<AssistantStep> = emptyList(),
    ) = AssistantMessage(
        id = id,
        role = AssistantRole.ASSISTANT,
        text = text,
        streaming = streaming,
        steps = steps,
        status = status,
        runId = runId,
    )

    private fun state(
        loaded: Boolean = true,
        sessionId: String? = "session-1",
        messages: List<AssistantMessage> = emptyList(),
        sending: Boolean = false,
    ) = AssistantOverlayState(
        conversation = ConversationState(messages = messages, loaded = loaded, sending = sending),
        sessionId = sessionId,
    )

    private fun question(sessionId: String = "session-1") = PendingQuestion(
        requestId = "question-1",
        sessionId = sessionId,
        toolCallId = "tool-1",
        questions = emptyList(),
    )

    private fun idleSignal(
        sessionId: String? = "session-1",
        workspaceId: String = "",
    ) = OverlayRunSignal(loaded = true, sessionId = sessionId, workspaceId = workspaceId)

    private fun thinkingSignal(
        messageId: Long?,
        sessionId: String? = "session-1",
        workspaceId: String = "workspace-1",
    ) = OverlayRunSignal(
        loaded = true,
        sessionId = sessionId,
        workspaceId = workspaceId,
        active = true,
        phase = OverlayRunPhase.Thinking,
        messageId = messageId,
    )

    private fun waitingSignal(
        sessionId: String? = "session-1",
        workspaceId: String = "workspace-1",
    ) = OverlayRunSignal(
        loaded = true,
        sessionId = sessionId,
        workspaceId = workspaceId,
        active = true,
        phase = OverlayRunPhase.Waiting,
    )

    private fun streamingSignal(
        messageId: Long?,
        runId: String = "",
        sessionId: String? = "session-1",
        workspaceId: String = "workspace-1",
    ) = OverlayRunSignal(
        loaded = true,
        sessionId = sessionId,
        workspaceId = workspaceId,
        active = true,
        phase = OverlayRunPhase.Streaming,
        messageId = messageId,
        runId = runId,
    )

    private fun activeSignal(
        messageId: Long?,
        runId: String = "",
        sessionId: String? = "session-1",
    ) = OverlayRunSignal(
        loaded = true,
        sessionId = sessionId,
        active = true,
        phase = OverlayRunPhase.Thinking,
        messageId = messageId,
        runId = runId,
    )

    @Test
    fun unloadedConversationIsIdle() {
        val signal = overlayRunSignal(state(loaded = false, messages = listOf(assistant())), emptyList())
        assertFalse(signal.loaded)
        assertFalse(signal.active)
        assertEquals(OverlayRunPhase.Idle, signal.phase)
    }

    @Test
    fun emptyConversationIsIdle() {
        val signal = overlayRunSignal(state(), emptyList())
        assertTrue(signal.loaded)
        assertFalse(signal.active)
        assertEquals(OverlayRunPhase.Idle, signal.phase)
        assertNull(signal.messageId)
        assertEquals("", signal.runId)
    }

    @Test
    fun waitingQuestionTakesPriorityOverStreaming() {
        val message = assistant(
            id = 7L,
            streaming = true,
            status = MessageStatus.RUNNING,
            runId = "run-7",
            steps = listOf(AssistantStep.Text(id = 1L, text = "partial")),
        )
        val signal = overlayRunSignal(
            state(messages = listOf(message), sending = true),
            listOf(question()),
        )
        assertEquals(OverlayRunPhase.Waiting, signal.phase)
        assertTrue(signal.active)
        assertEquals(7L, signal.messageId)
        assertEquals("run-7", signal.runId)
    }

    @Test
    fun otherSessionQuestionDoesNotWait() {
        val message = assistant(
            id = 7L,
            streaming = true,
            status = MessageStatus.RUNNING,
            steps = listOf(AssistantStep.Text(id = 1L, text = "partial")),
        )
        val signal = overlayRunSignal(state(messages = listOf(message)), listOf(question("session-2")))
        assertEquals(OverlayRunPhase.Streaming, signal.phase)
    }

    @Test
    fun activeTextStepMeansStreaming() {
        val message = assistant(
            id = 3L,
            streaming = true,
            status = MessageStatus.RUNNING,
            runId = "run-3",
            steps = listOf(
                AssistantStep.Reasoning(id = 1L, text = "thinking"),
                AssistantStep.Text(id = 2L, text = "hello"),
            ),
        )
        val signal = overlayRunSignal(state(messages = listOf(message), sending = true), emptyList())
        assertTrue(signal.active)
        assertEquals(OverlayRunPhase.Streaming, signal.phase)
        assertEquals(3L, signal.messageId)
        assertEquals("run-3", signal.runId)
    }

    @Test
    fun blankTextStepStillMeansThinking() {
        val message = assistant(
            id = 3L,
            streaming = true,
            status = MessageStatus.RUNNING,
            steps = listOf(AssistantStep.Text(id = 1L, text = "   ")),
        )
        val signal = overlayRunSignal(state(messages = listOf(message), sending = true), emptyList())
        assertEquals(OverlayRunPhase.Thinking, signal.phase)
    }

    @Test
    fun activeWithoutTextStepMeansThinking() {
        val message = assistant(
            id = 4L,
            streaming = true,
            status = MessageStatus.RUNNING,
            steps = listOf(AssistantStep.Reasoning(id = 1L, text = "thinking")),
        )
        val signal = overlayRunSignal(state(messages = listOf(message)), emptyList())
        assertTrue(signal.active)
        assertEquals(OverlayRunPhase.Thinking, signal.phase)
    }

    @Test
    fun sendingFlagWithoutActiveMessageMeansThinking() {
        val signal = overlayRunSignal(
            state(messages = listOf(assistant(id = 1L, status = MessageStatus.DONE)), sending = true),
            emptyList(),
        )
        assertTrue(signal.active)
        assertEquals(OverlayRunPhase.Thinking, signal.phase)
        assertNull(signal.messageId)
    }

    @Test
    fun streamingMessageMarksRunActiveEvenWhenSendingFlagFalse() {
        val message = assistant(id = 2L, streaming = true, status = MessageStatus.RUNNING)
        val signal = overlayRunSignal(state(messages = listOf(message)), emptyList())
        assertTrue(signal.active)
        assertEquals(OverlayRunPhase.Thinking, signal.phase)
    }

    @Test
    fun idlePhaseFollowsLastAssistantStatus() {
        assertEquals(
            OverlayRunPhase.Done,
            overlayRunSignal(state(messages = listOf(assistant(status = MessageStatus.DONE))), emptyList()).phase,
        )
        assertEquals(
            OverlayRunPhase.Stopped,
            overlayRunSignal(state(messages = listOf(assistant(status = MessageStatus.STOPPED))), emptyList()).phase,
        )
        assertEquals(
            OverlayRunPhase.Failed,
            overlayRunSignal(state(messages = listOf(assistant(status = MessageStatus.FAILED))), emptyList()).phase,
        )
    }

    @Test
    fun firstLoadWithActiveRunDoesNotTrigger() {
        val tracker = OverlayStartTracker()
        val message = assistant(id = 5L, streaming = true, status = MessageStatus.RUNNING, runId = "run-5")
        val signal = overlayRunSignal(state(messages = listOf(message), sending = true), emptyList())
        assertFalse(tracker.update(signal))
    }

    @Test
    fun newActiveMessageTriggersOnce() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(idleSignal()))
        assertTrue(tracker.update(activeSignal(messageId = 5L)))
        assertFalse(tracker.update(activeSignal(messageId = 5L)))
        assertFalse(tracker.update(activeSignal(messageId = 5L, runId = "run-5")))
    }

    @Test
    fun runIdFilledLaterDoesNotTrigger() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(idleSignal()))
        assertTrue(tracker.update(activeSignal(messageId = 5L)))
        assertFalse(tracker.update(activeSignal(messageId = 5L, runId = "run-5")))
    }

    @Test
    fun runIdFillThroughSignalDoesNotTrigger() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(overlayRunSignal(state(loaded = false), emptyList())))
        assertFalse(tracker.update(overlayRunSignal(state(), emptyList())))
        val placeholder = assistant(id = 5L, streaming = true, status = MessageStatus.RUNNING)
        assertTrue(
            tracker.update(
                overlayRunSignal(state(messages = listOf(placeholder), sending = true), emptyList()),
            ),
        )
        val filled = placeholder.copy(runId = "run-5")
        assertFalse(
            tracker.update(
                overlayRunSignal(state(messages = listOf(filled), sending = true), emptyList()),
            ),
        )
    }

    @Test
    fun sessionSwitchDoesNotTrigger() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(idleSignal("session-1")))
        assertTrue(tracker.update(activeSignal(messageId = 5L, sessionId = "session-1")))
        assertFalse(tracker.update(activeSignal(messageId = 5L, sessionId = "session-2")))
        assertFalse(tracker.update(activeSignal(messageId = 5L, sessionId = "session-2")))
    }

    @Test
    fun idleThenActiveTriggersAgain() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(idleSignal()))
        assertTrue(tracker.update(activeSignal(messageId = 5L)))
        assertFalse(tracker.update(idleSignal()))
        assertTrue(tracker.update(activeSignal(messageId = 6L)))
        assertFalse(tracker.update(activeSignal(messageId = 6L)))
    }

    @Test
    fun nextRunAfterCompletedTurnTriggersAgain() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(overlayRunSignal(state(), emptyList())))
        val first = assistant(id = 5L, streaming = true, status = MessageStatus.RUNNING)
        assertTrue(
            tracker.update(
                overlayRunSignal(state(messages = listOf(first), sending = true), emptyList()),
            ),
        )
        val done = first.copy(streaming = false, status = MessageStatus.DONE, text = "done")
        assertFalse(
            tracker.update(
                overlayRunSignal(state(messages = listOf(done)), emptyList()),
            ),
        )
        val second = assistant(id = 6L, streaming = true, status = MessageStatus.RUNNING)
        assertTrue(
            tracker.update(
                overlayRunSignal(state(messages = listOf(done, second), sending = true), emptyList()),
            ),
        )
    }

    @Test
    fun unloadedSignalIsIgnored() {
        val tracker = OverlayStartTracker()
        assertFalse(tracker.update(OverlayRunSignal()))
        assertFalse(tracker.update(OverlayRunSignal(loaded = true, sessionId = null)))
    }

    @Test
    fun firstLoadWithStreamingReplyDoesNotFlash() {
        val tracker = OverlayReplyTracker()
        assertFalse(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
        assertFalse(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
    }

    @Test
    fun firstReplyTextFlashesOnce() {
        val tracker = OverlayReplyTracker()
        assertFalse(tracker.update(idleSignal(workspaceId = "workspace-1")))
        assertFalse(tracker.update(thinkingSignal(messageId = 5L)))
        assertTrue(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
        assertFalse(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
        assertFalse(tracker.update(streamingSignal(messageId = 5L, runId = "run-6")))
    }

    @Test
    fun sameMessageDoesNotFlashAgain() {
        val tracker = OverlayReplyTracker()
        assertFalse(tracker.update(idleSignal(workspaceId = "workspace-1")))
        assertTrue(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
        assertFalse(tracker.update(thinkingSignal(messageId = 5L)))
        assertFalse(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
    }

    @Test
    fun sessionOrWorkspaceSwitchDoesNotFlash() {
        val tracker = OverlayReplyTracker()
        assertFalse(tracker.update(idleSignal(sessionId = "session-1", workspaceId = "workspace-1")))
        assertTrue(tracker.update(streamingSignal(messageId = 5L, sessionId = "session-1", workspaceId = "workspace-1")))
        assertFalse(tracker.update(streamingSignal(messageId = 5L, sessionId = "session-2", workspaceId = "workspace-1")))
        assertFalse(tracker.update(streamingSignal(messageId = 6L, sessionId = "session-2", workspaceId = "workspace-2")))
        assertTrue(tracker.update(streamingSignal(messageId = 7L, sessionId = "session-2", workspaceId = "workspace-2")))
    }

    @Test
    fun thinkingOrWaitingDoesNotFlash() {
        val tracker = OverlayReplyTracker()
        assertFalse(tracker.update(idleSignal(workspaceId = "workspace-1")))
        assertFalse(tracker.update(thinkingSignal(messageId = 5L)))
        assertFalse(tracker.update(waitingSignal()))
        assertTrue(tracker.update(streamingSignal(messageId = 5L, runId = "run-5")))
    }

    @Test
    fun replySweepTravelsFromBottomToTop() {
        assertEquals(1.06f, replyHotspotCenterFraction(0f), 0.001f)
        assertEquals(-0.06f, replyHotspotCenterFraction(1f), 0.001f)
        assertEquals(1f, replyBottomHotspotWeight(0f), 0.001f)
        assertEquals(0f, replyBottomHotspotWeight(1f), 0.001f)
        assertEquals(0f, replyTopHotspotWeight(0f), 0.001f)
        assertEquals(1f, replyTopHotspotWeight(1f), 0.001f)
        assertTrue(replyTopHotspotWeight(0.7f) > replyTopHotspotWeight(0.3f))
        assertTrue(replyBottomHotspotWeight(0.3f) > replyBottomHotspotWeight(0.7f))
    }
}
