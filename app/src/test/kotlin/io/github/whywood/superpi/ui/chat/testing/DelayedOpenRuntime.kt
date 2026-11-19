package io.github.whywood.superpi.ui.chat.testing

import io.github.whywood.superpi.runtime.AgentRuntime
import io.github.whywood.superpi.runtime.OpenedSession
import kotlinx.coroutines.CompletableDeferred

class DelayedOpenRuntime(private val delegate: FakeAgentRuntime) : AgentRuntime by delegate {
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
        val pending = gate
        if (pending != null) pending.await()
        return delegate.openSession(filePath, cwd)
    }
}
