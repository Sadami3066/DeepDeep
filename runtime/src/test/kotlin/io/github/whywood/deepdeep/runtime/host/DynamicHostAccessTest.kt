package io.github.whywood.deepdeep.runtime.host

import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutionException
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import okio.ForwardingFileSystem
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicHostAccessTest {
    @Test
    fun shellExecutorResolvesTheCurrentEnvironmentOnEveryCall() = kotlinx.coroutines.test.runTest {
        var current: ShellExecutor = FakeShell("local")
        val executor = DynamicShellExecutor { current }
        assertEquals("local", executor.run("id", null, null).output)

        current = FakeShell("root")
        assertEquals("root", executor.runDetailed("id", null, null).output)
    }

    @Test
    fun shellExecutorFailsWhenNoEnvironmentIsAvailable() {
        val executor = DynamicShellExecutor { null }
        val error = assertThrows(ShellExecutionException::class.java) {
            kotlinx.coroutines.runBlocking { executor.run("id", null, null) }
        }
        assertTrue(error.message!!.contains("not available"))
    }

    @Test
    fun fileSystemResolvesTheCurrentEnvironmentOnEveryOperation() {
        val local = RecordingFileSystem(FileSystem.SYSTEM)
        val root = RecordingFileSystem(FileSystem.SYSTEM)
        var current: FileSystem = local
        val files = DynamicFileSystem { current }
        val path = "/tmp/deepdeep-dynamic-fs-test".toPath()

        files.metadataOrNull(path)
        files.listOrNull(path)

        current = root
        files.metadataOrNull(path)

        assertEquals(2, local.calls)
        assertEquals(1, root.calls)
    }

    private class FakeShell(private val output: String) : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: io.github.whywood.deepdeep.sdk.ai.RunSignal?): ShellResult =
            ShellResult(output, 0, false)
    }

    private class RecordingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
        var calls = 0
            private set

        override fun metadataOrNull(path: okio.Path): okio.FileMetadata? {
            calls += 1
            return super.metadataOrNull(path)
        }

        override fun listOrNull(dir: okio.Path): List<okio.Path>? {
            calls += 1
            return super.listOrNull(dir)
        }
    }
}
