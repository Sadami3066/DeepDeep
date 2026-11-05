package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.host.PluginPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRulesTest {
    private fun manifest(installable: Boolean) = io.github.whywood.deepdeep.plugin.api.PluginManifest(
        id = "test",
        version = "1.2.3",
        name = io.github.whywood.deepdeep.plugin.api.LocalizedText.Literal("Test"),
        summary = io.github.whywood.deepdeep.plugin.api.LocalizedText.Literal("Summary"),
        installable = installable,
    )

    @Test
    fun installablePluginsRequirePersistedInstallation() {
        assertFalse(PluginRules.installedVersion(manifest(installable = true), null, null) != null)
        assertEquals("1.0.0", PluginRules.installedVersion(manifest(installable = true), "1.0.0", true))
    }

    @Test
    fun builtinPluginsAreInstalledWithoutPersistedState() {
        assertEquals("1.2.3", PluginRules.installedVersion(manifest(installable = false), null, null))
        assertEquals("1.0.0", PluginRules.installedVersion(manifest(installable = false), "1.0.0", false))
    }

    @Test
    fun activeRequiresEnabledInstalledAvailableAndContributingPhase() {
        assertTrue(PluginRules.isActive(true, "1.0.0", PluginAvailabilityUi.Available, PluginPhase.ACTIVE))
        assertTrue(PluginRules.isActive(true, "1.0.0", PluginAvailabilityUi.Available, PluginPhase.PENDING_RESTART))
        assertFalse(PluginRules.isActive(false, "1.0.0", PluginAvailabilityUi.Available, PluginPhase.ACTIVE))
        assertFalse(PluginRules.isActive(true, null, PluginAvailabilityUi.Available, PluginPhase.ACTIVE))
        assertFalse(
            PluginRules.isActive(
                true,
                "1.0.0",
                PluginAvailabilityUi.Unavailable("no"),
                PluginPhase.ACTIVE,
            ),
        )
        assertFalse(PluginRules.isActive(true, "1.0.0", PluginAvailabilityUi.Available, PluginPhase.FAILED))
    }

    @Test
    fun updateAvailabilityNeedsVersionMismatchAndNoPendingRestart() {
        assertFalse(PluginRules.updateAvailable("1.0.0", null, null))
        assertFalse(PluginRules.updateAvailable("1.0.0", "1.0.0", null))
        assertFalse(PluginRules.updateAvailable("1.1.0", "1.0.0", "1.1.0"))
        assertTrue(PluginRules.updateAvailable("1.1.0", "1.0.0", null))
    }

    @Test
    fun phaseFollowsLifecycleRules() {
        assertEquals(
            PluginPhase.DISABLED,
            PluginRules.phase(false, "1.0.0", installable = false, PluginAvailabilityUi.Available, null),
        )
        assertEquals(
            PluginPhase.INSTALLED,
            PluginRules.phase(true, null, installable = true, PluginAvailabilityUi.Available, null),
        )
        assertEquals(
            PluginPhase.DEGRADED,
            PluginRules.phase(
                true,
                "1.0.0",
                installable = false,
                PluginAvailabilityUi.Unavailable("no"),
                null,
            ),
        )
        assertEquals(
            PluginPhase.FAILED,
            PluginRules.phase(
                true,
                "1.0.0",
                installable = false,
                PluginAvailabilityUi.Available,
                PluginStatusUi.Failed("bad"),
            ),
        )
        assertEquals(
            PluginPhase.ACTIVE,
            PluginRules.phase(
                true,
                "1.0.0",
                installable = false,
                PluginAvailabilityUi.Available,
                PluginStatusUi.Ready("ok"),
            ),
        )
    }

}
