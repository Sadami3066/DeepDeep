package io.github.whywood.deepdeep.shell

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RootShellTest {
    @Test
    fun wrapsCommandsWithInnerKillTimeout() {
        assertEquals(
            "timeout -s KILL 2.0s sh -c 'sleep 300'",
            wrapRootCommand("sleep 300", 2_000),
        )
        assertEquals(
            "timeout -s KILL 15.0s sh -c 'echo '\\''a'\\'''",
            wrapRootCommand("echo 'a'", 15_000),
        )
    }

    private fun shell(result: RootProcessResult): RootShell =
        RootShell(RootCommandRunner { _, _ -> result })

    @Test
    fun probeReturnsGrantedForUidZero() = runBlocking {
        val shell = shell(RootProcessResult(0, "uid=0(root) gid=0(root) groups=0(root)\n", "", false, true))

        assertEquals(RootStatus.Granted, shell.probe())
    }

    @Test
    fun probeReturnsDeniedWhenUidIsNotRoot() = runBlocking {
        val shell = shell(RootProcessResult(0, "uid=2000(shell) gid=2000(shell)\n", "", false, true))

        assertEquals(RootStatus.Denied, shell.probe())
    }

    @Test
    fun probeReturnsDeniedOnNonZeroExit() = runBlocking {
        val shell = shell(RootProcessResult(1, "", "su: permission denied\n", false, true))

        assertEquals(RootStatus.Denied, shell.probe())
    }

    @Test
    fun probeReturnsUnsupportedWhenSuIsMissing() = runBlocking {
        val shell = shell(RootProcessResult(-1, "", "", false, false))

        assertEquals(RootStatus.Unsupported, shell.probe())
    }

    @Test
    fun probeReturnsTimeout() = runBlocking {
        val shell = shell(RootProcessResult(-1, "", "", true, true))

        assertEquals(RootStatus.Timeout, shell.probe())
    }

    @Test
    fun probeReturnsErrorWhenRunnerThrows() = runBlocking {
        val shell = RootShell(RootCommandRunner { _, _ -> throw IllegalStateException("boom") })

        assertEquals(RootStatus.Error, shell.probe())
    }

    @Test
    fun probeRunsIdWithGivenTimeout() = runBlocking {
        var seenCommand: String? = null
        var seenTimeout = 0L
        val shell = RootShell(RootCommandRunner { command, timeout ->
            seenCommand = command
            seenTimeout = timeout
            RootProcessResult(0, "uid=0(root)", "", false, true)
        })

        shell.probe(12_000)

        assertEquals("id", seenCommand)
        assertEquals(12_000L, seenTimeout)
    }

    @Test
    fun executeReturnsExitCodeAndSeparateStreams() = runBlocking {
        var seenCommand: String? = null
        var seenTimeout = 0L
        val shell = RootShell(RootCommandRunner { command, timeout ->
            seenCommand = command
            seenTimeout = timeout
            RootProcessResult(7, "out\n", "err\n", false, true)
        })

        val result = shell.execute("id /data", 5_000)

        assertEquals("id /data", seenCommand)
        assertEquals(5_000L, seenTimeout)
        assertEquals(7, result.exitCode)
        assertEquals("out\n", result.stdout)
        assertEquals("err\n", result.stderr)
        assertFalse(result.truncated)
    }

    @Test
    fun executeTimeoutReportsNegativeExitCode() = runBlocking {
        val shell = shell(RootProcessResult(-1, "partial", "", true, true))

        val result = shell.execute("sleep 60", 1_000)

        assertEquals(-1, result.exitCode)
        assertEquals("partial", result.stdout)
        assertFalse(result.truncated)
    }

    @Test
    fun executeTruncatesStdoutBeyondSixtyFourKilobytes() = runBlocking {
        val stdout = "a".repeat(70_000)
        val shell = shell(RootProcessResult(0, stdout, "", false, true))

        val result = shell.execute("cat", 1_000)

        assertTrue(result.truncated)
        assertEquals(64 * 1024, result.stdout.toByteArray(Charsets.UTF_8).size)
        assertEquals("", result.stderr)
    }

    @Test
    fun executeDropsStderrWhenStdoutFillsTheBudget() = runBlocking {
        val stdout = "a".repeat(64 * 1024)
        val shell = shell(RootProcessResult(0, stdout, "warning", false, true))

        val result = shell.execute("cat", 1_000)

        assertTrue(result.truncated)
        assertEquals(64 * 1024, result.stdout.toByteArray(Charsets.UTF_8).size)
        assertEquals("", result.stderr)
    }

    @Test
    fun executeDoesNotMarkOutputThatFitsTheBudget() = runBlocking {
        val stdout = "a".repeat(64 * 1024)
        val shell = shell(RootProcessResult(0, stdout, "", false, true))

        val result = shell.execute("cat", 1_000)

        assertFalse(result.truncated)
        assertEquals(64 * 1024, result.stdout.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun executeFailsWhenSuIsMissing() = runBlocking {
        val shell = shell(RootProcessResult(-1, "", "", false, false))

        try {
            shell.execute("id", 1_000)
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("su"))
        }
    }

    @Test
    fun executeRejectsBlankCommand() = runBlocking {
        val shell = shell(RootProcessResult(0, "", "", false, true))

        try {
            shell.execute("   ", 1_000)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
