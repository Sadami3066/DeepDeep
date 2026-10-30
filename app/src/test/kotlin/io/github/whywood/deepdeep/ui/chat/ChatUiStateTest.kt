package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {
    private val usage = SessionUsage(1L, 2L, 3L, 4L, 5L, 6L, 0.5)
    private val messages = listOf(AssistantMessage(0, AssistantRole.ASSISTANT, "hello"))

    @Test
    fun conversationFieldsAreProjectedOnState() {
        val state = ChatUiState(
            conversation = ConversationState(
                messages = messages,
                title = "title",
                sending = true,
                activity = RuntimeActivity.Thinking,
                activityStartedAt = 7L,
                loaded = true,
                usage = usage,
                sessionModel = SessionModelSnapshot("p", "m", ThinkingLevel.HIGH),
            ),
        )

        assertEquals(messages, state.messages)
        assertEquals("title", state.conversationTitle)
        assertTrue(state.sending)
        assertEquals(RuntimeActivity.Thinking, state.activity)
        assertEquals(7L, state.activityStartedAt)
        assertTrue(state.historyLoaded)
        assertEquals(usage, state.usage)
    }

    @Test
    fun conversationUpdateKeepsChromeFields() {
        val state = ChatUiState(
            conversation = ConversationState(messages = messages),
            input = "draft",
            quote = QuotedMessage(1L, "quoted"),
            error = "error",
            providerId = "p",
            modelId = "m",
            conversationId = "session-1",
        )

        val updated = state.updateConversation { it.copy(title = "renamed") }

        assertEquals("renamed", updated.conversationTitle)
        assertEquals("draft", updated.input)
        assertEquals("quoted", updated.quote?.text)
        assertEquals("error", updated.error)
        assertEquals("p", updated.providerId)
        assertEquals("m", updated.modelId)
        assertEquals("session-1", updated.conversationId)
    }

    @Test
    fun chromeChangeDoesNotTouchConversation() {
        val state = ChatUiState(
            conversation = ConversationState(messages = messages, title = "kept"),
            input = "draft",
        )

        val updated = state.copy(input = "changed", error = "boom")

        assertEquals(state.conversation, updated.conversation)
        assertEquals("kept", updated.conversationTitle)
        assertEquals(messages, updated.messages)
    }

    @Test
    fun composerStateIgnoresMessageBatch() {
        val first = ChatUiState(
            conversation = ConversationState(messages = listOf(AssistantMessage(0, AssistantRole.ASSISTANT, "a"))),
            input = "draft",
            providerId = "p",
            modelId = "m",
        )
        val second = first.updateConversation {
            it.copy(messages = it.messages + AssistantMessage(1, AssistantRole.ASSISTANT, "b"))
        }

        assertEquals(composerOf(first), composerOf(second))
        assertTrue(first.conversation != second.conversation)
    }

    @Test
    fun chromeOnlyChangeLeavesConversationIdentity() {
        val state = ChatUiState(
            conversation = ConversationState(messages = messages),
            compacting = true,
        )

        val updated = state.copy(reloading = true, compacting = false)

        assertEquals(state.conversation, updated.conversation)
        assertTrue(updated.reloading)
        assertTrue(!updated.compacting)
    }

    private fun composerOf(state: ChatUiState): ChatComposerState = ChatComposerState(
        value = state.input,
        ready = state.ready,
        sending = state.sending,
        configured = state.configured,
        modelLabel = state.modelLabel,
        modelId = state.modelId,
        models = state.models,
        providerId = state.providerId,
        providers = emptyList(),
        thinkingLevel = state.thinkingLevel,
        thinkingLevels = emptyList(),
        quote = state.quote,
        attachments = state.attachments,
        attachmentRoot = java.io.File("/home"),
        queuedInputs = state.queuedInputs,
        importingAttachment = state.importingAttachment,
        modelsOpen = false,
    )
}
