package io.github.whywood.superpi.ui.chat

import io.github.whywood.superpi.runtime.RemoteBotRef
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteChatControlsTest {
    private val local = RuntimeWorkspace(id = "ws-1", name = "Work", cwd = "/work")

    private fun remote(online: Boolean) = RuntimeWorkspace(
        id = "remote:desktop:pi",
        name = "PI",
        cwd = "/remote/desktop/pi",
        remote = RemoteBotRef(
            deviceId = "desktop",
            deviceName = "Mac",
            botId = "pi",
            botName = "PI",
            online = online,
        ),
    )

    @Test
    fun localWorkspaceKeepsModelControlsAndInput() {
        val controls = remoteChatControls(listOf(local), local.id)

        assertNull(controls.remote)
        assertTrue(controls.inputEnabled)
        assertTrue(controls.modelControlsVisible)
    }

    @Test
    fun onlineRemoteWorkspaceHidesModelControls() {
        val workspace = remote(online = true)
        val controls = remoteChatControls(listOf(local, workspace), workspace.id)

        assertEquals("Mac", controls.remote?.deviceName)
        assertTrue(controls.inputEnabled)
        assertFalse(controls.modelControlsVisible)
    }

    @Test
    fun offlineRemoteWorkspaceDisablesInput() {
        val workspace = remote(online = false)
        val controls = remoteChatControls(listOf(workspace), workspace.id)

        assertFalse(controls.inputEnabled)
        assertFalse(controls.modelControlsVisible)
    }

    @Test
    fun unknownWorkspaceFallsBackToLocalControls() {
        val controls = remoteChatControls(listOf(local), "missing")

        assertNull(controls.remote)
        assertTrue(controls.inputEnabled)
        assertTrue(controls.modelControlsVisible)
    }
}
