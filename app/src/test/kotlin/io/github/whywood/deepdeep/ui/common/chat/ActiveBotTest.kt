package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveBotTest {
    private val home = RuntimeWorkspace(id = "home", name = "DeepDeep", cwd = "/home", isHome = true)
    private val writer = RuntimeWorkspace(id = "writer", name = "Writer", cwd = "/writer", avatar = "/avatars/bot.jpg")
    private val coder = RuntimeWorkspace(id = "coder", name = "Coder", cwd = "/coder")

    @Test
    fun homeWorkspaceHasNoBotIdentity() {
        assertNull(activeBotFor(listOf(home, writer), "home", "/home"))
    }

    @Test
    fun matchingWorkspaceAndCwdReturnsBot() {
        assertEquals(writer, activeBotFor(listOf(home, writer, coder), "writer", "/writer"))
    }

    @Test
    fun cwdMismatchReturnsNull() {
        assertNull(activeBotFor(listOf(writer), "writer", "/elsewhere"))
    }

    @Test
    fun unknownWorkspaceReturnsNull() {
        assertNull(activeBotFor(listOf(writer, coder), "missing", "/writer"))
    }
}
