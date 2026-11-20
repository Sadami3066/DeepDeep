package io.github.whywood.superpi.ui

import io.github.whywood.superpi.data.relay.RelayBot
import io.github.whywood.superpi.data.relay.RelayDevice
import io.github.whywood.superpi.runtime.ConversationSummary
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
    ) = ConversationSummary(
        id = id,
        title = "Session $id",
        updatedAt = updatedAt,
        filePath = "/work/$id.jsonl",
        cwd = "/work",
        pinned = pinned,
        archived = archived,
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
    fun appendsRemoteDeviceGroupsWithTheirBots() {
        val devices = listOf(
            RelayDevice(
                deviceId = "desktop-1",
                name = "Mac",
                platform = "desktop",
                online = true,
                lastSeen = 0L,
                bots = listOf(RelayBot(id = "pi", name = "PI"), RelayBot(id = "work", name = "Work")),
            ),
            RelayDevice(
                deviceId = "tablet-1",
                name = "Tablet",
                platform = "android",
                online = false,
                lastSeen = 0L,
                bots = listOf(RelayBot(id = "home", name = "Home")),
            ),
            RelayDevice(
                deviceId = "empty-1",
                name = "Empty",
                platform = "desktop",
                online = true,
                lastSeen = 0L,
                bots = emptyList(),
            ),
        )

        val result = buildDrawerEntries(
            workspaces = listOf(workspace),
            sessionsByCwd = emptyMap(),
            expandedWorkspaceIds = emptySet(),
            expandedSessionWorkspaceIds = emptySet(),
            relayDevices = devices,
        )

        val remoteEntries = result.filter {
            it is DrawerEntry.RemoteDeviceEntry || it is DrawerEntry.RemoteBotEntry
        }
        assertEquals(
            listOf(
                "remote_device_desktop-1",
                "remote_bot_desktop-1_pi",
                "remote_bot_desktop-1_work",
                "remote_device_tablet-1",
                "remote_bot_tablet-1_home",
            ),
            remoteEntries.map { it.key },
        )
        assertEquals(
            result.size,
            result.map { it.key }.toSet().size,
        )
    }

    @Test
    fun noRemoteEntriesWithoutRemoteDevices() {
        val result = entries(emptyList())
        assertTrue(result.none { it is DrawerEntry.RemoteDeviceEntry || it is DrawerEntry.RemoteBotEntry })
    }

    @Test
    fun uniqueKeysForAllEntries() {
        val result = entries((1..7).map { session("s$it", it.toLong()) })
        assertEquals(result.size, result.map { it.key }.toSet().size)
    }
}
