package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessUnavailableException
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.StreamingShellExecutor
import io.github.whywood.deepdeep.runtime.host.StreamingShellResult
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEnvironmentsTest {
    @Test
    fun localEnvironmentUsesSystemFilesAndNoSystemShell() {
        val prootShell = FakeShell()
        val environment = LocalHostEnvironment(
            prootBash = { prootShell },
            sharedStorageEnabled = { true },
        )

        assertEquals(HostAccess.LOCAL, environment.access)
        assertEquals(HostCapabilities(), environment.capabilities)
        assertSame(FileSystem.SYSTEM, environment.files)
        assertNull(environment.system)
        assertFalse(environment.capabilities.hasScreenControl)
        assertFalse(environment.capabilities.rootLinuxReady)
        assertSame(prootShell, environment.bash("/tmp"))
        assertEquals(1, environment.promptLines.size)
        assertTrue(environment.promptLines.single().contains("proot"))
    }

    @Test
    fun adbEnvironmentExposesAdbShell() {
        val prootShell = FakeShell()
        val adbShell = FakeShell()
        val environment = AdbHostEnvironment(
            prootBash = { prootShell },
            sharedStorageEnabled = { false },
            adbShell = adbShell,
        )

        assertEquals(HostAccess.ADB, environment.access)
        assertTrue(environment.capabilities.hasSystemShell)
        assertFalse(environment.capabilities.isRoot)
        assertFalse(environment.capabilities.canReadSystemData)
        assertTrue(environment.capabilities.hasScreenControl)
        assertSame(FileSystem.SYSTEM, environment.files)
        assertSame(adbShell, environment.system)
        assertSame(prootShell, environment.bash("/tmp"))
        assertEquals(2, environment.promptLines.size)
        assertTrue(environment.promptLines.last().contains("adb shell"))
    }

    @Test
    fun rootEnvironmentRejectsAccessWhilePreparing() {
        val environment = rootEnvironment(readiness = { RootBashReadiness.PREPARING })

        val bashError = assertThrows(HostAccessUnavailableException::class.java) {
            environment.bash("/")
        }
        assertEquals("Root Linux is being prepared", bashError.message)

        val filesError = assertThrows(HostAccessUnavailableException::class.java) {
            environment.files
        }
        assertEquals("Root Linux is being prepared", filesError.message)

        val systemError = assertThrows(HostAccessUnavailableException::class.java) {
            environment.system
        }
        assertEquals("Root Linux is being prepared", systemError.message)
    }

    @Test
    fun rootEnvironmentRejectsAccessAfterFailure() {
        val environment = rootEnvironment(readiness = { RootBashReadiness.FAILED })

        val error = assertThrows(HostAccessUnavailableException::class.java) {
            environment.bash("/")
        }
        assertEquals("Root Linux preparation failed", error.message)
    }

    @Test
    fun readyRootEnvironmentExposesRootCapabilities() {
        val rootShell = FakeShell()
        val rootFiles = FileSystem.SYSTEM
        val environment = RootHostEnvironment(
            chrootBash = { rootShell },
            rootFiles = rootFiles,
            rootShell = rootShell,
            sharedStorageEnabled = { true },
            readiness = { RootBashReadiness.READY },
        )

        assertEquals(HostAccess.ROOT, environment.access)
        assertEquals(
            HostCapabilities(
                isRoot = true,
                hasSystemShell = true,
                canReadSystemData = true,
                bashAvailable = true,
                hasScreenControl = true,
                rootLinuxReady = true,
            ),
            environment.capabilities,
        )
        assertSame(rootFiles, environment.files)
        assertSame(rootShell, environment.system)
        assertSame(rootShell, environment.bash("/"))
        assertEquals(7, environment.promptLines.size)
        assertTrue(environment.promptLines.any { it.contains("/data (rw)") })
        assertTrue(environment.promptLines.any { it.contains("root shell capability") })
    }

    @Test
    fun bashAvailabilityFollowsTheCurrentEnvironment() {
        var installed = false
        val local = LocalHostEnvironment(
            prootBash = { FakeShell() },
            sharedStorageEnabled = { false },
            bashAvailable = { installed },
        )
        assertFalse(local.capabilities.bashAvailable)
        installed = true
        assertTrue(local.capabilities.bashAvailable)

        val root = rootEnvironment(readiness = { RootBashReadiness.PREPARING })
        assertFalse(root.capabilities.bashAvailable)
        assertFalse(root.capabilities.rootLinuxReady)
        assertTrue(root.capabilities.hasScreenControl)
    }

    @Test
    fun sharedStorageFlagIsObservedDynamically() {
        var enabled = false
        val environment = LocalHostEnvironment(
            prootBash = { FakeShell() },
            sharedStorageEnabled = { enabled },
        )

        assertFalse(environment.sharedStorageEnabled)
        enabled = true
        assertTrue(environment.sharedStorageEnabled)
    }

    @Test
    fun localAndAdbEnvironmentsExposeStreamingShellOnlyWhenProvided() {
        val streaming = FakeStreamingShell()
        val local = LocalHostEnvironment(
            prootBash = { FakeShell() },
            sharedStorageEnabled = { false },
        )
        assertNull(local.bashStreaming("/tmp"))

        val streamingLocal = LocalHostEnvironment(
            prootBash = { FakeShell() },
            sharedStorageEnabled = { false },
            prootBashStreaming = { streaming },
        )
        assertSame(streaming, streamingLocal.bashStreaming("/tmp"))

        val adb = AdbHostEnvironment(
            prootBash = { FakeShell() },
            sharedStorageEnabled = { false },
            adbShell = FakeShell(),
            prootBashStreaming = { streaming },
        )
        assertSame(streaming, adb.bashStreaming("/tmp"))
    }

    @Test
    fun rootStreamingRequiresReadyState() {
        val streaming = FakeStreamingShell()
        val preparing = rootEnvironment(
            readiness = { RootBashReadiness.PREPARING },
            chrootStreaming = { streaming },
        )
        val error = assertThrows(HostAccessUnavailableException::class.java) {
            preparing.bashStreaming("/")
        }
        assertEquals("Root Linux is being prepared", error.message)

        val ready = rootEnvironment(
            readiness = { RootBashReadiness.READY },
            chrootStreaming = { streaming },
        )
        assertSame(streaming, ready.bashStreaming("/"))

        val defaultReady = rootEnvironment(readiness = { RootBashReadiness.READY })
        assertNull(defaultReady.bashStreaming("/"))
    }

    private fun rootEnvironment(
        readiness: () -> RootBashReadiness,
        chrootStreaming: (String) -> StreamingShellExecutor? = { null },
    ): RootHostEnvironment {
        val rootShell = FakeShell()
        return RootHostEnvironment(
            chrootBash = { rootShell },
            rootFiles = FileSystem.SYSTEM,
            rootShell = rootShell,
            sharedStorageEnabled = { true },
            readiness = readiness,
            chrootStreaming = chrootStreaming,
        )
    }

    private class FakeStreamingShell : StreamingShellExecutor {
        override suspend fun execute(
            command: String,
            timeoutMillis: Long,
            onOutputLine: (String) -> Unit,
        ): StreamingShellResult = StreamingShellResult(
            exitCode = 0,
            output = "",
            truncated = false,
            timedOut = false,
            durationMillis = 0,
        )
    }

    private class FakeShell : ShellExecutor {
        override suspend fun run(
            command: String,
            timeoutSeconds: Long?,
            signal: RunSignal?,
        ): ShellResult = error("unused")
    }
}
