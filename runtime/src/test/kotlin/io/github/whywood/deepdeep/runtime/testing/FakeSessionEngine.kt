package io.github.whywood.deepdeep.runtime.testing

import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.SessionEngine
import io.github.whywood.deepdeep.runtime.SessionHandle

class FakeSessionEngine(
    private val idPrefix: String = "session",
) : SessionEngine {
    val created = mutableListOf<FakeSessionHandle>()
    val configs = mutableListOf<ModelConfig>()
    val idsByFile = mutableMapOf<String, String>()
    var onCreated: (FakeSessionHandle) -> Unit = {}
    var mcpServers: Map<String, McpServerConfig> = emptyMap()
    private var counter = 0
    val createCount: Int get() = created.size
    val handles: List<FakeSessionHandle> get() = created

    override suspend fun create(file: String?, config: ModelConfig): SessionHandle {
        val sessionId = file?.let { path -> idsByFile.getOrPut(path) { idPrefix + "-" + (++counter) } }
            ?: (idPrefix + "-" + (++counter))
        configs += config
        val handle = FakeSessionHandle(sessionId = sessionId, sessionFile = file)
        created += handle
        onCreated(handle)
        return handle
    }

    override suspend fun loadMcpServers(): Map<String, McpServerConfig> = mcpServers

    override fun titleFor(text: String): String = text.take(12)
}
