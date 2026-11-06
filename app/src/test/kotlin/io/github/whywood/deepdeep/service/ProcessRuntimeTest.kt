package io.github.whywood.deepdeep.service

import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProcessRuntimeTest {
    private val steps = mutableListOf<String>()
    private val diagnosticsDir: java.io.File = Files.createTempDirectory("process-runtime").toFile()

    @Test
    fun startRunsStepsOnceInOrder() = runTest {
        val runtime = runtime()

        val jobs = List(2) { async { runtime.start() } }
        jobs.awaitAll()

        assertEquals(listOf("plugin", "prepare", "reconfigure", "ensure"), steps)
        assertEquals(ProcessState.STARTED, runtime.state.value)
    }

    @Test
    fun startAfterStartedDoesNothing() = runTest {
        val runtime = runtime()

        runtime.start()
        runtime.start()

        assertEquals(listOf("plugin", "prepare", "reconfigure", "ensure"), steps)
    }

    @Test
    fun startAsyncRunsTheSameSingleStart() = runTest {
        val runtime = runtime()

        runtime.startAsync()
        advanceUntilIdle()
        runtime.start()

        assertEquals(listOf("plugin", "prepare", "reconfigure", "ensure"), steps)
        assertEquals(ProcessState.STARTED, runtime.state.value)
    }

    @Test
    fun pluginFailureDoesNotBlockStartup() = runTest {
        val runtime = runtime(startPlugins = { steps += "plugin"; error("plugin boom") })

        runtime.start()

        assertEquals(listOf("plugin", "prepare", "reconfigure", "ensure"), steps)
        assertEquals(ProcessState.STARTED, runtime.state.value)
        assertTrue(java.io.File(diagnosticsDir, "errors.log").readText().contains("plugin start failed"))
    }

    @Test
    fun failedStartCanBeRetried() = runTest {
        var attempts = 0
        val runtime = runtime(
            prepare = {
                steps += "prepare"
                attempts += 1
                if (attempts == 1) error("prepare boom")
            },
        )

        try {
            runtime.start()
            fail("expected prepare failure")
        } catch (_: IllegalStateException) {
        }
        assertEquals(ProcessState.FAILED, runtime.state.value)

        runtime.start()
        assertEquals(ProcessState.STARTED, runtime.state.value)
        assertEquals(2, attempts)
        assertEquals(listOf("plugin", "prepare", "plugin", "prepare", "reconfigure", "ensure"), steps)
    }

    @Test
    fun onForegroundRefreshesPluginsAndSyncsSkills() = runTest {
        var foregrounded = 0
        var refreshedAfter = -1L
        var synced = 0
        val runtime = runtime(
            onForegroundPlugins = { foregrounded += 1 },
            refreshPluginsIfStale = { refreshedAfter = it },
            syncSkills = { synced += 1 },
        )

        runtime.onForeground()
        advanceUntilIdle()

        assertEquals(1, foregrounded)
        assertEquals(ProcessRuntime.PLUGIN_REFRESH_MIN_INTERVAL_MS, refreshedAfter)
        assertEquals(1, synced)
    }

    private fun kotlinx.coroutines.test.TestScope.runtime(
        startPlugins: suspend () -> Unit = { steps += "plugin" },
        prepare: suspend () -> Unit = { steps += "prepare" },
        onForegroundPlugins: () -> Unit = {},
        refreshPluginsIfStale: (Long) -> Unit = {},
        syncSkills: suspend () -> Unit = {},
    ) = ProcessRuntime(
        scope = this,
        prepare = prepare,
        startPlugins = startPlugins,
        onForegroundPlugins = onForegroundPlugins,
        refreshPluginsIfStale = refreshPluginsIfStale,
        reconfigureHost = { steps += "reconfigure" },
        ensureHostStarted = { steps += "ensure" },
        syncSkills = syncSkills,
        diagnosticsDir = diagnosticsDir,
    )
}
