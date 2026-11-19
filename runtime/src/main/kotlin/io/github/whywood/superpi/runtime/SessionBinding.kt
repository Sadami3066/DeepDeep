package io.github.whywood.superpi.runtime

import io.github.whywood.superpi.sdk.coding.session.AgentSession

data class BoundSession(
    val sessionId: String,
    val sessionFile: String?,
    val cwd: String,
)

class SessionBinding(
    val supportsChatDelivery: Boolean = true,
    private val live: (() -> BoundSession?)? = null,
) {
    @Volatile
    private var bound: BoundSession? = null

    fun attach(session: AgentSession) {
        bound = BoundSession(
            sessionId = session.sessionId,
            sessionFile = session.sessionFile?.toString(),
            cwd = session.cwd.toString(),
        )
    }

    fun bind(session: BoundSession) {
        bound = session
    }

    fun current(): BoundSession? = live?.invoke() ?: bound
}
