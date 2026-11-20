package io.github.whywood.superpi.ui.common

import io.github.whywood.superpi.runtime.RemoteBotRef
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceRowMetaTest {
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
    fun localWorkspaceIsEnabledAndManageable() {
        val meta = workspaceRowMeta(local)

        assertEquals(true, meta.enabled)
        assertEquals(true, meta.manageable)
        assertNull(meta.sourceDevice)
    }

    @Test
    fun onlineRemoteWorkspaceIsEnabledButNotManageable() {
        val meta = workspaceRowMeta(remote(online = true))

        assertEquals(true, meta.enabled)
        assertEquals(false, meta.manageable)
        assertEquals("Mac", meta.sourceDevice)
    }

    @Test
    fun offlineRemoteWorkspaceIsDisabled() {
        val meta = workspaceRowMeta(remote(online = false))

        assertEquals(false, meta.enabled)
        assertEquals(false, meta.manageable)
        assertEquals("Mac", meta.sourceDevice)
    }

    @Test
    fun newChatTargetSkipsOfflineRemoteWorkspace() {
        val home = RuntimeWorkspace(id = "home", name = "PI", cwd = "/home", isHome = true)
        val remoteOnline = remote(online = true)
        val remoteOffline = remote(online = false)

        assertEquals(home.id, preferredNewChatWorkspaceId(listOf(home, remoteOffline), remoteOffline.id))
        assertEquals(remoteOnline.id, preferredNewChatWorkspaceId(listOf(home, remoteOnline), remoteOnline.id))
        assertEquals(home.id, preferredNewChatWorkspaceId(listOf(remoteOffline, home), home.id))
        assertNull(preferredNewChatWorkspaceId(listOf(remoteOffline), remoteOffline.id))
        assertNull(preferredNewChatWorkspaceId(emptyList(), "home"))
    }
}
