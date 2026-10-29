package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.RuntimeActivity

enum class RunErrorKind {
    INVALID_API_KEY,
    INSUFFICIENT_BALANCE,
    RATE_LIMITED,
    TIMEOUT,
    NETWORK,
    MODEL_UNAVAILABLE,
    TOOL_ARGS,
    SERVER,
    UNKNOWN,
}

data class ChatUiStrings(
    val thinking: String,
    val retrying: (Int) -> String,
    val compacting: String,
    val compactionFailed: String,
    val runningTool: (String) -> String,
    val requestFailed: String,
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
    val reloadBusy: String,
    val reloadFailed: String,
    val newChatFailed: String,
    val newChatTitle: String,
    val invalidApiKey: String,
    val rateLimited: String,
    val timeout: String,
    val networkError: String,
    val invalidToolArgs: String,
    val serviceUnavailable: String,
    val insufficientBalance: String,
    val modelUnavailable: String,
    val retry: String,
    val autoRetrying: (Int, Int) -> String,
    val withCode: (String, String) -> String,
    val nothingToCompact: String,
    val compactionBusy: String,
    val compactionUnavailable: String,
    val skillMissing: (String) -> String = { name -> "Skill not found: $name" },
    val stopBeforeCommand: String = "Stop the current reply before sending a command",
    val insufficientSpace: String = "Not enough storage to add this attachment",
    val unsupportedImage: String = "This image format is not supported",
    val imageFailed: String = "Could not process the image; sending it as a file",
) {
    fun activity(activity: RuntimeActivity?): String? = when (activity) {
        null -> null
        RuntimeActivity.Thinking -> thinking
        is RuntimeActivity.Retrying -> retrying(activity.attempt)
        RuntimeActivity.Compacting -> compacting
        RuntimeActivity.CompactionFailed -> compactionFailed
        is RuntimeActivity.RunningTool -> runningTool(activity.name)
    }

    fun runErrorKind(raw: String?): RunErrorKind {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return RunErrorKind.UNKNOWN
        return when {
            text.contains("API key", ignoreCase = true) || text.contains("401") -> RunErrorKind.INVALID_API_KEY
            text.contains("402") ||
                text.contains("Insufficient Balance", ignoreCase = true) ||
                text.contains("insufficient_quota", ignoreCase = true) -> RunErrorKind.INSUFFICIENT_BALANCE

            text.contains("429") -> RunErrorKind.RATE_LIMITED
            text.contains("timed out", ignoreCase = true) ||
                text.contains("timeout", ignoreCase = true) -> RunErrorKind.TIMEOUT

            text.contains("Network", ignoreCase = true) -> RunErrorKind.NETWORK
            text.contains("404") || text.contains("model not found", ignoreCase = true) -> RunErrorKind.MODEL_UNAVAILABLE
            text.contains("tool input", ignoreCase = true) ||
                text.contains("invalid JSON", ignoreCase = true) -> RunErrorKind.TOOL_ARGS

            SERVER_ERROR.containsMatchIn(text) -> RunErrorKind.SERVER
            else -> RunErrorKind.UNKNOWN
        }
    }

    fun canAutoRetry(kind: RunErrorKind): Boolean = when (kind) {
        RunErrorKind.TIMEOUT,
        RunErrorKind.INVALID_API_KEY,
        RunErrorKind.INSUFFICIENT_BALANCE,
        RunErrorKind.MODEL_UNAVAILABLE,
        -> false

        else -> true
    }

    fun runError(raw: String?): String {
        val text = raw?.trim().orEmpty()
        val code = ERROR_CODE.find(text)?.groupValues?.getOrNull(1)
        val base = when (runErrorKind(text)) {
            RunErrorKind.INVALID_API_KEY -> invalidApiKey
            RunErrorKind.INSUFFICIENT_BALANCE -> insufficientBalance
            RunErrorKind.RATE_LIMITED -> rateLimited
            RunErrorKind.TIMEOUT -> timeout
            RunErrorKind.NETWORK -> networkError
            RunErrorKind.MODEL_UNAVAILABLE -> modelUnavailable
            RunErrorKind.TOOL_ARGS -> invalidToolArgs
            RunErrorKind.SERVER -> serviceUnavailable
            RunErrorKind.UNKNOWN -> requestFailed
        }
        val kind = runErrorKind(text)
        val showCode = code != null && kind != RunErrorKind.TIMEOUT &&
            kind != RunErrorKind.NETWORK && kind != RunErrorKind.TOOL_ARGS
        return if (showCode) withCode(base, code!!) else base
    }

    companion object {
        private val SERVER_ERROR = Regex("\\b5\\d{2}\\b")
        private val ERROR_CODE = Regex("\\b([1-5]\\d{2})\\b")

        fun fallback(): ChatUiStrings = ChatUiStrings(
            thinking = "Thinking",
            retrying = { attempt -> "Retrying ($attempt)" },
            compacting = "Compacting context…",
            compactionFailed = "Compaction failed",
            runningTool = { name -> "Running $name" },
            requestFailed = "Request failed. Try again.",
            runtimeStartFailed = "Runtime failed to start",
            preparingSession = "Preparing session…",
            maxAttachments = "Up to 20 attachments",
            attachmentTooLarge = "Attachment exceeds 1.0 GB",
            attachmentFailed = "Failed to add attachment",
            restoringSession = "Restoring session…",
            sessionNotReady = "Session is not ready",
            createWorkspaceFailed = "Failed to create project",
            switchModelFailed = "Failed to switch model",
            switchThinkingFailed = "Failed to switch thinking level",
            openConversationFailed = "Failed to open conversation",
            reloadBusy = "Stop the response before reloading",
            reloadFailed = "Failed to reload the session",
            newChatFailed = "Failed to start new chat",
            newChatTitle = "New chat",
            invalidApiKey = "Invalid or expired API key",
            rateLimited = "Too many requests. Try again later.",
            timeout = "Request timed out. Check your network.",
            networkError = "Cannot reach the model service. Check your network.",
            invalidToolArgs = "Model returned invalid tool arguments. Try again.",
            serviceUnavailable = "Model service temporarily unavailable",
            insufficientBalance = "Insufficient balance. Top up and try again.",
            modelUnavailable = "Model unavailable. Switch models.",
            retry = "Retry",
            autoRetrying = { attempt, max -> "Retrying $attempt/$max" },
            withCode = { base, code -> "$base ($code)" },
            nothingToCompact = "No compactable history",
            compactionBusy = "Cannot compact while a reply is running",
            compactionUnavailable = "This session cannot be compacted",
            skillMissing = { name -> "Skill not found: $name" },
            stopBeforeCommand = "Stop the current reply before sending a command",
            insufficientSpace = "Not enough storage to add this attachment",
            unsupportedImage = "This image format is not supported",
            imageFailed = "Could not process the image; sending it as a file",
        )
    }
}
