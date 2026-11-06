package io.github.whywood.deepdeep.ui.common.chat.state

import android.net.Uri
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.ui.common.chat.markdownToPlainText
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.chat.QuotedMessage
import io.github.whywood.deepdeep.ui.common.chat.ScrollAnchor
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



internal data class SessionDraft(
    val input: String = "",
    val quote: QuotedMessage? = null,
    val attachments: List<AttachmentRef> = emptyList(),
    val scrollAnchor: ScrollAnchor? = null,
)

internal class SessionDraftStore(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val attachmentStore: AttachmentStore,
    private val normalizeImage: suspend (File, String) -> NormalizedImage?,
    private val state: () -> ChatUiState,
    private val strings: () -> ConversationStrings,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
) {
    private val drafts = mutableMapOf<String, SessionDraft>()
    private val importer = AttachmentImporter(attachmentStore, scope, ioDispatcher)
    fun remember(sessionId: String, current: ChatUiState) {
        drafts[sessionId] = (drafts[sessionId] ?: SessionDraft()).copy(
            input = current.input,
            quote = current.quote,
            attachments = current.attachments,
        )
    }

    fun restore(sessionId: String): SessionDraft? = drafts[sessionId]
    fun forget(sessionId: String) {
        drafts.remove(sessionId)
    }
    fun retainOnly(sessionIds: Set<String>) {
        drafts.keys.retainAll(sessionIds)
    }
    fun reopen(from: String, to: String) {
        drafts.remove(from)?.let { draft -> drafts[to] = draft }
    }
    fun updateAnchor(sessionId: String, index: Int, offset: Int, atBottom: Boolean = false) {
        if (index < 0) return
        drafts[sessionId] = (drafts[sessionId] ?: SessionDraft()).copy(
            scrollAnchor = ScrollAnchor(index, offset.coerceAtLeast(0), atBottom),
        )
    }

    fun anchor(sessionId: String): ScrollAnchor? = drafts[sessionId]?.scrollAnchor
    fun updateInput(value: String) {
        updateState { it.copy(input = value, error = null) }
    }
    fun setQuote(messageId: Long, text: String) {
        val excerpt = quoteExcerpt(text)
        if (excerpt.isEmpty()) return
        updateState { it.copy(quote = QuotedMessage(messageId, excerpt)) }
    }

    fun clearQuote() {
        updateState { if (it.quote == null) it else it.copy(quote = null) }
    }

    fun attach(uri: Uri) {
        val current = state()
        if (current.switchingConversation) return
        val sessionId = current.conversationId ?: return
        val root = attachmentRoot(current) ?: return updateState { it.copy(error = strings().preparingSession) }
        if (current.attachments.size >= MAX_ATTACHMENTS) return updateState { it.copy(error = strings().maxAttachments) }
        importAttachment(sessionId, root) { attachmentStore.import(uri, root) }
    }

    fun importAttachment(sessionId: String, root: File, load: suspend () -> AttachmentImport) {
        importer.import(
            sessionId = sessionId,
            root = root,
            currentSession = { state().conversationId },
            attachmentCount = { state().attachments.size },
            load = load,
            onStart = {
                if (state().conversationId == sessionId) updateState { it.copy(importingAttachment = true) }
            },
            onImported = { ref ->
                updateState { it.copy(attachments = it.attachments + ref, error = null) }
            },
            onFailure = { failure ->
                updateState {
                    it.copy(
                        error = when (failure) {
                            AttachmentImportFailure.LIMIT -> strings().maxAttachments
                            AttachmentImportFailure.TOO_LARGE -> strings().attachmentTooLarge
                            AttachmentImportFailure.INSUFFICIENT_SPACE -> strings().insufficientSpace
                            AttachmentImportFailure.UNSUPPORTED_IMAGE -> strings().unsupportedImage
                            AttachmentImportFailure.FAILED -> strings().attachmentFailed
                        },
                    )
                }
            },
            onFinished = {
                if (state().conversationId == sessionId) updateState { it.copy(importingAttachment = false) }
            },
        )
    }

    fun removeAttachment(id: String) {
        updateState { it.copy(attachments = it.attachments.filterNot { ref -> ref.id == id }) }
    }

    fun mergeDroppedInput(input: String, dropped: List<String>): String =
        mergeDroppedInputText(input, dropped)

    fun attachmentRoot(current: ChatUiState): File? =
        current.conversationCwd.takeIf { it.isNotBlank() }?.let(::File)

    fun imageRefs(attachments: List<AttachmentRef>): List<AttachmentRef> =
        attachments.filter { it.kind == AttachmentKind.IMAGE }

    fun buildPrompt(quote: QuotedMessage?, attachments: List<AttachmentRef>, body: String): String = buildString {
        if (quote != null) {
            append(quote.text.lineSequence().joinToString("\n") { "> $it" })
            append("\n\n")
        }
        if (attachments.isNotEmpty()) {
            append(encodeAttachmentBlock(attachments, checkNotNull(attachmentRoot(state()))))
            append("\n\n")
        }
        append(body)
    }

    suspend fun normalizeImages(sessionId: String, refs: List<AttachmentRef>, root: File?): List<ImagePart> {
        if (refs.isEmpty() || root == null) return emptyList()
        if (state().conversationId == sessionId) updateState { it.copy(importingAttachment = true) }
        var failed = false
        val parts = try {
            withContext(ioDispatcher) {
                refs.mapNotNull { ref ->
                    val source = attachmentStore.file(ref, root)
                    val normalized = runCatching {
                        normalizeImage(source, ref.mime)
                    }.getOrNull()
                    if (normalized == null) {
                        failed = true
                        null
                    } else {
                        ImagePart(normalized.data, normalized.mimeType, source.absolutePath)
                    }
                }
            }
        } finally {
            if (state().conversationId == sessionId) updateState { it.copy(importingAttachment = false) }
        }
        if (failed && state().conversationId == sessionId) {
            updateState { it.copy(error = it.error ?: strings().imageFailed) }
        }
        return parts
    }

    private fun quoteExcerpt(text: String): String {
        val plain = markdownToPlainText(text.take(4000))
            .lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("\n")
        return if (plain.length <= 240) plain else plain.take(240).trimEnd() + "…"
    }
}

internal fun mergeDroppedInputText(input: String, dropped: List<String>): String {
    val texts = dropped.map { it.trim() }.filter { it.isNotEmpty() }
    if (texts.isEmpty()) return input
    val merged = texts.joinToString("\n\n")
    return if (input.isBlank()) merged else input.trimEnd() + "\n\n" + merged
}
