package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.ConversationSummary

data class SessionSearchHit(
    val conversation: ConversationSummary,
    val workspaceName: String,
)
