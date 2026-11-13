package io.github.whywood.deepdeep.plugin.api

public object PluginPermission {
    public const val LOCATION_COARSE: String = "location.coarse"
    public const val LOCATION_FINE: String = "location.fine"
    public const val CONTACTS_READ: String = "contacts.read"
    public const val CONTACTS_WRITE: String = "contacts.write"
    public const val SMS_READ: String = "sms.read"
    public const val SMS_SEND: String = "sms.send"
    public const val CALENDAR_READ: String = "calendar.read"
    public const val CALENDAR_WRITE: String = "calendar.write"
    public const val CAMERA: String = "camera"
    public const val MICROPHONE: String = "microphone"
    public const val NOTIFICATIONS_POST: String = "notifications.post"
    public const val NOTIFICATIONS_LISTEN: String = "notifications.listen"
    public const val ACCESSIBILITY: String = "accessibility"
    public const val OVERLAY: String = "overlay"
    public const val USAGE_STATS: String = "usage-stats"
    public const val BATTERY_OPTIMIZATION: String = "battery-optimization"
    public const val INSTALL_PACKAGES: String = "install-packages"
    public const val MANAGE_STORAGE: String = "manage-storage"

    public val ALL: List<String> = listOf(
        LOCATION_COARSE,
        LOCATION_FINE,
        CONTACTS_READ,
        CONTACTS_WRITE,
        SMS_READ,
        SMS_SEND,
        CALENDAR_READ,
        CALENDAR_WRITE,
        CAMERA,
        MICROPHONE,
        NOTIFICATIONS_POST,
        NOTIFICATIONS_LISTEN,
        ACCESSIBILITY,
        OVERLAY,
        USAGE_STATS,
        BATTERY_OPTIMIZATION,
        INSTALL_PACKAGES,
        MANAGE_STORAGE,
    )
}
