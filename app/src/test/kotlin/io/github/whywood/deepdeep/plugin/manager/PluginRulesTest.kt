package io.github.whywood.deepdeep.plugin.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRulesTest {
    @Test
    fun updateAvailabilityNeedsVersionMismatchAndNoPendingRestart() {
        assertFalse(PluginRules.updateAvailable("1.0.0", null, null))
        assertFalse(PluginRules.updateAvailable("1.0.0", "1.0.0", null))
        assertFalse(PluginRules.updateAvailable("1.1.0", "1.0.0", "1.1.0"))
        assertTrue(PluginRules.updateAvailable("1.1.0", "1.0.0", null))
    }
}
