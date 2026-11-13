package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PluginPermission
import io.github.whywood.deepdeep.plugin.api.PluginSpecialAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPermissionMappingTest {
    @Test
    fun mapsEveryPermissionWireToExactlyOneTarget() {
        PluginPermission.ALL.forEach { wire ->
            val androidPermission = PluginPermissionMapping.androidPermission(wire)
            val specialAccess = PluginPermissionMapping.specialAccess(wire)
            val classifications = listOfNotNull(androidPermission, specialAccess)
            assertEquals("wire $wire must map to one target", 1, classifications.size)
        }
    }

    @Test
    fun mapsRuntimePermissionsToAndroidPermissions() {
        assertEquals("android.permission.CAMERA", PluginPermissionMapping.androidPermission(PluginPermission.CAMERA))
        assertEquals(
            "android.permission.RECORD_AUDIO",
            PluginPermissionMapping.androidPermission(PluginPermission.MICROPHONE),
        )
        assertEquals(
            "android.permission.ACCESS_FINE_LOCATION",
            PluginPermissionMapping.androidPermission(PluginPermission.LOCATION_FINE),
        )
        assertEquals(
            "android.permission.POST_NOTIFICATIONS",
            PluginPermissionMapping.androidPermission(PluginPermission.NOTIFICATIONS_POST),
        )
        assertEquals(
            "android.permission.READ_SMS",
            PluginPermissionMapping.androidPermission(PluginPermission.SMS_READ),
        )
        assertEquals(
            "android.permission.WRITE_CONTACTS",
            PluginPermissionMapping.androidPermission(PluginPermission.CONTACTS_WRITE),
        )
    }

    @Test
    fun mapsSpecialPermissionsToSpecialAccess() {
        assertEquals(
            PluginSpecialAccess.NOTIFICATION_LISTENER,
            PluginPermissionMapping.specialAccess(PluginPermission.NOTIFICATIONS_LISTEN),
        )
        assertEquals(
            PluginSpecialAccess.USAGE_STATS,
            PluginPermissionMapping.specialAccess(PluginPermission.USAGE_STATS),
        )
        assertEquals(
            PluginSpecialAccess.MANAGE_STORAGE,
            PluginPermissionMapping.specialAccess(PluginPermission.MANAGE_STORAGE),
        )
        assertEquals(
            PluginSpecialAccess.INSTALL_PACKAGES,
            PluginPermissionMapping.specialAccess(PluginPermission.INSTALL_PACKAGES),
        )
    }

    @Test
    fun acceptsRawAndroidPermissionsAndTrimsInput() {
        assertEquals(
            "android.permission.CAMERA",
            PluginPermissionMapping.androidPermission(" android.permission.CAMERA "),
        )
        assertEquals(
            "android.permission.CAMERA",
            PluginPermissionMapping.androidPermission(" camera "),
        )
        assertNull(PluginPermissionMapping.androidPermission("unknown.permission"))
        assertNull(PluginPermissionMapping.specialAccess("unknown.permission"))
    }

    @Test
    fun keepsRuntimeAndSpecialMappingsDisjoint() {
        val runtimeKeys = PluginPermissionMapping.runtimeMappings().keys
        val specialKeys = PluginPermissionMapping.specialMappings().keys
        assertTrue(runtimeKeys.intersect(specialKeys).isEmpty())
        assertEquals(PluginPermission.ALL.size, runtimeKeys.size + specialKeys.size)
        assertNotNull(PluginSpecialAccess.ALL.first())
    }
}
