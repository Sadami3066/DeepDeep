package io.github.whywood.deepdeep.vscreen

import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.virtualscreen.virtualScreenAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenPluginTest {
    @Test
    fun availabilityFollowsBackendAvailability() {
        val reason = "需要无线调试（ADB）或 Root 权限"
        val unavailable = virtualScreenAvailability(available = false, unavailableReason = reason)
        assertTrue(unavailable is PluginAvailability.Unavailable)
        assertEquals(reason, (unavailable as PluginAvailability.Unavailable).reason)
        assertTrue(virtualScreenAvailability(available = true, unavailableReason = reason) is PluginAvailability.Available)
    }
}
