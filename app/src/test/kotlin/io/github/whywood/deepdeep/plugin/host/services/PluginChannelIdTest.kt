package io.github.whywood.deepdeep.plugin.host.services

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginChannelIdTest {
    @Test
    fun channelIdKeepsFullPluginIdentity() {
        val sharedPrefix = "plugin-identifier-that-is-longer-than-24"
        val first = pluginChannelId(sharedPrefix + "-one", "default")
        val second = pluginChannelId(sharedPrefix + "-two", "default")

        assertNotEquals(first, second)
        assertTrue(first.contains(sharedPrefix + "-one"))
        assertTrue(second.contains(sharedPrefix + "-two"))
    }

    @Test
    fun channelIdSeparatesKinds() {
        assertNotEquals(
            pluginChannelId("demo", "default"),
            pluginChannelId("demo", "progress"),
        )
    }
}
