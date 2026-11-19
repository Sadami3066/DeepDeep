package io.github.whywood.superpi.runtime

const val MINIAPP_BRIDGE_VERSION = 2

val MINIAPP_CAPABILITIES: List<String> = listOf(
    "env",
    "notify.show",
    "notify.cancel",
    "notify.cancelAll",
    "notify.schedule",
    "notify.list",
    "files.pick",
    "files.read",
    "files.write",
    "files.export",
    "files.url",
    "files.list",
    "files.delete",
    "share.text",
    "share.file",
    "clipboard.read",
    "clipboard.write",
    "ui.haptic",
    "ui.keepScreenOn",
    "media.pick",
    "media.capture",
    "media.saveToGallery",
    "media.scanQr",
    "speech.speak",
    "speech.stop",
    "speech.listen",
    "system.openUrl",
    "location.current",
    "calendar.list",
    "calendar.add",
)

class MiniappHostException(val code: String, message: String) : Exception(message) {
    companion object {
        const val UNAVAILABLE = "unavailable"
        const val PERMISSION_DENIED = "permission_denied"
        const val NOT_FOUND = "not_found"
        const val INVALID_PARAMS = "invalid_params"
        const val TOO_LARGE = "too_large"
        const val FAILED = "failed"
        const val CANCELLED = "cancelled"
    }
}

data class MiniappSafeArea(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

data class MiniappEnv(
    val locale: String,
    val fontScale: Float,
    val safeArea: MiniappSafeArea,
)

data class MiniappNotificationRequest(
    val id: String?,
    val title: String,
    val body: String,
)

data class MiniappReminderRequest(
    val id: String?,
    val title: String,
    val body: String,
    val atMillis: Long,
    val repeatDaily: Boolean,
)

data class MiniappReminder(
    val id: String,
    val title: String,
    val body: String,
    val atMillis: Long,
    val repeatDaily: Boolean,
)

data class MiniappFileRef(
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
)

data class MiniappFileEntry(
    val name: String,
    val path: String,
    val size: Long,
    val directory: Boolean,
)

data class MiniappFileRead(
    val path: String,
    val size: Long,
    val mimeType: String,
    val text: String?,
    val base64: String?,
)

data class MiniappLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val provider: String,
    val timeMillis: Long,
)

data class MiniappCalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val description: String?,
    val location: String?,
)

data class MiniappCalendarDraft(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val description: String?,
    val location: String?,
)

interface MiniappHostGateway {
    suspend fun env(): MiniappEnv

    suspend fun showNotification(request: MiniappNotificationRequest): String

    suspend fun cancelNotification(id: String): Boolean

    suspend fun cancelNotifications(): Boolean

    suspend fun scheduleReminder(request: MiniappReminderRequest): MiniappReminder

    suspend fun cancelReminder(id: String): Boolean

    suspend fun reminders(): List<MiniappReminder>

    suspend fun pickFiles(types: List<String>, multiple: Boolean): List<MiniappFileRef>

    suspend fun readFile(path: String, encoding: String): MiniappFileRead

    suspend fun writeFile(path: String, encoding: String, data: String): MiniappFileRef

    suspend fun exportFile(path: String): Boolean

    suspend fun deleteFile(path: String): Boolean

    suspend fun listFiles(directory: String): List<MiniappFileEntry>

    suspend fun fileUrl(path: String): String

    suspend fun shareText(text: String): Boolean

    suspend fun shareFile(path: String, mimeType: String?): Boolean

    suspend fun readClipboard(): String?

    suspend fun writeClipboard(text: String): Boolean

    suspend fun haptic(intensity: String): Boolean

    suspend fun keepScreenOn(enabled: Boolean): Boolean

    suspend fun pickMedia(type: String, multiple: Boolean): List<MiniappFileRef>

    suspend fun captureMedia(): MiniappFileRef?

    suspend fun saveToGallery(path: String): Boolean

    suspend fun scanQr(): String?

    suspend fun speak(text: String, language: String?): Boolean

    suspend fun stopSpeaking(): Boolean

    suspend fun listen(timeoutMillis: Long): String

    suspend fun openUrl(url: String): Boolean

    suspend fun location(): MiniappLocation

    suspend fun calendarEvents(fromMillis: Long, toMillis: Long, limit: Int): List<MiniappCalendarEvent>

    suspend fun addCalendarEvent(draft: MiniappCalendarDraft): MiniappCalendarEvent?
}
