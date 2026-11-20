package io.github.whywood.superpi.ui.common

import io.github.whywood.superpi.runtime.RemoteBotRef
import io.github.whywood.superpi.runtime.RuntimeWorkspace

internal data class RemoteChatControls(
    val remote: RemoteBotRef?,
    val inputEnabled: Boolean,
    val modelControlsVisible: Boolean,
)

internal fun remoteChatControls(workspaces: List<RuntimeWorkspace>, workspaceId: String): RemoteChatControls {
    val remote = workspaces.firstOrNull { workspace -> workspace.id == workspaceId }?.remote
    return RemoteChatControls(
        remote = remote,
        inputEnabled = remote?.online != false,
        modelControlsVisible = remote == null,
    )
}
