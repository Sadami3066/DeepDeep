package io.github.whywood.deepdeep.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginVersionsTest {
    @Test
    fun comparesNumericSegments() {
        assertTrue(PluginVersions.isOlder("1.0.9", "1.0.98"))
        assertTrue(PluginVersions.isOlder("1.0.98", "1.1.0"))
        assertFalse(PluginVersions.isOlder("1.0.98", "1.0.98"))
        assertFalse(PluginVersions.isOlder("1.0.99", "1.0.98"))
    }
}
