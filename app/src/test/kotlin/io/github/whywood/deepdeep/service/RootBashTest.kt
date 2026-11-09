package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.shell.LinuxExecutor
import io.github.whywood.deepdeep.shell.LinuxResult
import io.github.whywood.deepdeep.shell.RootfsState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RootBashTest {
    @Test
    fun modeStaysProotWhenDisabledOrNotRootTier() {
        assertEquals(
            RootBashMode.PROOT,
            resolveRootBashMode(enabled = false, effective = PrivilegeLevel.ROOT, status = RootfsState.Ready),
        )
        assertEquals(
            RootBashMode.PROOT,
            resolveRootBashMode(enabled = true, effective = PrivilegeLevel.ADB, status = RootfsState.Ready),
        )
    }

    @Test
    fun modeFollowsRootfsStatusWhenActive() {
        assertEquals(
            RootBashMode.PREPARING,
            resolveRootBashMode(enabled = true, effective = PrivilegeLevel.ROOT, status = RootfsState.Idle),
        )
        assertEquals(
            RootBashMode.CHROOT,
            resolveRootBashMode(enabled = true, effective = PrivilegeLevel.ROOT, status = RootfsState.Ready),
        )
        assertEquals(
            RootBashMode.FAILED,
            resolveRootBashMode(
                enabled = true,
                effective = PrivilegeLevel.ROOT,
                status = RootfsState.Failed("no busybox"),
            ),
        )
    }

    @Test
    fun routingExecutorDelegatesToProotAndChroot() = runBlocking {
        var prootCalls = 0
        var chrootCalls = 0
        val proot = recorder { prootCalls++ }
        val chroot = recorder { chrootCalls++ }

        val router = RoutingLinuxExecutor(
            proot = proot,
            chroot = chroot,
            mode = { RootBashMode.PROOT },
            failureReason = { null },
        )
        router.execute("id", 1_000)
        assertEquals(1, prootCalls)
        assertEquals(0, chrootCalls)

        val rootRouter = RoutingLinuxExecutor(
            proot = proot,
            chroot = chroot,
            mode = { RootBashMode.CHROOT },
            failureReason = { null },
        )
        rootRouter.execute("id", 1_000)
        assertEquals(1, prootCalls)
        assertEquals(1, chrootCalls)
    }

    @Test
    fun routingExecutorReportsPreparationAndFailure() = runBlocking {
        val executor = recorder {}
        try {
            RoutingLinuxExecutor(
                proot = executor,
                chroot = executor,
                mode = { RootBashMode.PREPARING },
                failureReason = { null },
            ).execute("id", 1_000)
            fail("expected preparation error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("being prepared"))
        }

        try {
            RoutingLinuxExecutor(
                proot = executor,
                chroot = executor,
                mode = { RootBashMode.FAILED },
                failureReason = { "no busybox" },
            ).execute("id", 1_000)
            fail("expected failure error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("no busybox"))
        }
    }

    private fun recorder(onExecute: () -> Unit): LinuxExecutor = object : LinuxExecutor {
        override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult {
            onExecute()
            return LinuxResult(0, command, false, false, 1)
        }
    }
}
