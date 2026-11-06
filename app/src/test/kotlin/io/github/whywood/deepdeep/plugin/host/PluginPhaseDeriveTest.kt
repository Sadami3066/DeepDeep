package io.github.whywood.deepdeep.plugin.host

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginPhaseDeriveTest {
    private fun record(
        enabled: Boolean = true,
        installedVersion: String? = "1.0.0",
    ): PluginRecord = PluginRecord(id = "alpha", enabled = enabled, installedVersion = installedVersion)

    private fun facts(
        loaded: Boolean = true,
        failed: String? = null,
        degraded: String? = null,
    ): PluginRuntimeFacts = PluginRuntimeFacts(
        loaded = loaded,
        failed = failed,
        degraded = degraded,
    )

    @Test
    fun disabledPluginIsDisabledEvenWhenNotLoaded() {
        assertEquals(PluginPhase.DISABLED, PluginPhase.derive(record(enabled = false), facts(loaded = false)))
    }

    @Test
    fun enabledPluginWithoutInstalledVersionStaysInstalled() {
        assertEquals(PluginPhase.INSTALLED, PluginPhase.derive(record(installedVersion = null), facts(loaded = false)))
    }

    @Test
    fun enabledInstalledPluginThatWasNotActivatedYetIsNotFailed() {
        assertEquals(PluginPhase.INSTALLED, PluginPhase.derive(record(), facts(loaded = false)))
    }

    @Test
    fun loadedAvailablePluginIsActive() {
        assertEquals(PluginPhase.ACTIVE, PluginPhase.derive(record(), facts()))
    }

    @Test
    fun failedFactWinsOverNotLoadedTransientState() {
        assertEquals(PluginPhase.FAILED, PluginPhase.derive(record(), facts(loaded = false, failed = "boom")))
    }

    @Test
    fun unavailablePluginIsDegraded() {
        assertEquals(PluginPhase.DEGRADED, PluginPhase.derive(record(), facts(degraded = "offline")))
    }

    @Test
    fun failedWinsOverDegraded() {
        assertEquals(
            PluginPhase.FAILED,
            PluginPhase.derive(record(), facts(failed = "boom", degraded = "offline")),
        )
    }

    @Test
    fun contributesOnlyForActiveAndDegraded() {
        assertEquals(true, PluginPhase.ACTIVE.contributes)
        assertEquals(true, PluginPhase.DEGRADED.contributes)
        assertEquals(false, PluginPhase.INSTALLED.contributes)
        assertEquals(false, PluginPhase.FAILED.contributes)
        assertEquals(false, PluginPhase.DISABLED.contributes)
    }
}
