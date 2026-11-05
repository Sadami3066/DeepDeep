package io.github.whywood.deepdeep.plugin.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRecoveryPolicyTest {
    @Test
    fun firstInterruptedActivationRequiresManualRetry() {
        assertEquals(RecoveryDecision.RetryRequired, PluginRecoveryPolicy.afterInterruptedActivation(1))
    }

    @Test
    fun repeatedInterruptedActivationAutoDisables() {
        val decision = PluginRecoveryPolicy.afterInterruptedActivation(PluginRecoveryPolicy.CRASH_LIMIT)
        assertTrue(decision is RecoveryDecision.AutoDisable)
        assertTrue((decision as RecoveryDecision.AutoDisable).interrupted)
    }

    @Test
    fun firstStartupKeepsPluginEnabled() {
        assertEquals(RecoveryDecision.Continue, PluginRecoveryPolicy.afterStartup(1))
    }

    @Test
    fun repeatedStartupCrashesAutoDisable() {
        val decision = PluginRecoveryPolicy.afterStartup(PluginRecoveryPolicy.CRASH_LIMIT)
        assertTrue(decision is RecoveryDecision.AutoDisable)
        assertEquals(false, (decision as RecoveryDecision.AutoDisable).interrupted)
    }
}
