package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.plugin.api.PluginPermission

internal object DataPermissions {
    val location: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val contactsRead: Array<String> = arrayOf(Manifest.permission.READ_CONTACTS)

    val contactsWrite: Array<String> = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    val smsRead: Array<String> = arrayOf(Manifest.permission.READ_SMS)

    val smsSend: Array<String> = arrayOf(Manifest.permission.SEND_SMS)

    val calendarRead: Array<String> = arrayOf(Manifest.permission.READ_CALENDAR)

    val calendarWrite: Array<String> = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    val microphone: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun anyGranted(context: Context, permissions: Array<String>): Boolean =
        permissions.any { granted(context, it) }

    fun allGranted(context: Context, permissions: Array<String>): Boolean =
        permissions.all { granted(context, it) }

    fun locationGranted(context: Context): Boolean = anyGranted(context, location)

    fun requireGranted(context: Context, permissions: Array<String>, operation: String) {
        if (allGranted(context, permissions)) return
        val missing = permissions.filterNot { granted(context, it) }
        throw SecurityException("$operation requires ${missing.joinToString()} permission; grant it for DeepDeep and retry")
    }

    fun locationGrantedMessage(): String =
        "Location permission was not granted; grant location access for DeepDeep and retry"

    fun permissionWire(androidPermission: String): String? = when (androidPermission) {
        Manifest.permission.ACCESS_COARSE_LOCATION -> PluginPermission.LOCATION_COARSE
        Manifest.permission.ACCESS_FINE_LOCATION -> PluginPermission.LOCATION_FINE
        Manifest.permission.READ_CONTACTS -> PluginPermission.CONTACTS_READ
        Manifest.permission.WRITE_CONTACTS -> PluginPermission.CONTACTS_WRITE
        Manifest.permission.READ_SMS -> PluginPermission.SMS_READ
        Manifest.permission.SEND_SMS -> PluginPermission.SMS_SEND
        Manifest.permission.READ_CALENDAR -> PluginPermission.CALENDAR_READ
        Manifest.permission.WRITE_CALENDAR -> PluginPermission.CALENDAR_WRITE
        Manifest.permission.CAMERA -> PluginPermission.CAMERA
        Manifest.permission.RECORD_AUDIO -> PluginPermission.MICROPHONE
        Manifest.permission.POST_NOTIFICATIONS -> PluginPermission.NOTIFICATIONS_POST
        else -> null
    }

    fun androidPermissions(pluginPermission: String): List<String> = when (pluginPermission) {
        PluginPermission.LOCATION_COARSE -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        PluginPermission.LOCATION_FINE -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        PluginPermission.CONTACTS_READ -> listOf(Manifest.permission.READ_CONTACTS)
        PluginPermission.CONTACTS_WRITE -> listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )

        PluginPermission.SMS_READ -> listOf(Manifest.permission.READ_SMS)
        PluginPermission.SMS_SEND -> listOf(Manifest.permission.SEND_SMS)
        PluginPermission.CALENDAR_READ -> listOf(Manifest.permission.READ_CALENDAR)
        PluginPermission.CALENDAR_WRITE -> listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )

        PluginPermission.CAMERA -> listOf(Manifest.permission.CAMERA)
        PluginPermission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        PluginPermission.NOTIFICATIONS_POST -> listOf(Manifest.permission.POST_NOTIFICATIONS)
        else -> emptyList()
    }
}
