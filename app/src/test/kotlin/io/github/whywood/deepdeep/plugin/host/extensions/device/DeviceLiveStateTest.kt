package io.github.whywood.deepdeep.plugin.host.extensions.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLiveStateTest {
    @Test
    fun reducesBatteryLevelIntoPercent() {
        val base = DeviceLiveSnapshot()
        val charged = reduceDeviceLive(base, DeviceLiveEvent.Battery(level = 50, scale = 100, charging = true))
        assertEquals(50, charged.batteryPercent)
        assertTrue(charged.charging)

        val low = reduceDeviceLive(charged, DeviceLiveEvent.Battery(level = 1, scale = 8, charging = false))
        assertEquals(12, low.batteryPercent)
        assertFalse(low.charging)
    }

    @Test
    fun keepsPreviousBatteryWhenReadingIsInvalid() {
        val base = DeviceLiveSnapshot(batteryPercent = 42, charging = true)
        assertEquals(42, reduceDeviceLive(base, DeviceLiveEvent.Battery(-1, 100, true)).batteryPercent)
        assertEquals(42, reduceDeviceLive(base, DeviceLiveEvent.Battery(50, 0, true)).batteryPercent)
    }

    @Test
    fun tracksPowerSaveNetworkScreenAndLock() {
        val network = reduceDeviceLive(DeviceLiveSnapshot(), DeviceLiveEvent.Network("wifi", true))
        assertEquals("wifi", network.networkType)
        assertTrue(network.connected)

        val unknown = reduceDeviceLive(network, DeviceLiveEvent.Network("  ", true))
        assertEquals(DEVICE_NETWORK_NONE, unknown.networkType)

        val screenOff = reduceDeviceLive(network, DeviceLiveEvent.ScreenOn(false))
        assertFalse(screenOff.screenOn)
        assertTrue(screenOff.locked)

        val unlocked = reduceDeviceLive(screenOff, DeviceLiveEvent.Locked(false))
        assertFalse(unlocked.locked)

        val powerSave = reduceDeviceLive(unlocked, DeviceLiveEvent.PowerSave(true))
        assertTrue(powerSave.powerSave)
    }

    @Test
    fun computesBatteryPercentWithinBounds() {
        assertEquals(0, batteryPercentOf(0, 100) ?: -1)
        assertEquals(100, batteryPercentOf(100, 100) ?: -1)
        assertEquals(100, batteryPercentOf(200, 100) ?: -1)
        assertNull(batteryPercentOf(-1, 100))
        assertNull(batteryPercentOf(50, -1))
    }
}
