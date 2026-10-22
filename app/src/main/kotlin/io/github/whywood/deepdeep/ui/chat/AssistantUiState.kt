package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val modelOverride: String? = null,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val conversationId: String? = null,
    val conversationTitle: String = "",
    val conversations: List<ConversationSummary> = emptyList(),
    val historyLoaded: Boolean = false,
)

fun AssistantMessage.answerText(): String {
    val body = text
        .removeSuffix("\n\n$STOPPED_MARKER")
        .removeSuffix(STOPPED_MARKER)
        .trim()
    return body.takeUnless { it == EMPTY_REPLY || it == NO_REPLY_MARKER }.orEmpty()
}

const val STOPPED_MARKER = "（已停止）"
const val EMPTY_REPLY = "没有收到回复，请重试。"
const val NO_REPLY_MARKER = "（没有收到回复）"
