package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOpenerTest {
    private var state = ChatUiState(ready = true)
    private val stored = mutableMapOf<String, ConversationState>()
    private var active: String? = null
    private val decorated = mutableListOf<String>()
    private val drafts = mutableMapOf<String, SessionDraft>()

    private fun summary(id: String, updatedAt: Long = 1L): ConversationSummary =
        ConversationSummary(id, id, updatedAt, "$id.jsonl", HOME_CWD)

    private fun message(text: String): AssistantMessage =
        AssistantMessage(0, AssistantRole.USER, text)

    private fun opener(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        runtime: AgentRuntime = FakeAgentRuntime(),
        localizeError: (String?) -> String = { it ?: "" },
    ): SessionOpener = SessionOpener(
        runtime = runtime,
        ioDispatcher = dispatcher,
        scope = scope,
        state = { state },
        update = { transform -> state = transform(state) },
        cachedState = { stored[it] },
        rememberState = { id, conversation -> stored[id] = conversation },
        isActive = { it == active },
        setActive = { active = it },
        draftFor = { drafts[it] },
        moveDraft = { from, to -> drafts.remove(from)?.let { drafts[to] = it } },
        takeCached = { stored.remove(it) },
        applySessionModel = {},
        titleFrom = { messages -> messages.firstOrNull()?.text ?: "new" },
        localizeError = localizeError,
        localizeTitle = { it },
        decorateNotices = { conversation, id ->
            decorated += id
            conversation
        },
        cacheCurrentSession = {},
        refreshSessions = {},
        userFacingError = { it },
        openFailedText = { "open failed" },
        newChatFailedText = { "new failed" },
        startupFailedText = { "startup failed" },
        reloadBusyText = { "busy" },
        sessionNotReadyText = { "not ready" },
        reloadFailedText = { "reload failed" },
    )

    @Test
    fun switchingToSameSessionWhileLoadingStillCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf("session-1" to listOf(message("hi")))
            historyDelay = 100
        }
        val opener = opener(this, dispatcher, runtime)

        opener.switchToSummary(summary("session-1"))
        runCurrent()
        opener.switchToSummary(summary("session-1"))
        advanceUntilIdle()

        assertEquals("session-1", state.conversationId)
        assertFalse(state.switchingConversation)
        assertTrue(state.historyLoaded)
        assertEquals(1, state.messages.size)
    }

    @Test
    fun cachedSessionRendersBeforeHistoryLands() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val opener = opener(this, dispatcher)
        stored["session-1"] = ConversationState(messages = listOf(message("cached")), title = "cached", loaded = true)

        opener.switchToSummary(summary("session-1"))

        assertEquals("session-1", state.conversationId)
        assertEquals("cached", state.messages.single().text)
        assertFalse(state.switchingConversation)
        assertTrue(state.historyLoaded)
    }

    @Test
    fun historyLandsWithQueuedInputs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf("session-1" to listOf(message("from history")))
            queued = listOf(QueuedInput(InputQueueKind.STEER, "queued"))
        }
        val opener = opener(this, dispatcher, runtime)

        opener.switchToSummary(summary("session-1"))
        assertTrue(state.switchingConversation)

        advanceUntilIdle()

        assertEquals("from history", state.messages.single().text)
        assertTrue(state.historyLoaded)
        assertFalse(state.switchingConversation)
        assertEquals(1, state.queuedInputs.size)
        assertTrue(stored.getValue("session-1").loaded)
        assertEquals(listOf("session-1"), decorated.distinct())
    }

    @Test
    fun staleOpenDiscardsLandingAfterSwitch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf("session-1" to listOf(message("first")), "session-2" to listOf(message("second")))
        }
        val opener = opener(this, dispatcher, runtime)

        opener.switchToSummary(summary("session-1"))
        opener.switchToSummary(summary("session-2"))
        advanceUntilIdle()

        assertEquals("session-2", state.conversationId)
        assertEquals("second", state.messages.single().text)
        assertNull(stored["session-1"])
        assertNotNull(stored["session-2"])
    }

    @Test
    fun duplicateOpenForSameSessionRunsOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply { histories = mapOf("session-1" to emptyList()) }
        val opener = opener(this, dispatcher, runtime)

        opener.openSession(summary("session-1"))
        opener.openSession(summary("session-1"))
        advanceUntilIdle()

        assertEquals(1, runtime.openCalls.size)
    }

    @Test
    fun failedHistoryLocalizesSummaryAndKeepsProviderDetails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val strings = ConversationStrings.fallback()
        val raw = "413 Request Entity Too Large"
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "看图"),
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "",
                        status = MessageStatus.FAILED,
                        rawError = raw,
                        startedAt = 2L,
                        finishedAt = 3L,
                    ),
                ),
            )
        }
        val opener = opener(this, dispatcher, runtime, localizeError = strings::runError)

        opener.switchToSummary(summary("session-1"))
        advanceUntilIdle()

        val failed = state.messages.last()
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals(strings.requestTooLarge, failed.error)
        assertEquals(raw, failed.rawError)
    }

    @Test
    fun openFailureLeavesConversationWithError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = object : AgentRuntime by FakeAgentRuntime() {
            override suspend fun openSession(filePath: String, cwd: String): OpenedSession =
                throw IllegalStateException("boom")
        }
        val opener = opener(this, dispatcher, runtime)

        opener.switchToSummary(summary("session-1"))
        advanceUntilIdle()

        assertEquals("session-1", state.conversationId)
        assertFalse(state.switchingConversation)
        assertEquals("boom", state.error)
    }

    @Test
    fun emptyCacheSessionReloadsHistory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeAgentRuntime().apply { histories = mapOf("session-1" to listOf(message("reloaded"))) }
        val opener = opener(this, dispatcher, runtime)

        opener.switchToSummary(summary("session-1"))
        assertTrue(state.switchingConversation)
        assertFalse(state.historyLoaded)

        advanceUntilIdle()

        assertEquals("reloaded", state.messages.single().text)
        assertNotNull(stored["session-1"])
        assertEquals("session-1", decorated.last())
    }

}
