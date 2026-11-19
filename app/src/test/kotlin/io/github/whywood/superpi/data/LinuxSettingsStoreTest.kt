package io.github.whywood.superpi.data

import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxSettingsStoreTest {
    @Test
    fun linuxIsEnabledByDefault() {
        assertTrue(linuxEnabledDefault())
    }
}
