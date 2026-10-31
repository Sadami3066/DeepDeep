package io.github.whywood.deepdeep.privilege

import io.github.whywood.deepdeep.shell.RootStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootProbePolicyTest {
    @Test
    fun coldStartProbesRootOnlyForPrivilegedSelections() {
        assertFalse(shouldProbeRootOnStartup(PrivilegeLevel.NORMAL))
        assertTrue(shouldProbeRootOnStartup(PrivilegeLevel.ADB))
        assertTrue(shouldProbeRootOnStartup(PrivilegeLevel.ROOT))
    }

    @Test
    fun automaticProbeSkipsDeniedAndUnsupported() {
        assertFalse(shouldAutoProbeRoot(RootStatus.Denied))
        assertFalse(shouldAutoProbeRoot(RootStatus.Unsupported))
    }

    @Test
    fun automaticProbeRetriesUndecidedAndFailedStates() {
        assertTrue(shouldAutoProbeRoot(RootStatus.NotRequested))
        assertTrue(shouldAutoProbeRoot(RootStatus.Granted))
        assertTrue(shouldAutoProbeRoot(RootStatus.Timeout))
        assertTrue(shouldAutoProbeRoot(RootStatus.Error))
    }
}
