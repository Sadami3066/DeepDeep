package io.github.whywood.deepdeep.runtime

import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RuntimeWorkspaces(
    private val mutex: Mutex,
    private val homeDir: File,
    private val workspacesDir: File,
    private val directories: WorkspaceDirectory,
    private val catalog: SessionDirectory,
    private val emitListChanged: suspend () -> Unit,
    private val emitRemoved: suspend (String) -> Unit,
    private val stopTurn: suspend (String) -> List<String>,
    private val detachForget: suspend (String) -> Unit,
    private val sessionCleaner: ((String) -> Unit)?,
) {
    suspend fun list(): List<RuntimeWorkspace> = buildList {
        add(homeWorkspace())
        addAll(directories.read().filter { it.id != AgentRuntimeHost.HOME_ID })
    }

    suspend fun create(name: String): RuntimeWorkspace = mutex.withLock {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "工作区名称不能为空" }
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val directory = File(workspacesDir, id).apply { mkdirs() }
        val workspace = RuntimeWorkspace(
            id = id,
            name = trimmed,
            cwd = directory.absolutePath,
            createdAt = System.currentTimeMillis(),
        )
        directories.add(workspace)
        emitListChanged()
        workspace
    }

    suspend fun rename(id: String, name: String) {
        if (id == AgentRuntimeHost.HOME_ID) return
        val workspace = directories.read().firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        directories.add(workspace.copy(name = trimmed))
        emitListChanged()
    }

    suspend fun delete(id: String) {
        if (id == AgentRuntimeHost.HOME_ID) return
        val workspace = directories.read().firstOrNull { it.id == id } ?: return
        val sessions = catalog.list(workspace.cwd)
        sessions.forEach { session ->
            runCatching { stopTurn(session.id) }
            detachForget(session.id)
            sessionCleaner?.invoke(session.id)
        }
        sessions.forEach { session ->
            catalog.delete(session.filePath)
            catalog.removeMeta(session.id)
            emitRemoved(session.id)
        }
        runCatching { File(workspace.cwd).deleteRecursively() }
        directories.remove(id)
        emitListChanged()
    }

    suspend fun find(workspaceId: String): RuntimeWorkspace =
        if (workspaceId == AgentRuntimeHost.HOME_ID || workspaceId.isBlank()) homeWorkspace()
        else directories.read().firstOrNull { it.id == workspaceId } ?: homeWorkspace()

    suspend fun visibleCwds(): List<String> =
        listOf(homeDir.absolutePath) + directories.read().map { it.cwd }

    suspend fun sessionCount(workspaceId: String): Int =
        catalog.list(find(workspaceId).cwd).size

    private fun homeWorkspace(): RuntimeWorkspace = RuntimeWorkspace(
        id = AgentRuntimeHost.HOME_ID,
        name = AgentRuntimeHost.HOME_NAME,
        cwd = homeDir.absolutePath,
        isHome = true,
    )
}
