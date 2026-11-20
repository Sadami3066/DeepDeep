package io.github.whywood.superpi.remote

import io.github.whywood.superpi.runtime.ModelConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteSessionEngineTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("remote-engine").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun titleForNormalizesWhitespace() {
        assertEquals("hello world", engine().titleFor("  hello\n\tworld  "))
    }

    @Test
    fun titleForTruncatesLongText() {
        val title = engine().titleFor("x".repeat(40))

        assertEquals(29, title.length)
        assertTrue(title.endsWith("…"))
        assertEquals("x".repeat(28), title.dropLast(1))
    }

    @Test
    fun titleForBlankTextIsDefault() {
        assertEquals("New chat", engine().titleFor("   "))
    }

    @Test
    fun loadMcpServersIsEmpty() = runTest {
        assertTrue(engine().loadMcpServers().isEmpty())
    }

    @Test
    fun createNewMirrorWithoutFileArgument() = runTest {
        val handle = engine().create(null, ModelConfig())

        assertTrue(handle.sessionId.isNotBlank())
        assertTrue(handle.sessionFile!!.endsWith(".jsonl"))
        assertTrue(handle.messages.isEmpty())
    }

    private fun engine(): RemoteSessionEngine {
        val transport = FakeRemoteTransport()
        val target = RemoteSessionTarget(
            deviceId = FakeRemoteTransport.DEVICE_ID,
            deviceName = "Desktop",
            botId = "pi",
            botName = "PI",
            isOnline = { true },
        )
        return RemoteSessionEngine(File(root, "remote/device/pi"), File(root, "agent"), target, transport)
    }
}
