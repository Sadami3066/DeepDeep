package io.github.whywood.deepdeep.ui.chat.state

import android.net.Uri
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.QuotedMessage
import io.github.whywood.deepdeep.ui.chat.ScrollAnchor
import io.github.whywood.deepdeep.ui.chat.markdownToPlainText
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
    private val strings: () -> ChatUiStrings,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
) {
    private val drafts = mutableMapOf<String, SessionDraft>()
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
        scope.launch {
            val result = withContext(ioDispatcher) { load() }
            if (state().conversationId != sessionId) {
                if (result is AttachmentImport.Imported) {
                    runCatching { attachmentStore.file(result.ref, root).delete() }
                }
                return@launch
            }
            when (result) {
                is AttachmentImport.Imported -> updateState {
                    if (it.attachments.size >= MAX_ATTACHMENTS) {
                        it.copy(error = strings().maxAttachments)
                    } else {
                        it.copy(attachments = it.attachments + result.ref, error = null)
                    }
                }

                is AttachmentImport.TooLarge -> updateState { it.copy(error = strings().attachmentTooLarge) }
                is AttachmentImport.InsufficientSpace -> updateState { it.copy(error = strings().insufficientSpace) }
                is AttachmentImport.UnsupportedImage -> updateState { it.copy(error = strings().unsupportedImage) }
                is AttachmentImport.Failed -> updateState { it.copy(error = strings().attachmentFailed) }
            }
        }
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
            append(encodeAttachmentBlock(attachments))
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
                    val normalized = runCatching {
                        normalizeImage(attachmentStore.file(ref, root), ref.mime)
                    }.getOrNull()
                    if (normalized == null) {
                        failed = true
                        null
                    } else {
                        ImagePart(normalized.data, normalized.mimeType)
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

    companion object {
        const val MAX_ATTACHMENTS = 20
    }
}

internal fun mergeDroppedInputText(input: String, dropped: List<String>): String {
    val texts = dropped.map { it.trim() }.filter { it.isNotEmpty() }
    if (texts.isEmpty()) return input
    val merged = texts.joinToString("\n\n")
    return if (input.isBlank()) merged else input.trimEnd() + "\n\n" + merged
}
