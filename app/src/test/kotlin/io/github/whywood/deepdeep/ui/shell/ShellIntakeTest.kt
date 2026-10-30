package io.github.whywood.deepdeep.ui.shell

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellIntakeTest {
    private fun summary(id: String) = ConversationSummary(id, id, 0L, "$id.jsonl", "/workspace")

    private fun chat(
        ready: Boolean = true,
        historyLoaded: Boolean = true,
        sending: Boolean = false,
        conversationId: String? = "s1",
        cwd: String = "/workspace",
        conversations: List<ConversationSummary> = emptyList(),
        archived: List<ConversationSummary> = emptyList(),
        archivedCount: Int = archived.size,
    ) = ShellIntakeChatState(
        ready, historyLoaded, sending, false, conversationId, cwd, conversations, archived, archivedCount,
    )

    @Test
    fun sessionIdleWithoutRequest() {
        assertEquals(SessionIntake.Idle, resolveSessionIntake(null, chat()))
        assertEquals(SessionIntake.WaitingForReady, resolveSessionIntake("s9", chat(ready = false)))
    }

    @Test
    fun sessionOpensWhenListed() {
        assertEquals(SessionIntake.Open("s9"), resolveSessionIntake("s9", chat(conversations = listOf(summary("s9")))))
        assertEquals(SessionIntake.Open("s9"), resolveSessionIntake("s9", chat(archived = listOf(summary("s9")))))
    }

    @Test
    fun sessionLookupFailsWhenMissing() {
        assertEquals(SessionIntake.LookupFailed("s9"), resolveSessionIntake("s9", chat()))
    }
    @Test
    fun secondGraceRequiredWhenArchivedPending() {
        assertTrue(needsSecondSessionGrace(3, emptyList()))
        assertFalse(needsSecondSessionGrace(3, listOf(summary("s1"))))
        assertFalse(needsSecondSessionGrace(0, emptyList()))
    }
    @Test
    fun shareIdleWithoutPayload() {
        assertEquals(ShareIntake.Idle, resolveShareIntake(hasPayload = false, chat = chat()))
        assertEquals(ShareIntake.WaitingForReady, resolveShareIntake(true, chat(ready = false)))
    }

    @Test
    fun shareWaitsForSessionUntilConversationAppears() {
        assertEquals(ShareIntake.WaitingForSession, resolveShareIntake(true, chat(conversationId = null)))
    }

    @Test
    fun shareWaitsWhileChatBusy() {
        assertEquals(ShareIntake.WaitingForChat, resolveShareIntake(true, chat(sending = true)))
        assertEquals(ShareIntake.WaitingForChat, resolveShareIntake(true, chat(historyLoaded = false)))
        assertEquals(ShareIntake.WaitingForChat, resolveShareIntake(true, chat(cwd = "")))
    }

    @Test
    fun shareReadyWhenChatIdleAndTimeoutIsClamped() {
        assertEquals(ShareIntake.Ready, resolveShareIntake(hasPayload = true, chat = chat()))
        assertEquals(0L, shareWaitRemainingMs(1_000L, 10_000L, 8_000L))
        assertEquals(3_000L, shareWaitRemainingMs(1_000L, 6_000L, 8_000L))
    }

    @Test
    fun deepLinkWaitsUntilReadyThenApplies() {
        assertEquals(DeepLinkIntake.Idle, resolveDeepLinkIntake(null, ready = true))
        assertEquals(DeepLinkIntake.WaitingForReady, resolveDeepLinkIntake(AppDestination.Skills, ready = false))
        assertEquals(
            DeepLinkIntake.Apply(AppDestination.PluginDetail("p1")),
            resolveDeepLinkIntake(AppDestination.PluginDetail("p1"), ready = true),
        )
    }
}
