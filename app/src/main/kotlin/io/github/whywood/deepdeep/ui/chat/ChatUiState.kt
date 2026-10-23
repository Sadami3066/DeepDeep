package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.ThinkingLevel

data class QuotedMessage(
    val messageId: Long,
    val text: String,
)

data class ChatUiState(
    val ready: Boolean = false,
    val configured: Boolean = false,
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val quote: QuotedMessage? = null,
    val attachments: List<AttachmentRef> = emptyList(),
    val sending: Boolean = false,
    val activity: String? = null,
    val activityStartedAt: Long? = null,
    val error: String? = null,
    val providerId: String = "",
    val modelId: String = "",
    val modelLabel: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val models: List<ModelOption> = emptyList(),
    val conversationId: String? = null,
    val conversationTitle: String = "",
    val conversations: List<ConversationSummary> = emptyList(),
    val archivedConversations: List<ConversationSummary> = emptyList(),
    val archivedCount: Int = 0,
    val workspaces: List<RuntimeWorkspace> = emptyList(),
    val currentWorkspaceId: String = "home",
    val conversationCwd: String = "",
    val busySessionIds: Set<String> = emptySet(),
    val historyLoaded: Boolean = false,
    val switchingConversation: Boolean = false,
    val reloading: Boolean = false,
)
