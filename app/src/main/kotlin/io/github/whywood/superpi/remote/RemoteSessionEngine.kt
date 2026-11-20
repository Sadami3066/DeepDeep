package io.github.whywood.superpi.remote

import io.github.whywood.superpi.runtime.ModelConfig
import io.github.whywood.superpi.runtime.SessionEngine
import io.github.whywood.superpi.runtime.SessionHandle
import io.github.whywood.superpi.sdk.mcp.config.McpServerConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope

class RemoteSessionEngine(
    workspace: File,
    agentDir: File,
    private val target: RemoteSessionTarget,
    private val transport: RemoteAgentTransport,
    private val scope: CoroutineScope? = null,
) : SessionEngine {
    private val cwd: File = workspace.absoluteFile

    private val agentDirectory: File = agentDir.absoluteFile

    override suspend fun create(file: String?, config: ModelConfig): SessionHandle {
        val mirror = if (file.isNullOrBlank()) {
            RemoteMirrorSession.create(cwd, agentDirectory)
        } else {
            RemoteMirrorSession.open(File(file))
        }
        return RemoteSessionHandle(target, transport, mirror, scope)
    }

    override suspend fun loadMcpServers(): Map<String, McpServerConfig> = emptyMap()

    override fun titleFor(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return DEFAULT_TITLE
        return if (normalized.length <= TITLE_LENGTH) normalized else normalized.take(TITLE_LENGTH) + ELLIPSIS
    }

    private companion object {
        private const val TITLE_LENGTH = 28
        private const val DEFAULT_TITLE = "New chat"
        private const val ELLIPSIS = "…"
    }
}
