package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.RuntimeActivity

data class ChatUiStrings(
    val thinking: String,
    val retrying: (Int) -> String,
    val compacting: String,
    val compactionFailed: String,
    val runningTool: (String) -> String,
    val requestFailed: String,
    val emptyReply: String,
    val runtimeStartFailed: String,
    val preparingSession: String,
    val maxAttachments: String,
    val attachmentTooLarge: String,
    val attachmentFailed: String,
    val restoringSession: String,
    val sessionNotReady: String,
    val createWorkspaceFailed: String,
    val switchModelFailed: String,
    val switchThinkingFailed: String,
    val openConversationFailed: String,
    val newChatFailed: String,
    val newChatTitle: String,
    val invalidApiKey: String,
    val rateLimited: String,
    val timeout: String,
    val networkError: String,
    val invalidToolArgs: String,
    val serviceUnavailable: String,
) {
    fun activity(activity: RuntimeActivity?): String? = when (activity) {
        null -> null
        RuntimeActivity.Thinking -> thinking
        is RuntimeActivity.Retrying -> retrying(activity.attempt)
        RuntimeActivity.Compacting -> compacting
        RuntimeActivity.CompactionFailed -> compactionFailed
        is RuntimeActivity.RunningTool -> runningTool(activity.name)
    }

    companion object {
        fun fallback(): ChatUiStrings = ChatUiStrings(
            thinking = "Thinking",
            retrying = { attempt -> "Retrying ($attempt)" },
            compacting = "Compacting context…",
            compactionFailed = "Compaction failed",
            runningTool = { name -> "Running $name" },
            requestFailed = "Request failed. Try again.",
            emptyReply = "(No reply got)",
            runtimeStartFailed = "Runtime failed to start",
            preparingSession = "Preparing session…",
            maxAttachments = "Up to 5 attachments",
            attachmentTooLarge = "Attachment exceeds 25 MB",
            attachmentFailed = "Failed to add attachment",
            restoringSession = "Restoring session…",
            sessionNotReady = "Session is not ready",
            createWorkspaceFailed = "Failed to create project",
            switchModelFailed = "Failed to switch model",
            switchThinkingFailed = "Failed to switch thinking level",
            openConversationFailed = "Failed to open conversation",
            newChatFailed = "Failed to start new chat",
            newChatTitle = "New chat",
            invalidApiKey = "Invalid or expired API key",
            rateLimited = "Too many requests. Try again later.",
            timeout = "Request timed out. Check your network.",
            networkError = "Cannot reach the model service. Check your network.",
            invalidToolArgs = "Model returned invalid tool arguments. Try again.",
            serviceUnavailable = "Model service temporarily unavailable",
        )

    }
}
