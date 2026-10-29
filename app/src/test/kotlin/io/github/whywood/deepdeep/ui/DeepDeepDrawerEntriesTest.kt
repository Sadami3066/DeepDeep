package io.github.whywood.deepdeep.ui

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDeepDrawerEntriesTest {
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
    fun uniqueKeysForAllEntries() {
        val result = entries((1..7).map { session("s$it", it.toLong()) })
        assertEquals(result.size, result.map { it.key }.toSet().size)
    }
}
