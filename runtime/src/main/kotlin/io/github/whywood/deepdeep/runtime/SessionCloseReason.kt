package io.github.whywood.deepdeep.runtime

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
