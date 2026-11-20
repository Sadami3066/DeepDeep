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
    fun localWorkspaceIsSelectableAndCreatable() {
        val meta = workspaceRowMeta(local)

        assertEquals(true, meta.selectable)
        assertEquals(true, meta.canCreateSession)
        assertEquals(true, meta.manageable)
        assertEquals(false, meta.offline)
        assertNull(meta.sourceDevice)
    }

    @Test
    fun onlineRemoteWorkspaceIsSelectableButNotManageable() {
        val meta = workspaceRowMeta(remote(online = true))

        assertEquals(true, meta.selectable)
        assertEquals(true, meta.canCreateSession)
        assertEquals(false, meta.manageable)
        assertEquals(false, meta.offline)
        assertEquals("Mac", meta.sourceDevice)
    }

    @Test
    fun offlineRemoteWorkspaceStaysSelectableButCannotCreateSessions() {
        val meta = workspaceRowMeta(remote(online = false))

        assertEquals(true, meta.selectable)
        assertEquals(false, meta.canCreateSession)
        assertEquals(false, meta.manageable)
        assertEquals(true, meta.offline)
        assertEquals("Mac", meta.sourceDevice)
    }

    @Test
    fun menuActionsFollowWorkspaceKind() {
        val localHome = RuntimeWorkspace(id = "home", name = "PI", cwd = "/home", isHome = true)

        assertEquals(
            listOf(
                WorkspaceMenuAction.NEW_SESSION,
                WorkspaceMenuAction.RENAME,
                WorkspaceMenuAction.PIN,
                WorkspaceMenuAction.SETTINGS,
                WorkspaceMenuAction.DELETE,
            ),
            workspaceMenuActions(local),
        )
        assertEquals(
            listOf(WorkspaceMenuAction.NEW_SESSION, WorkspaceMenuAction.PIN, WorkspaceMenuAction.SETTINGS),
            workspaceMenuActions(localHome),
        )
        assertEquals(
            listOf(WorkspaceMenuAction.NEW_SESSION, WorkspaceMenuAction.PIN),
            workspaceMenuActions(remote(online = true)),
        )
        assertEquals(listOf(WorkspaceMenuAction.PIN), workspaceMenuActions(remote(online = false)))
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
