package io.github.whywood.superpi.ui

import io.github.whywood.superpi.runtime.ConversationSummary
import io.github.whywood.superpi.runtime.RemoteBotRef
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperPIDrawerEntriesTest {
    private val workspace = RuntimeWorkspace(id = "ws-1", name = "Work", cwd = "/work", isHome = true)

    private fun session(
        id: String,
        updatedAt: Long,
        pinned: Boolean = false,
        archived: Boolean = false,
        cwd: String = "/work",
    ) = ConversationSummary(
        id = id,
        title = "Session $id",
        updatedAt = updatedAt,
        filePath = "$cwd/$id.jsonl",
        cwd = cwd,
        pinned = pinned,
        archived = archived,
    )

    private fun remoteWorkspace(online: Boolean) = RuntimeWorkspace(
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

    private fun entries(
        sessions: List<ConversationSummary>,
        workspaceExpanded: Boolean = true,
        sessionsExpanded: Boolean = false,
    ): List<DrawerEntry> = buildDrawerEntries(
        workspaces = listOf(workspace),
        sessionsByCwd = mapOf("/work" to sessions),
        expandedWorkspaceIds = if (workspaceExpanded) setOf(workspace.id) else emptySet(),
        expandedSessionWorkspaceIds = if (sessionsExpanded) setOf(workspace.id) else emptySet(),
    )

    @Test
    fun collapsesToFiveWithMoreRow() {
        val sessions = (1..7).map { session("s$it", it.toLong()) }
        val result = entries(sessions)
        val visible = result.filterIsInstance<DrawerEntry.Session>()
        assertEquals(5, visible.size)
        assertEquals(listOf("s7", "s6", "s5", "s4", "s3"), visible.map { it.conversation.id })
        val more = result.filterIsInstance<DrawerEntry.SessionMore>().single()
        assertEquals(workspace.id, more.workspaceId)
        assertEquals(2, more.remaining)
        assertTrue(result.filterIsInstance<DrawerEntry.SessionLess>().isEmpty())
    }

    @Test
    fun showsAllAndLessRowWhenSessionsExpanded() {
        val sessions = (1..7).map { session("s$it", it.toLong()) }
        val result = entries(sessions, sessionsExpanded = true)
        assertEquals(7, result.filterIsInstance<DrawerEntry.Session>().size)
        val less = result.filterIsInstance<DrawerEntry.SessionLess>().single()
        assertEquals(workspace.id, less.workspaceId)
        assertTrue(result.filterIsInstance<DrawerEntry.SessionMore>().isEmpty())
    }

    @Test
    fun keepsTopFiveOrderedByPinnedThenUpdatedAt() {
        val sessions = listOf(
            session("a", 5),
            session("pin", 1, pinned = true),
            session("b", 4),
            session("c", 3),
            session("d", 2),
            session("e", 6),
        )
        val result = entries(sessions)
        val visible = result.filterIsInstance<DrawerEntry.Session>().map { it.conversation.id }
        assertEquals(listOf("pin", "e", "a", "b", "c"), visible)
        assertEquals(1, result.filterIsInstance<DrawerEntry.SessionMore>().single().remaining)
    }

    @Test
    fun skipsToggleRowsWhenFiveOrFewer() {
        val result = entries((1..3).map { session("s$it", it.toLong()) })
        assertEquals(3, result.filterIsInstance<DrawerEntry.Session>().size)
        assertTrue(result.filterIsInstance<DrawerEntry.SessionMore>().isEmpty())
        assertTrue(result.filterIsInstance<DrawerEntry.SessionLess>().isEmpty())
    }

    @Test
    fun collapsedWorkspaceRendersNoSessions() {
        val result = entries((1..7).map { session("s$it", it.toLong()) }, workspaceExpanded = false)
        assertTrue(result.filterIsInstance<DrawerEntry.Session>().isEmpty())
        assertTrue(result.filterIsInstance<DrawerEntry.SessionMore>().isEmpty())
        assertTrue(result.any { it.key == "workspace_${workspace.id}" })
    }

    @Test
    fun remoteWorkspacesUseTheSameEntriesAsLocalBots() {
        val local = RuntimeWorkspace(id = "ws-1", name = "Work", cwd = "/work")
        val remote = remoteWorkspace(online = true)
        val result = buildDrawerEntries(
            workspaces = listOf(local, remote),
            sessionsByCwd = mapOf(
                local.cwd to listOf(session("s1", 1)),
                remote.cwd to listOf(session("remote-s1", 2, cwd = remote.cwd)),
            ),
            expandedWorkspaceIds = setOf(local.id, remote.id),
            expandedSessionWorkspaceIds = emptySet(),
        )

        assertEquals(
            listOf(local.id, remote.id),
            result.filterIsInstance<DrawerEntry.WorkspaceEntry>().map { it.workspace.id },
        )
        assertEquals(
            listOf(local.id, remote.id),
            result.filterIsInstance<DrawerEntry.Session>().map { it.workspaceId },
        )
        assertEquals(result.size, result.map { it.key }.toSet().size)
    }

    @Test
    fun workspaceMetaGatesRemoteRowsAndActions() {
        val local = drawerWorkspaceMeta(RuntimeWorkspace(id = "ws-1", name = "Work", cwd = "/work"))
        assertEquals(true, local.enabled)
        assertEquals(true, local.manageable)
        assertEquals(null, local.sourceDevice)

        val online = drawerWorkspaceMeta(remoteWorkspace(online = true))
        assertEquals(true, online.enabled)
        assertEquals(false, online.manageable)
        assertEquals("Mac", online.sourceDevice)

        val offline = drawerWorkspaceMeta(remoteWorkspace(online = false))
        assertEquals(false, offline.enabled)
        assertEquals(false, offline.manageable)
        assertEquals("Mac", offline.sourceDevice)
    }

    @Test
    fun newChatTargetSkipsOfflineRemoteWorkspace() {
        val local = RuntimeWorkspace(id = "home", name = "PI", cwd = "/home", isHome = true)
        val remoteOnline = remoteWorkspace(online = true)
        val remoteOffline = remoteWorkspace(online = false)

        assertEquals(
            local.id,
            drawerNewChatWorkspaceId(listOf(local, remoteOffline), remoteOffline.id),
        )
        assertEquals(
            remoteOnline.id,
            drawerNewChatWorkspaceId(listOf(local, remoteOnline), remoteOnline.id),
        )
        assertEquals(
            local.id,
            drawerNewChatWorkspaceId(listOf(remoteOffline, local), local.id),
        )
        assertEquals(null, drawerNewChatWorkspaceId(listOf(remoteOffline), remoteOffline.id))
        assertEquals(null, drawerNewChatWorkspaceId(emptyList(), "home"))
    }

    @Test
    fun uniqueKeysForAllEntries() {
        val result = entries((1..7).map { session("s$it", it.toLong()) })
        assertEquals(result.size, result.map { it.key }.toSet().size)
    }
}
