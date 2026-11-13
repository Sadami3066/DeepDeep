package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.Manifest
import io.github.whywood.deepdeep.plugin.api.PluginPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPermissionsTest {
    @Test
    fun mapsAndroidPermissionsToPluginWire() {
        assertEquals(PluginPermission.LOCATION_FINE, DataPermissions.permissionWire(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PluginPermission.LOCATION_COARSE, DataPermissions.permissionWire(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(PluginPermission.CONTACTS_READ, DataPermissions.permissionWire(Manifest.permission.READ_CONTACTS))
        assertEquals(PluginPermission.CONTACTS_WRITE, DataPermissions.permissionWire(Manifest.permission.WRITE_CONTACTS))
        assertEquals(PluginPermission.SMS_READ, DataPermissions.permissionWire(Manifest.permission.READ_SMS))
        assertEquals(PluginPermission.SMS_SEND, DataPermissions.permissionWire(Manifest.permission.SEND_SMS))
        assertEquals(PluginPermission.CALENDAR_READ, DataPermissions.permissionWire(Manifest.permission.READ_CALENDAR))
        assertEquals(PluginPermission.CALENDAR_WRITE, DataPermissions.permissionWire(Manifest.permission.WRITE_CALENDAR))
        assertEquals(PluginPermission.MICROPHONE, DataPermissions.permissionWire(Manifest.permission.RECORD_AUDIO))
        assertEquals(PluginPermission.CAMERA, DataPermissions.permissionWire(Manifest.permission.CAMERA))
        assertNull(DataPermissions.permissionWire("com.example.unknown"))
    }

    @Test
    fun mapsPluginWireToAndroidPermissions() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            DataPermissions.androidPermissions(PluginPermission.LOCATION_FINE),
        )
        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            DataPermissions.androidPermissions(PluginPermission.CONTACTS_WRITE),
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            DataPermissions.androidPermissions(PluginPermission.CALENDAR_WRITE),
        )
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            DataPermissions.androidPermissions(PluginPermission.NOTIFICATIONS_POST),
        )
    }

    @Test
    fun roundTripsSupportedPermissions() {
        val supported = listOf(
            PluginPermission.LOCATION_COARSE,
            PluginPermission.CONTACTS_READ,
            PluginPermission.SMS_SEND,
            PluginPermission.CALENDAR_READ,
            PluginPermission.MICROPHONE,
        )
        supported.forEach { wire ->
            val android = DataPermissions.androidPermissions(wire)
            assertTrue("missing mapping for $wire", android.isNotEmpty())
            assertTrue(
                "round trip failed for $wire",
                android.any { DataPermissions.permissionWire(it) != null },
            )
        }
    }

    @Test
    fun unknownPluginPermissionHasNoAndroidPermission() {
        assertEquals(emptyList<String>(), DataPermissions.androidPermissions("teleport"))
    }

    @Test
    fun permissionGroupsCoverExpectedManifestPermissions() {
        assertTrue(DataPermissions.location.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(DataPermissions.contactsWrite.contains(Manifest.permission.WRITE_CONTACTS))
        assertTrue(DataPermissions.smsSend.contains(Manifest.permission.SEND_SMS))
        assertTrue(DataPermissions.calendarRead.contains(Manifest.permission.READ_CALENDAR))
    }
}
