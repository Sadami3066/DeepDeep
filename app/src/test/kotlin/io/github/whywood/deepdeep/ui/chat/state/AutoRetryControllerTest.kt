package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRetryControllerTest {
    private val strings = ChatUiStrings.fallback()
    private var activeState = ConversationState(
        messages = listOf(
            AssistantMessage(
                id = 0,
                role = AssistantRole.ASSISTANT,
                text = "",
                status = MessageStatus.FAILED,
                startedAt = 1L,
                runId = "run-1",
            ),
        ),
    )
    private val activeWrites = mutableListOf<ConversationState>()
    private val begins = mutableListOf<String>()
    private val failures = mutableListOf<Pair<String, String?>>()
    private val activeErrors = mutableListOf<Pair<ConversationState, String?>>()

    private fun target(): ConversationWriteTarget = ConversationWriteTarget(
        activeReader = { activeState },
        activeWriter = {
            activeState = it
            activeWrites += it
        },
    ).apply { setActive("session-1") }

    private fun controller(
        scope: CoroutineScope,
        enabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
        writeTarget: ConversationWriteTarget = target(),
    ): AutoRetryController = AutoRetryController(
        scope = scope,
        writeTarget = writeTarget,
        runtime = FakeAgentRuntime(),
        enabledFlow = enabled,
        canRetry = { message -> strings.canAutoRetry(strings.runErrorKind(message)) },
        awaitSession = {},
        onBegin = { begins += it },
        onActiveError = { state, error -> activeErrors += state to error },
        onFailure = { sessionId, message -> failures += sessionId to message },
    )

    @Test
    fun transientErrorSchedulesRetry() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()

        assertTrue(controller.onRunFailed("session-1", "run-1", "Network unreachable"))
        assertEquals(1, controller.attempts("session-1"))
        assertTrue(controller.isRetrying("session-1"))

        advanceTimeBy(2_001L)
        advanceUntilIdle()

        assertEquals(listOf("session-1"), begins)
        assertEquals(1, controller.attempts("session-1"))
        assertFalse(controller.isRetrying("session-1"))
        assertTrue(activeWrites.isNotEmpty())
    }

    @Test
    fun nonTransientErrorsDoNotRetry() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()

        assertFalse(controller.onRunFailed("session-1", "run-1", "401 Unauthorized"))
        assertFalse(controller.onRunFailed("session-1", "run-1", "request timed out"))
        assertFalse(controller.onRunFailed("session-1", "run-1", "insufficient balance"))
        assertEquals(0, controller.attempts("session-1"))

        advanceTimeBy(5_000L)
        advanceUntilIdle()
        assertTrue(begins.isEmpty())
    }

    @Test
    fun stopCancelsPendingRetry() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()
        assertTrue(controller.onRunFailed("session-1", "run-1", "Network unreachable"))

        controller.onStop("session-1")
        assertEquals(0, controller.attempts("session-1"))

        advanceTimeBy(5_000L)
        advanceUntilIdle()
        assertTrue(begins.isEmpty())
    }

    @Test
    fun switchingSessionKeepsRetryCountdown() = runTest {
        val writeTarget = target()
        writeTarget.store("session-2", ConversationState())
        val controller = controller(backgroundScope, writeTarget = writeTarget)
        runCurrent()
        assertTrue(controller.onRunFailed("session-1", "run-1", "Network unreachable"))
        assertTrue(controller.onRunFailed("session-2", "run-2", "Network unreachable"))

        advanceTimeBy(2_001L)
        advanceUntilIdle()

        assertEquals(setOf("session-1", "session-2"), begins.toSet())
    }

    @Test
    fun retiredRunIdIsSuppressedUntilRetryStarts() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()
        controller.onRunFailed("session-1", "run-1", "Network unreachable")

        assertTrue(controller.onActivityChanged("run-1"))
        assertFalse(controller.onActivityChanged("run-2"))

        advanceTimeBy(2_001L)
        advanceUntilIdle()

        assertFalse(controller.onActivityChanged("run-1"))
    }

    @Test
    fun successResetsAttempts() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()
        controller.onRunFailed("session-1", "run-1", "Network unreachable")
        assertEquals(1, controller.attempts("session-1"))

        controller.onRunSucceeded("session-1")
        assertEquals(0, controller.attempts("session-1"))

        advanceTimeBy(5_000L)
        advanceUntilIdle()
        assertTrue(begins.isEmpty())
    }

    @Test
    fun finishRunErrorWritesErrorForInactiveSession() = runTest {
        val controller = controller(backgroundScope)
        runCurrent()
        val writeTarget = ConversationWriteTarget(
            activeReader = { ConversationState() },
            activeWriter = {},
        )
        writeTarget.setActive("active")
        writeTarget.store("session-2", ConversationState(title = "cached"))
        val writer = AutoRetryController(
            scope = backgroundScope,
            writeTarget = writeTarget,
            runtime = FakeAgentRuntime(),
            enabledFlow = MutableStateFlow(false),
            canRetry = { false },
            awaitSession = {},
        )

        writer.finishRunError("session-2", "Network unreachable", strings)

        assertEquals("Network unreachable", writeTarget.readCached("session-2")?.messages?.firstOrNull()?.error ?: "Network unreachable")
        assertEquals(0, controller.attempts("session-1"))
    }
}
