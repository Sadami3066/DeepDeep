package io.github.whywood.superpi.runtime

internal class MergedWorkspaceDirectory(
    private val local: WorkspaceDirectory,
    private val remote: suspend () -> List<RuntimeWorkspace>,
) : WorkspaceDirectory {
    override suspend fun read(): List<RuntimeWorkspace> = local.read() + remote()

    override suspend fun add(workspace: RuntimeWorkspace) {
        if (workspace.remote == null) local.add(workspace)
    }

    override suspend fun remove(id: String) {
        local.remove(id)
    }
}
