package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
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
        assertEquals(HostCapabilities(isRoot = true, hasSystemShell = true, canReadSystemData = true), environment.capabilities)
        assertSame(rootFiles, environment.files)
        assertSame(rootShell, environment.system)
        assertSame(rootShell, environment.bash("/"))
        assertEquals(7, environment.promptLines.size)
        assertTrue(environment.promptLines.any { it.contains("/data (rw)") })
        assertTrue(environment.promptLines.any { it.contains("root shell capability") })
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

    private fun rootEnvironment(
        readiness: () -> RootBashReadiness,
    ): RootHostEnvironment {
        val rootShell = FakeShell()
        return RootHostEnvironment(
            chrootBash = { rootShell },
            rootFiles = FileSystem.SYSTEM,
            rootShell = rootShell,
            sharedStorageEnabled = { true },
            readiness = readiness,
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
