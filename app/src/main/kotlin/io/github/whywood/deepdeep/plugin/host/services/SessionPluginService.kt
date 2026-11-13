package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.MessageRole
import io.github.whywood.deepdeep.plugin.api.PluginMessage
import io.github.whywood.deepdeep.plugin.api.SessionInfo
import io.github.whywood.deepdeep.plugin.api.SessionService
import io.github.whywood.deepdeep.runtime.Maintenance
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.SessionCommands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal class SessionPluginService(
    private val runtime: ConversationQueries,
    private val commands: SessionCommands?,
    private val activeSessionId: StateFlow<String?>,
    private val maintenance: Maintenance? = null,
) : SessionService {
    override val active: SessionInfo?
        get() = activeSessionId.value?.let { id ->
            SessionInfo(id = id, busy = id in runtime.busySessionIds.value)
        }

    override fun observe(): Flow<SessionInfo?> =
        combine(activeSessionId, runtime.busySessionIds) { id, busy ->
            id?.let { current ->
                SessionInfo(
                    id = current,
                    title = runCatching { runtime.sessionTitle(current) }.getOrNull(),
                    busy = current in busy,
                )
            }
        }.distinctUntilChanged()

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

    override suspend fun history(sessionId: String, limit: Int): List<PluginMessage> = runCatching {
        runtime.history(sessionId).takeLast(limit).map { message ->
            PluginMessage(
                id = message.id.toString(),
                role = if (message.role == io.github.whywood.deepdeep.runtime.AssistantRole.USER) {
                    MessageRole.USER
                } else {
                    MessageRole.ASSISTANT
                },
                text = message.text,
                timestampMs = message.startedAt,
            )
        }
    }.getOrDefault(emptyList())

    override suspend fun stop(sessionId: String): Boolean = commands?.let { sender ->
        runCatching { sender.stop(sessionId) }.isSuccess
    } ?: false

    override suspend fun create(title: String?): SessionInfo? {
        val sender = commands ?: return null
        val workspace = runCatching { runtime.workspaces().firstOrNull() }.getOrNull() ?: return null
        val opened = runCatching { sender.newSession(workspace.id) }.getOrNull() ?: return null
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanTitle != null) {
            maintenance?.let { admin -> runCatching { admin.renameSession(opened.sessionId, cleanTitle) } }
        }
        return SessionInfo(
            id = opened.sessionId,
            title = cleanTitle,
            cwd = opened.cwd,
            busy = false,
        )
    }

    override suspend fun rename(sessionId: String, title: String): Boolean = adminCall { admin ->
        admin.renameSession(sessionId, title)
    }

    override suspend fun delete(sessionId: String): Boolean = adminCall { admin ->
        admin.deleteSession(sessionId)
    }

    override suspend fun open(sessionId: String): Boolean {
        val sender = commands ?: return false
        val summary = runCatching { runtime.allSessions().firstOrNull { it.id == sessionId } }.getOrNull() ?: return false
        return runCatching { sender.openSession(summary.filePath, summary.cwd) }.isSuccess
    }

    override suspend fun archive(sessionId: String, archived: Boolean): Boolean = adminCall { admin ->
        admin.setSessionArchived(sessionId, archived)
    }

    private suspend fun adminCall(block: suspend (Maintenance) -> Unit): Boolean {
        val admin = maintenance ?: return false
        return runCatching { block(admin) }.isSuccess
    }
}
