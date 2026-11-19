package io.github.whywood.superpi.ui.miniapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.whywood.superpi.arrange.AndroidArrangeCalendarGateway
import io.github.whywood.superpi.data.AppLanguage
import io.github.whywood.superpi.runtime.MiniappCalendarDraft
import io.github.whywood.superpi.runtime.MiniappCalendarEvent
import io.github.whywood.superpi.runtime.MiniappEnv
import io.github.whywood.superpi.runtime.MiniappFileEntry
import io.github.whywood.superpi.runtime.MiniappFileRead
import io.github.whywood.superpi.runtime.MiniappFileRef
import io.github.whywood.superpi.runtime.MiniappHostException
import io.github.whywood.superpi.runtime.MiniappHostGateway
import io.github.whywood.superpi.runtime.MiniappLocation
import io.github.whywood.superpi.runtime.MiniappNotificationRequest
import io.github.whywood.superpi.runtime.MiniappReminder
import io.github.whywood.superpi.runtime.MiniappReminderRequest
import io.github.whywood.superpi.runtime.MiniappSafeArea
import io.github.whywood.superpi.voice.SpeechEvent
import io.github.whywood.superpi.voice.SpeechRecognizerController
import io.github.whywood.superpi.voice.TextSpeaker
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kotlin.coroutines.resume

internal interface MiniappUiHost {
    fun haptic(intensity: String): Boolean

    fun keepScreenOn(enabled: Boolean): Boolean
}

internal class MiniappHostImpl(
    private val context: Context,
    private val appId: String,
    private val appName: String,
    private val fileStore: MiniappFileStore,
    private val interactions: MiniappInteractions,
    private val uiHost: MiniappUiHost,
    private val safeArea: () -> MiniappSafeArea,
) : MiniappHostGateway {
    private val notifications = MiniappNotificationCenter(context, appId, appName)
    private val calendar = AndroidArrangeCalendarGateway(context)
    private val speaker: TextSpeaker by lazy { TextSpeaker(context) }
    private val recognizer: SpeechRecognizerController by lazy { SpeechRecognizerController(context) }

    override suspend fun env(): MiniappEnv {
        val configuration = context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]?.toLanguageTag().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.toLanguageTag().orEmpty()
        }
        return MiniappEnv(
            locale = locale.ifBlank { Locale.getDefault().toLanguageTag() },
            fontScale = configuration.fontScale,
            safeArea = safeArea(),
        )
    }

    override suspend fun showNotification(request: MiniappNotificationRequest): String {
        ensureNotificationsPermission()
        return notifications.show(request)
    }

    override suspend fun cancelNotification(id: String): Boolean = notifications.cancel(id)

    override suspend fun cancelNotifications(): Boolean = notifications.cancelAll()

    override suspend fun scheduleReminder(request: MiniappReminderRequest): MiniappReminder {
        ensureNotificationsPermission()
        return notifications.schedule(request)
    }

    override suspend fun cancelReminder(id: String): Boolean = notifications.cancel(id)

    override suspend fun reminders(): List<MiniappReminder> = notifications.list()

    override suspend fun pickFiles(types: List<String>, multiple: Boolean): List<MiniappFileRef> {
        val uris = interactions.pickFiles(types, multiple)
        return importAll(uris)
    }

    override suspend fun readFile(path: String, encoding: String): MiniappFileRead =
        fileStore.read(path, encoding)

    override suspend fun writeFile(path: String, encoding: String, data: String): MiniappFileRef =
        fileStore.write(path, encoding, data)

    override suspend fun exportFile(path: String): Boolean = fileStore.export(path) { name, mimeType ->
        interactions.exportDocument(name, mimeType)
    }

    override suspend fun deleteFile(path: String): Boolean = fileStore.delete(path)

    override suspend fun listFiles(directory: String): List<MiniappFileEntry> = fileStore.list(directory)

    override suspend fun fileUrl(path: String): String = fileStore.url(path)

    override suspend fun shareText(text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launchChooser(intent)
    }

    override suspend fun shareFile(path: String, mimeType: String?): Boolean {
        val file = fileStore.resolve(path)
        if (!file.isFile) throw MiniappHostException(MiniappHostException.NOT_FOUND, "file not found")
        val uri = fileUri(file)
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: fileStore.mimeType(file.name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = resolvedMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launchChooser(intent)
    }

    override suspend fun readClipboard(): String? {
        val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    override suspend fun writeClipboard(text: String): Boolean {
        val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return false
        manager.setPrimaryClip(android.content.ClipData.newPlainText(appName, text))
        return true
    }

    override suspend fun haptic(intensity: String): Boolean = uiHost.haptic(intensity)

    override suspend fun keepScreenOn(enabled: Boolean): Boolean = uiHost.keepScreenOn(enabled)

    override suspend fun pickMedia(type: String, multiple: Boolean): List<MiniappFileRef> {
        val uris = interactions.pickMedia(type, multiple)
        return importAll(uris)
    }

    override suspend fun captureMedia(): MiniappFileRef? {
        val uri = interactions.capturePhoto() ?: return null
        return importAll(listOf(uri)).firstOrNull()
    }

    override suspend fun saveToGallery(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.resolve(path)
        if (!file.isFile) throw MiniappHostException(MiniappHostException.NOT_FOUND, "file not found")
        val mimeType = fileStore.mimeType(file.name)
        val collection = if (mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (pending) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (mimeType.startsWith("video/")) "Movies" else "Pictures")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = runCatching { context.contentResolver.insert(collection, values) }.getOrNull()
            ?: throw MiniappHostException(MiniappHostException.FAILED, "cannot create media entry")
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("cannot open media entry")
            if (pending) {
                val completed = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, completed, null, null)
            }
        }.getOrElse { error ->
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot save image")
        }
        true
    }

    override suspend fun scanQr(): String? = interactions.scanQr()

    override suspend fun speak(text: String, language: String?): Boolean {
        val resolved = when {
            language.isNullOrBlank() -> AppLanguage.SYSTEM
            language.startsWith("zh") -> AppLanguage.CHINESE
            language.startsWith("en") -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
        speaker.speak(text, resolved)
        return true
    }

    override suspend fun stopSpeaking(): Boolean {
        speaker.stop()
        return true
    }

    override suspend fun listen(timeoutMillis: Long): String {
        ensurePermission(Manifest.permission.RECORD_AUDIO)
        if (!recognizer.isAvailable()) {
            throw MiniappHostException(MiniappHostException.UNAVAILABLE, "speech recognition is unavailable")
        }
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        } else {
            Locale.getDefault().toLanguageTag()
        }
        val finalEvent = withTimeoutOrNull(timeoutMillis) {
            recognizer.listen(tag.ifBlank { "zh-CN" }).first { event ->
                event is SpeechEvent.Final || event is SpeechEvent.Failed
            }
        } ?: throw MiniappHostException(MiniappHostException.FAILED, "speech recognition timed out")
        return (finalEvent as? SpeechEvent.Final)?.text
            ?: throw MiniappHostException(MiniappHostException.FAILED, "speech recognition failed")
    }

    override suspend fun openUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "invalid url")
        val scheme = uri.scheme?.lowercase()
        if (scheme.isNullOrBlank() || scheme in setOf("file", "content", "javascript", "intent")) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "url scheme is not allowed")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    @SuppressLint("MissingPermission")
    override suspend fun location(): MiniappLocation {
        ensurePermission(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw MiniappHostException(MiniappHostException.UNAVAILABLE, "location is unavailable")
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val known = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = if (provider != null) awaitLocation(manager, provider) else null
        val best = fresh ?: known
            ?: throw MiniappHostException(MiniappHostException.FAILED, "no location available")
        return locationOf(best)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(manager: LocationManager, provider: String): Location? {
        val listenerRef = java.util.concurrent.atomic.AtomicReference<LocationListener?>(null)
        return try {
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive) continuation.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) {
                            if (continuation.isActive) continuation.resume(null)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit
                    }
                    listenerRef.set(listener)
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }
                }
            }
        } finally {
            listenerRef.get()?.let { listener -> runCatching { manager.removeUpdates(listener) } }
        }
    }

    override suspend fun calendarEvents(fromMillis: Long, toMillis: Long, limit: Int): List<MiniappCalendarEvent> {
        calendar.ensureReadAccess()
        return withContext(Dispatchers.IO) {
            calendar.list(fromMillis, toMillis, null, limit).map { event ->
                MiniappCalendarEvent(
                    id = event.eventId.toString(),
                    calendarId = event.calendarId.toString(),
                    title = event.title,
                    startMillis = event.startMillis,
                    endMillis = event.endMillis,
                    allDay = event.allDay,
                    description = event.description?.takeIf { it.isNotBlank() },
                    location = event.location?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    override suspend fun addCalendarEvent(draft: MiniappCalendarDraft): MiniappCalendarEvent? {
        calendar.ensureWriteAccess()
        return withContext(Dispatchers.IO) {
            val created = calendar.create(
                io.github.whywood.superpi.arrange.ArrangeEventDraft(
                    title = draft.title,
                    startMillis = draft.startMillis,
                    endMillis = draft.endMillis,
                    allDay = draft.allDay,
                    location = draft.location,
                    description = draft.description,
                ),
            )
            MiniappCalendarEvent(
                id = created.eventId.toString(),
                calendarId = created.calendarId.toString(),
                title = created.title,
                startMillis = created.startMillis,
                endMillis = created.endMillis,
                allDay = created.allDay,
                description = created.description?.takeIf { it.isNotBlank() },
                location = created.location?.takeIf { it.isNotBlank() },
            )
        }
    }

    private suspend fun importAll(uris: List<Uri>): List<MiniappFileRef> = withContext(Dispatchers.IO) {
        uris.take(20).mapNotNull { uri -> runCatching { fileStore.import(uri) }.getOrNull() }
    }

    private fun fileUri(file: File): Uri {
        val authority = context.packageName + ".files"
        return runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrElse { error ->
            throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot share file")
        }
    }

    private fun launchChooser(intent: Intent): Boolean {
        val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    private suspend fun ensureNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        interactions.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private suspend fun ensurePermission(vararg permissions: String) {
        val missing = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return
        val grants = interactions.requestPermissions(missing.toTypedArray())
        if (grants.values.none { it }) {
            throw MiniappHostException(MiniappHostException.PERMISSION_DENIED, "permission denied")
        }
    }

    private fun locationOf(location: Location): MiniappLocation = MiniappLocation(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyM = location.accuracy,
        provider = location.provider.orEmpty(),
        timeMillis = location.time,
    )
}
