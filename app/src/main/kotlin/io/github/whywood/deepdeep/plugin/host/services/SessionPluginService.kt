package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.SessionInfo
import io.github.whywood.deepdeep.plugin.api.SessionService
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.SessionCommands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

internal class SessionPluginService(
    private val runtime: ConversationQueries,
    private val commands: SessionCommands?,
    private val activeSessionId: StateFlow<String?>,
) : SessionService {
    override val active: SessionInfo?
        get() = activeSessionId.value?.let { id ->
            SessionInfo(id = id, busy = id in runtime.busySessionIds.value)
        }

    override fun observe(): Flow<SessionInfo?> = flow {
        activeSessionId.collect { id ->
            emit(
                id?.let { current ->
                    SessionInfo(
                        id = current,
                        title = runCatching { runtime.sessionTitle(current) }.getOrNull(),
                        busy = current in runtime.busySessionIds.value,
                    )
                },
            )
        }
    }

    override suspend fun list(): List<SessionInfo> {
        val busy = runtime.busySessionIds.value
        return runtime.allSessions().map { summary ->
            SessionInfo(
                id = summary.id,
                title = summary.title,
                cwd = summary.cwd,
                busy = summary.id in busy,
            )
        }
    }

    override suspend fun prompt(text: String, sessionId: String?): Boolean {
        val target = sessionId ?: activeSessionId.value ?: return false
        val sender = commands ?: return false
        return runCatching { sender.send(target, text) }.isSuccess
    }
}
