package io.github.whywood.deepdeep.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxInstallPolicyTest {
    @Test
    fun installsWhenSupportedAndNotInstalled() {
        assertTrue(shouldAutoInstallLinux(supported = true, state = LinuxInstallState.NotInstalled))
    }

    @Test
    fun skipsWhileInstalling() {
        assertFalse(shouldAutoInstallLinux(supported = true, state = LinuxInstallState.Installing))
    }

    @Test
    fun skipsWhenInstalled() {
        val state = LinuxInstallState.Installed(version = "3.24.2", sizeBytes = 4096L)
        assertFalse(shouldAutoInstallLinux(supported = true, state = state))
    }

    @Test
    fun skipsWhenFailed() {
        assertFalse(shouldAutoInstallLinux(supported = true, state = LinuxInstallState.Failed("boom")))
    }

    @Test
    fun skipsWhenUnsupported() {
        assertFalse(shouldAutoInstallLinux(supported = false, state = LinuxInstallState.NotInstalled))
    }
}
