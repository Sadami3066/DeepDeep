package io.github.whywood.deepdeep.runtime

internal class CatalogSessionDirectory(
    private val catalog: SessionCatalog,
) : SessionDirectory {
    override suspend fun list(cwd: String): List<ConversationSummary> = catalog.list(cwd)

    override suspend fun listAll(cwds: List<String>): List<ConversationSummary> = catalog.listAll(cwds)

    override suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary? = catalog.find(cwds, sessionId)

    override suspend fun delete(filePath: String) = catalog.delete(filePath)

    override suspend fun rename(id: String, title: String) = catalog.rename(id, title)

    override suspend fun setPinned(id: String, pinned: Boolean) = catalog.setPinned(id, pinned)

    override suspend fun setArchived(id: String, archived: Boolean) = catalog.setArchived(id, archived)

    override suspend fun removeMeta(id: String) = catalog.removeMeta(id)
}

internal class WorkspaceStoreDirectory(
    private val store: WorkspaceStore,
) : WorkspaceDirectory {
    override suspend fun read(): List<RuntimeWorkspace> = store.read()

    override suspend fun add(workspace: RuntimeWorkspace) = store.add(workspace)

    override suspend fun remove(id: String) = store.remove(id)
}
