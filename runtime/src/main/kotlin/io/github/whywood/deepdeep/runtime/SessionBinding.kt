package io.github.whywood.deepdeep.runtime

import dev.pi.coding.session.AgentSession

data class BoundSession(
    val sessionId: String,
    val sessionFile: String?,
    val cwd: String,
)

class SessionBinding(val supportsChatDelivery: Boolean = true) {
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

    fun current(): BoundSession? = bound
}
