package io.github.whywood.superpi.runtime

enum class SessionCloseReason {
    Idle,
    Reload,
    Reconfigure,
    Deleted,
    Closed,
}

data class SessionClosure(
    val sessionId: String,
    val reason: SessionCloseReason,
)
