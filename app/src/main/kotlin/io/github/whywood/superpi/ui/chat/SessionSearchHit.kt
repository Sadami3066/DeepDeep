package io.github.whywood.superpi.ui.chat

import io.github.whywood.superpi.runtime.ConversationSummary

data class SessionSearchHit(
    val conversation: ConversationSummary,
    val workspaceName: String,
    val workspaceDevice: String? = null,
)
