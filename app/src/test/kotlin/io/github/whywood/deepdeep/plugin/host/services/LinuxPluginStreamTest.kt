package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.LinuxChunk
import io.github.whywood.deepdeep.shell.LinuxResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinuxPluginStreamTest {
    @Test
    fun emitsLinesAndExitThenCompletes() = runTest {
        val chunks = linuxStream(
            execute = { onLine ->
                onLine("one")
                onLine("two")
                shellResult(exitCode = 0)
            },
        ).toList()

        assertEquals(
            listOf(LinuxChunk.Stdout("one"), LinuxChunk.Stdout("two"), LinuxChunk.Exit(0)),
            chunks,
        )
    }

    @Test
    fun reportsOverflowedLinesInsteadOfDroppingSilently() = runTest {
        val dropped = AtomicLong()
        val chunks = linuxStream(
            execute = { onLine ->
                repeat(100) { index -> onLine("line-$index") }
                shellResult(exitCode = 0)
            },
            onOutputDropped = { dropped.set(it) },
        ).toList()

        val stdout = chunks.filterIsInstance<LinuxChunk.Stdout>()
        assertTrue(dropped.get() > 0)
        assertEquals(100 - dropped.get(), stdout.size.toLong())
        assertEquals(LinuxChunk.Exit(0), chunks.last())
    }

    @Test
    fun executeFailureReachesCollector() = runTest {
        val flow = linuxStream(execute = { error("Linux environment is not installed") })

        val failure = runCatching { flow.toList() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("Linux environment is not installed", failure?.message)
    }

    private fun shellResult(exitCode: Int): LinuxResult = LinuxResult(
        exitCode = exitCode,
        output = "",
        truncated = false,
        timedOut = false,
        durationMillis = 1,
    )
}
