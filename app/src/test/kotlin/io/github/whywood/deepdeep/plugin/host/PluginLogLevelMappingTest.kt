package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.PluginLogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginLogLevelMappingTest {
    @Test
    fun mapsAndroidLogLevelsToPluginLogLevels() {
        assertEquals(PluginLogLevel.DEBUG, androidLogLevelToPluginLogLevel(2))
        assertEquals(PluginLogLevel.DEBUG, androidLogLevelToPluginLogLevel(3))
        assertEquals(PluginLogLevel.INFO, androidLogLevelToPluginLogLevel(4))
        assertEquals(PluginLogLevel.WARN, androidLogLevelToPluginLogLevel(5))
        assertEquals(PluginLogLevel.ERROR, androidLogLevelToPluginLogLevel(6))
        assertEquals(PluginLogLevel.ERROR, androidLogLevelToPluginLogLevel(7))
        assertEquals(PluginLogLevel.DEBUG, androidLogLevelToPluginLogLevel(0))
    }
}
