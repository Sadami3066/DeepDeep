package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

public interface PluginNotificationAction {
    public val id: String
    public val label: String
}

public fun notificationAction(id: String, label: String): PluginNotificationAction =
    PluginNotificationActionImpl(id, label)

internal data class PluginNotificationActionImpl(
    override val id: String,
    override val label: String,
) : PluginNotificationAction

public data class PluginNotificationActionInfo(
    public val id: String,
    public val title: String?,
)

public data class PluginNotificationRecord(
    public val key: String,
    public val packageName: String,
    public val appLabel: String?,
    public val title: String?,
    public val text: String?,
    public val bigText: String?,
    public val subText: String?,
    public val channelId: String?,
    public val category: String?,
    public val postedAtMs: Long,
    public val ongoing: Boolean,
    public val clearable: Boolean,
    public val progress: Int? = null,
    public val actions: List<PluginNotificationActionInfo> = emptyList(),
)

public data class PluginNotificationHandle(
    public val id: String,
    public val key: String,
)

public interface PluginSettingsService {
    public suspend fun get(key: String): String?

    public suspend fun put(key: String, value: String)

    public suspend fun remove(key: String)

    public suspend fun clear()

    public suspend fun keys(): Set<String>

    public suspend fun snapshot(): Map<String, String>

    public suspend fun getJson(key: String): JsonObject?

    public suspend fun putJson(key: String, value: JsonObject)

    public fun observe(key: String? = null): Flow<Map<String, String>>
}

public data class PluginLocation(
    public val latitude: Double,
    public val longitude: Double,
    public val accuracyM: Float,
    public val provider: String,
    public val timeMs: Long,
    public val altitudeM: Double? = null,
    public val speedMps: Float? = null,
    public val bearingDeg: Float? = null,
)

public data class PluginGeocode(
    public val latitude: Double,
    public val longitude: Double,
    public val label: String? = null,
    public val country: String? = null,
    public val adminArea: String? = null,
    public val locality: String? = null,
)

public interface LocationService {
    public val available: Boolean

    public val permissionGranted: Boolean

    public suspend fun lastKnown(): PluginLocation?

    public suspend fun current(timeoutMs: Long = DEFAULT_TIMEOUT_MS): PluginLocation?

    public fun updates(minIntervalMs: Long = 1_000, minDistanceM: Float = 0f): Flow<PluginLocation>

    public suspend fun geocode(query: String, limit: Int = 5): List<PluginGeocode>

    public suspend fun reverseGeocode(location: PluginLocation): PluginGeocode?

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 10_000
    }
}

public data class PluginContactField(
    public val value: String,
    public val label: String? = null,
    public val type: String? = null,
)

public data class PluginContact(
    public val id: String,
    public val displayName: String,
    public val phones: List<PluginContactField> = emptyList(),
    public val emails: List<PluginContactField> = emptyList(),
    public val organization: String? = null,
    public val note: String? = null,
    public val starred: Boolean = false,
)

public interface PluginContactDraft {
    public val displayName: String
    public val phones: List<String>
    public val emails: List<String>
    public val organization: String?
}

public fun pluginContactDraft(
    displayName: String,
    phones: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    organization: String? = null,
): PluginContactDraft = PluginContactDraftImpl(displayName, phones, emails, organization)

internal data class PluginContactDraftImpl(
    override val displayName: String,
    override val phones: List<String>,
    override val emails: List<String>,
    override val organization: String?,
) : PluginContactDraft

public interface ContactsService {
    public val permissionGranted: Boolean

    public suspend fun list(limit: Int = 0): List<PluginContact>

    public suspend fun search(query: String, limit: Int = 20): List<PluginContact>

    public suspend fun get(id: String): PluginContact?

    public suspend fun create(draft: PluginContactDraft): PluginContact?

    public suspend fun update(id: String, draft: PluginContactDraft): PluginContact?

    public suspend fun delete(id: String): Boolean
}

public data class PluginSmsThread(
    public val id: String,
    public val address: String,
    public val contactName: String? = null,
    public val snippet: String? = null,
    public val dateMs: Long = 0,
    public val unreadCount: Int = 0,
)

public data class PluginSmsMessage(
    public val id: String,
    public val threadId: String,
    public val address: String,
    public val body: String,
    public val dateMs: Long,
    public val incoming: Boolean,
    public val read: Boolean,
)

public interface SmsService {
    public val available: Boolean

    public val canRead: Boolean

    public val canSend: Boolean

    public suspend fun threads(limit: Int = 20): List<PluginSmsThread>

    public suspend fun messages(threadId: String? = null, address: String? = null, limit: Int = 50): List<PluginSmsMessage>

    public suspend fun send(address: String, text: String, subscriptionId: Int? = null): Boolean

    public suspend fun markRead(threadId: String): Boolean

    public suspend fun delete(threadId: String): Boolean
}

public data class PluginCalendar(
    public val id: String,
    public val name: String,
    public val accountName: String? = null,
    public val displayName: String? = null,
    public val color: String? = null,
    public val visible: Boolean = true,
    public val primary: Boolean = false,
)

public data class PluginCalendarEvent(
    public val id: String,
    public val calendarId: String,
    public val title: String,
    public val startMs: Long,
    public val endMs: Long,
    public val allDay: Boolean,
    public val description: String? = null,
    public val location: String? = null,
    public val timeZone: String? = null,
    public val reminders: List<Int> = emptyList(),
)

public interface PluginCalendarEventDraft {
    public val title: String
    public val startMs: Long
    public val endMs: Long
    public val allDay: Boolean
    public val description: String?
    public val location: String?
    public val calendarId: String?
    public val timeZone: String?
    public val reminders: List<Int>
}

public fun pluginCalendarEventDraft(
    title: String,
    startMs: Long,
    endMs: Long,
    allDay: Boolean = false,
    description: String? = null,
    location: String? = null,
    calendarId: String? = null,
    timeZone: String? = null,
    reminders: List<Int> = emptyList(),
): PluginCalendarEventDraft = PluginCalendarEventDraftImpl(
    title,
    startMs,
    endMs,
    allDay,
    description,
    location,
    calendarId,
    timeZone,
    reminders,
)

internal data class PluginCalendarEventDraftImpl(
    override val title: String,
    override val startMs: Long,
    override val endMs: Long,
    override val allDay: Boolean,
    override val description: String?,
    override val location: String?,
    override val calendarId: String?,
    override val timeZone: String?,
    override val reminders: List<Int>,
) : PluginCalendarEventDraft

public interface CalendarService {
    public val permissionGranted: Boolean

    public suspend fun calendars(): List<PluginCalendar>

    public suspend fun events(
        fromMs: Long,
        toMs: Long,
        calendarIds: List<String> = emptyList(),
        limit: Int = 200,
    ): List<PluginCalendarEvent>

    public suspend fun get(eventId: String): PluginCalendarEvent?

    public suspend fun create(draft: PluginCalendarEventDraft): PluginCalendarEvent?

    public suspend fun update(eventId: String, draft: PluginCalendarEventDraft): PluginCalendarEvent?

    public suspend fun delete(eventId: String): Boolean
}

public interface MediaService {
    public suspend fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): String?

    public suspend fun saveFileToDownloads(path: String, fileName: String? = null, mimeType: String? = null): String?

    public suspend fun saveImageToGallery(fileName: String, mimeType: String, bytes: ByteArray): String?

    public suspend fun scanFile(path: String): Boolean

    public suspend fun open(path: String): Boolean

    public suspend fun share(path: String, mimeType: String? = null): Boolean

    public suspend fun preview(path: String): Boolean

    public suspend fun readImage(path: String, maxSizePx: Int = 2_048): ByteArray?

    public suspend fun thumbnail(path: String, maxSizePx: Int = 512): ByteArray?

    public suspend fun importUri(uri: String, fileName: String? = null): String?
}

public data class PluginSpeechResult(
    public val text: String?,
    public val error: String? = null,
)

public interface SpeechService {
    public val speakAvailable: Boolean

    public val recognizeAvailable: Boolean

    public suspend fun speak(text: String): Boolean

    public fun stopSpeaking()

    public fun speaking(): Flow<Boolean>

    public suspend fun recognize(timeoutMs: Long = DEFAULT_TIMEOUT_MS): PluginSpeechResult

    public fun openVoiceSettings(): Boolean

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 30_000
    }
}

public data class PluginWebResult(
    public val title: String,
    public val url: String,
    public val snippet: String? = null,
)

public data class PluginWebPage(
    public val url: String,
    public val title: String?,
    public val text: String,
    public val truncated: Boolean = false,
)

public interface WebService {
    public val available: Boolean

    public suspend fun search(query: String, limit: Int = 8): List<PluginWebResult>

    public suspend fun fetch(url: String, maxChars: Int = 20_000): PluginWebPage?
}

public data class PluginProviderCredential(
    public val id: String,
    public val name: String,
    public val baseUrl: String? = null,
    public val apiKey: String? = null,
    public val authKind: String? = null,
)

public data class PluginMcpToken(
    public val server: String,
    public val url: String? = null,
    public val accessToken: String? = null,
    public val refreshToken: String? = null,
    public val tokenType: String? = null,
    public val expiresAtMs: Long? = null,
)

public interface CredentialService {
    public fun providers(): List<PluginProviderCredential>

    public fun provider(id: String): PluginProviderCredential?

    public fun mcpTokens(): List<PluginMcpToken>

    public fun mcpToken(server: String): PluginMcpToken?

    public fun hostValues(): Map<String, String>
}

public data class PluginAccessibilityEvent(
    public val eventType: Int,
    public val packageName: String?,
    public val className: String?,
    public val text: String?,
    public val contentDescription: String?,
    public val timeMs: Long,
)

@JvmInline
public value class PluginGlobalAction private constructor(public val wire: String) {
    public companion object {
        public val BACK: PluginGlobalAction = PluginGlobalAction("back")
        public val HOME: PluginGlobalAction = PluginGlobalAction("home")
        public val RECENTS: PluginGlobalAction = PluginGlobalAction("recents")
        public val NOTIFICATIONS: PluginGlobalAction = PluginGlobalAction("notifications")
        public val QUICK_SETTINGS: PluginGlobalAction = PluginGlobalAction("quick-settings")
        public val POWER_DIALOG: PluginGlobalAction = PluginGlobalAction("power-dialog")
        public val ALL: List<PluginGlobalAction> = listOf(BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG)

        public fun fromWire(value: String): PluginGlobalAction? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface AccessibilityServicePlugin {
    public val enabled: Boolean

    public fun events(): Flow<PluginAccessibilityEvent>

    public fun performGlobalAction(action: PluginGlobalAction): Boolean
}
