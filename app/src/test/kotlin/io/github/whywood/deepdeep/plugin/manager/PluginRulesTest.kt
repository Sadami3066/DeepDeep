package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRulesTest {
    private fun manifest(installable: Boolean) = PluginManifest(
        id = "test",
        version = "1.2.3",
        name = LocalizedText.Literal("Test"),
        summary = LocalizedText.Literal("Summary"),
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
    fun updateAvailabilityNeedsVersionMismatchAndNoPendingRestart() {
        assertFalse(PluginRules.updateAvailable("1.0.0", null, null))
        assertFalse(PluginRules.updateAvailable("1.0.0", "1.0.0", null))
        assertFalse(PluginRules.updateAvailable("1.1.0", "1.0.0", "1.1.0"))
        assertTrue(PluginRules.updateAvailable("1.1.0", "1.0.0", null))
    }

    @Test
    fun installAndUninstallFollowInstallableFlag() {
        assertTrue(PluginRules.canInstall(manifest(installable = true)))
        assertTrue(PluginRules.canUninstall(manifest(installable = true)))
        assertFalse(PluginRules.canInstall(manifest(installable = false)))
        assertFalse(PluginRules.canUninstall(manifest(installable = false)))
    }

    @Test
    fun settingLeavesFlattenGroupsInDeclarationOrder() {
        val specs = listOf(
            SettingSpec.Toggle("top", LocalizedText.Literal("Top")),
            SettingSpec.Group(
                key = "group",
                title = LocalizedText.Literal("Group"),
                items = listOf(
                    SettingSpec.Text("leaf_a", LocalizedText.Literal("A")),
                    SettingSpec.Group(
                        key = "nested",
                        title = LocalizedText.Literal("Nested"),
                        items = listOf(SettingSpec.Number("leaf_b", LocalizedText.Literal("B"), 0, 5, 1)),
                    ),
                ),
            ),
        )

        assertEquals(listOf("top", "leaf_a", "leaf_b"), specs.settingLeaves().map { it.key })
    }
}
