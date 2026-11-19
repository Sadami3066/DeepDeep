package io.github.whywood.superpi.ui.common.chat

import io.github.whywood.superpi.conversation.ConversationState
import io.github.whywood.superpi.data.AttachmentRef
import io.github.whywood.superpi.runtime.AssistantMessage
import io.github.whywood.superpi.runtime.ConversationSummary
import io.github.whywood.superpi.runtime.ModelOption
import io.github.whywood.superpi.runtime.QueuedInput
import io.github.whywood.superpi.runtime.RuntimeActivity
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import io.github.whywood.superpi.runtime.SessionUsage
import io.github.whywood.superpi.runtime.ThinkingLevel



data class QuotedMessage(
    val messageId: Long,
    val text: String,
)

internal fun activeBotFor(
    workspaces: List<io.github.whywood.superpi.runtime.RuntimeWorkspace>,
    workspaceId: String,
    cwd: String,
): io.github.whywood.superpi.runtime.RuntimeWorkspace? = workspaces.firstOrNull {
    it.id == workspaceId && it.cwd == cwd
}

data class ChatUiState(
    val conversation: ConversationState = ConversationState(),
    val ready: Boolean = false,
    val configured: Boolean = false,
    val input: String = "",
    val quote: QuotedMessage? = null,
    val attachments: List<AttachmentRef> = emptyList(),
    val error: String? = null,
    val providerId: String = "",
    val modelId: String = "",
    val modelLabel: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val models: List<ModelOption> = emptyList(),
    val conversationId: String? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val archivedConversations: List<ConversationSummary> = emptyList(),
    val archivedCount: Int = 0,
    val workspaces: List<RuntimeWorkspace> = emptyList(),
    val currentWorkspaceId: String = "home",
    val conversationCwd: String = "",
    val busySessionIds: Set<String> = emptySet(),
    val switchingConversation: Boolean = false,
    val reloading: Boolean = false,
    val compacting: Boolean = false,
    val importingAttachment: Boolean = false,
) {
    val messages: List<AssistantMessage> get() = conversation.messages
    val conversationTitle: String get() = conversation.title
    val sending: Boolean get() = conversation.sending
    val activity: RuntimeActivity? get() = conversation.activity
    val activityStartedAt: Long? get() = conversation.activityStartedAt
    val historyLoaded: Boolean get() = conversation.loaded
    val usage: SessionUsage? get() = conversation.usage
    val queuedInputs: List<QueuedInput> get() = conversation.queuedInputs

    fun updateConversation(transform: (ConversationState) -> ConversationState): ChatUiState =
        copy(conversation = transform(conversation))
}
