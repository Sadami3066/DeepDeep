package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRecoverySectionTest {
    @Test
    fun showsRecoveryOnlyForFailedOrDegradedWithError() {
        assertTrue(showsRecoverySection(PluginPhaseUi.FAILED, "boom"))
        assertTrue(showsRecoverySection(PluginPhaseUi.DEGRADED, "boom"))
        assertFalse(showsRecoverySection(PluginPhaseUi.FAILED, null))
        assertFalse(showsRecoverySection(PluginPhaseUi.ACTIVE, "boom"))
        assertFalse(showsRecoverySection(PluginPhaseUi.DISABLED, "boom"))
        assertFalse(showsRecoverySection(PluginPhaseUi.PENDING_RESTART, "boom"))
    }

    @Test
    fun retryActivationIsOnlyOfferedForExternalPlugins() {
        assertTrue(supportsActivationRecovery(PluginSource.EXTERNAL))
        assertFalse(supportsActivationRecovery(PluginSource.BUILTIN))
    }

    @Test
    fun rollbackTargetRequiresExternalPluginWithPreviousVersion() {
        assertEquals("1.0.0", rollbackTargetVersion(PluginSource.EXTERNAL, "1.0.0"))
        assertNull(rollbackTargetVersion(PluginSource.EXTERNAL, null))
        assertNull(rollbackTargetVersion(PluginSource.BUILTIN, "1.0.0"))
    }
}
