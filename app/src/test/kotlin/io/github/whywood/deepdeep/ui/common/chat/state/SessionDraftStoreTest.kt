package io.github.whywood.deepdeep.ui.common.chat.state

import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.chat.QuotedMessage
import io.github.whywood.deepdeep.ui.common.chat.ScrollAnchor
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.available



class SessionDraftStoreTest {
    private var state = ChatUiState(conversationId = "session-1", conversationCwd = "/home")

    private fun store(
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(),
    ): SessionDraftStore = SessionDraftStore(
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        attachmentStore = NoopAttachmentStore,
        normalizeImage = { _, _ -> null },
        state = { state },
        strings = { ConversationStrings.fallback() },
        updateState = { transform -> state = transform(state) },
    )
    @Test
    fun rememberRestoreForgetRoundTrip() {
        val drafts = store()
        val quote = QuotedMessage(3L, "quoted")
        state = state.copy(input = "draft", quote = quote, attachments = listOf(attachment("a1")))

        drafts.remember("session-1", state)

        val restored = drafts.restore("session-1")
        assertEquals("draft", restored?.input)
        assertEquals(quote, restored?.quote)
        assertEquals(listOf("a1"), restored?.attachments?.map { it.id })

        drafts.forget("session-1")
        assertNull(drafts.restore("session-1"))
    }

    @Test
    fun scrollAnchorRoundTripIgnoresNegativeIndex() {
        val drafts = store()

        drafts.updateAnchor("session-1", 6, 24)
        drafts.updateAnchor("session-1", 7, 8, atBottom = true)
        drafts.updateAnchor("session-1", -1, 5)

        assertEquals(ScrollAnchor(7, 8, atBottom = true), drafts.anchor("session-1"))
        assertNull(drafts.anchor("session-2"))
    }

    @Test
    fun updateInputQuoteAndClearQuoteMutateState() {
        val drafts = store()

        drafts.updateInput("typed")
        assertEquals("typed", state.input)

        drafts.setQuote(9L, "line one\nline two")
        assertEquals("line one\nline two", state.quote?.text)

        drafts.clearQuote()
        assertNull(state.quote)
    }
    @Test
    fun buildPromptIncludesQuoteAndAttachments() {
        val drafts = store()
        val prompt = drafts.buildPrompt(
            quote = QuotedMessage(1L, "quoted"),
            attachments = listOf(attachment("a1")),
            body = "body",
        )

        assertTrue(prompt.contains("> quoted"))
        assertTrue(prompt.contains("- /home/attachments/a1 |"))
        assertTrue(prompt.contains("body"))
        assertTrue(prompt.endsWith("body"))
    }

    private fun attachment(id: String, kind: AttachmentKind = AttachmentKind.IMAGE): AttachmentRef =
        AttachmentRef(id = id, kind = kind, path = "attachments/$id", mime = "image/png", size = 1L, name = "$id.png")

    private object NoopAttachmentStore : AttachmentStore {
        override suspend fun import(uri: android.net.Uri, root: File): AttachmentImport = AttachmentImport.Failed

        override fun file(ref: AttachmentRef, root: File): File = File(root, ref.path)

        override suspend fun materialize(ref: AttachmentRef, root: File): File? = file(ref, root)

        override suspend fun delete(ref: AttachmentRef, root: File): Boolean = false

        override suspend fun shareIntent(ref: AttachmentRef, root: File): android.content.Intent? = null

        override suspend fun resolve(raw: String, root: File): AttachmentRef? = null

        override fun available(): Boolean = false
    }
}
