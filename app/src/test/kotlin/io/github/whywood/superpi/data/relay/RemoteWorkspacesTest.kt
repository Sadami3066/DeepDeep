package io.github.whywood.superpi.data.relay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWorkspacesTest {
    private val bot = RemoteBot(
        deviceId = "dev-1",
        deviceName = "Desktop",
        botId = "pi main",
        botName = "PI",
        online = true,
    )

    @Test
    fun buildsStableWorkspaceIdentity() {
        assertEquals("remote:dev-1:pi main", RemoteWorkspaces.workspaceId(bot))
        assertEquals(RemoteWorkspaces.workspaceId(bot), RemoteWorkspaces.workspaceId(bot.copy(online = false)))
    }

    @Test
    fun buildsWorkspaceWithRemoteSource() {
        val workspace = RemoteWorkspaces.workspace(File("/data/app/files/remote"), bot)

        assertEquals("PI", workspace.name)
        assertEquals("dev-1", workspace.remote?.deviceId)
        assertEquals("Desktop", workspace.remote?.deviceName)
        assertEquals("pi main", workspace.remote?.botId)
        assertTrue(workspace.remote?.online == true)
        assertTrue(workspace.cwd.startsWith(File("/data/app/files/remote").absolutePath))
        assertNull(workspace.avatar)
    }

    @Test
    fun sanitizesDeviceAndBotSegments() {
        val workspace = RemoteWorkspaces.workspace(
            File("/root"),
            bot.copy(deviceId = "dev/../1", botId = "a b"),
        )

        val root = File("/root").absolutePath + File.separator
        assertTrue(workspace.cwd.startsWith(root))
        assertTrue(!workspace.cwd.substringAfter(root).contains(".."))
    }

    @Test
    fun mapsAllBotsInOrder() {
        val second = bot.copy(botId = "work", botName = "Work")
        val workspaces = RemoteWorkspaces.workspaces(File("/root"), listOf(bot, second))

        assertEquals(listOf("PI", "Work"), workspaces.map { it.name })
    }
}
