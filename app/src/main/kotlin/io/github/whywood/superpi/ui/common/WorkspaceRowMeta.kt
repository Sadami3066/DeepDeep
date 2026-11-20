package io.github.whywood.superpi.ui.common

import io.github.whywood.superpi.runtime.RuntimeWorkspace

internal enum class WorkspaceMenuAction {
    NEW_SESSION,
    RENAME,
    PIN,
    SETTINGS,
    DELETE,
}

internal data class WorkspaceRowMeta(
    val selectable: Boolean,
    val canCreateSession: Boolean,
    val manageable: Boolean,
    val sourceDevice: String?,
    val offline: Boolean,
)

internal fun workspaceRowMeta(workspace: RuntimeWorkspace): WorkspaceRowMeta {
    val remote = workspace.remote
    return WorkspaceRowMeta(
        selectable = true,
        canCreateSession = remote?.online != false,
        manageable = remote == null,
        sourceDevice = remote?.deviceName,
        offline = remote != null && !remote.online,
    )
}

internal fun workspaceMenuActions(workspace: RuntimeWorkspace): List<WorkspaceMenuAction> {
    val meta = workspaceRowMeta(workspace)
    return buildList {
        if (meta.canCreateSession) add(WorkspaceMenuAction.NEW_SESSION)
        if (!workspace.isHome && meta.manageable) add(WorkspaceMenuAction.RENAME)
        add(WorkspaceMenuAction.PIN)
        if (meta.manageable) add(WorkspaceMenuAction.SETTINGS)
        if (!workspace.isHome && meta.manageable) add(WorkspaceMenuAction.DELETE)
    }
}

internal fun preferredNewChatWorkspaceId(workspaces: List<RuntimeWorkspace>, activeWorkspaceId: String): String? {
    val active = workspaces.firstOrNull { workspace ->
        workspace.id == activeWorkspaceId && workspaceRowMeta(workspace).canCreateSession
    }
    return (active ?: workspaces.firstOrNull { workspace -> workspaceRowMeta(workspace).canCreateSession })?.id
}
