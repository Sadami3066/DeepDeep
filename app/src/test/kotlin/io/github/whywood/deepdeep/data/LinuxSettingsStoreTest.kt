package io.github.whywood.deepdeep.data

import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxSettingsStoreTest {
    @Test
    fun linuxIsEnabledByDefault() {
        assertTrue(linuxEnabledDefault())
    }
}
