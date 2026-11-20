package io.github.whywood.superpi.ui.common

import io.github.whywood.superpi.runtime.RuntimeWorkspace

internal data class WorkspaceRowMeta(
    val enabled: Boolean,
    val manageable: Boolean,
    val sourceDevice: String?,
)

internal fun workspaceRowMeta(workspace: RuntimeWorkspace): WorkspaceRowMeta = WorkspaceRowMeta(
    enabled = workspace.remote?.online != false,
    manageable = workspace.remote == null,
    sourceDevice = workspace.remote?.deviceName,
)

internal fun preferredNewChatWorkspaceId(workspaces: List<RuntimeWorkspace>, activeWorkspaceId: String): String? {
    val active = workspaces.firstOrNull { workspace ->
        workspace.id == activeWorkspaceId && workspaceRowMeta(workspace).enabled
    }
    return (active ?: workspaces.firstOrNull { workspace -> workspaceRowMeta(workspace).enabled })?.id
}
