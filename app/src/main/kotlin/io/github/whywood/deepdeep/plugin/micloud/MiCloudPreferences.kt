package io.github.whywood.deepdeep.plugin.micloud

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MiCloudFeature {
    NOTES,
    TODOS,
    SMS,
    CONTACTS,
    GALLERY,
    RECORDINGS,
    ACCOUNT,
}

data class MiCloudCapabilities(
    val notesRead: Boolean = false,
    val notesWrite: Boolean = false,
    val todosRead: Boolean = false,
    val todosWrite: Boolean = false,
    val smsRead: Boolean = false,
    val contactsRead: Boolean = false,
    val galleryRead: Boolean = false,
    val recordingsRead: Boolean = false,
    val accountRead: Boolean = false,
)

val MiCloudFeature.writable: Boolean
    get() = this == MiCloudFeature.NOTES || this == MiCloudFeature.TODOS

fun MiCloudCapabilities.readEnabled(feature: MiCloudFeature): Boolean = when (feature) {
    MiCloudFeature.NOTES -> notesRead
    MiCloudFeature.TODOS -> todosRead
    MiCloudFeature.SMS -> smsRead
    MiCloudFeature.CONTACTS -> contactsRead
    MiCloudFeature.GALLERY -> galleryRead
    MiCloudFeature.RECORDINGS -> recordingsRead
    MiCloudFeature.ACCOUNT -> accountRead
}

fun MiCloudCapabilities.writeEnabled(feature: MiCloudFeature): Boolean = when (feature) {
    MiCloudFeature.NOTES -> notesWrite
    MiCloudFeature.TODOS -> todosWrite
    else -> false
}

internal fun MiCloudCapabilities.withRead(feature: MiCloudFeature, enabled: Boolean): MiCloudCapabilities = when (feature) {
    MiCloudFeature.NOTES -> if (enabled) {
        copy(notesRead = true)
    } else {
        copy(notesRead = false, notesWrite = false)
    }

    MiCloudFeature.TODOS -> if (enabled) {
        copy(todosRead = true)
    } else {
        copy(todosRead = false, todosWrite = false)
    }

    MiCloudFeature.SMS -> copy(smsRead = enabled)
    MiCloudFeature.CONTACTS -> copy(contactsRead = enabled)
    MiCloudFeature.GALLERY -> copy(galleryRead = enabled)
    MiCloudFeature.RECORDINGS -> copy(recordingsRead = enabled)
    MiCloudFeature.ACCOUNT -> copy(accountRead = enabled)
}

internal fun MiCloudCapabilities.withWrite(feature: MiCloudFeature, enabled: Boolean): MiCloudCapabilities = when (feature) {
    MiCloudFeature.NOTES -> if (enabled) {
        copy(notesRead = true, notesWrite = true)
    } else {
        copy(notesWrite = false)
    }

    MiCloudFeature.TODOS -> if (enabled) {
        copy(todosRead = true, todosWrite = true)
    } else {
        copy(todosWrite = false)
    }

    else -> this
}

interface MiCloudSettings {
    val capabilities: StateFlow<MiCloudCapabilities>

    suspend fun setRead(feature: MiCloudFeature, enabled: Boolean)

    suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean)

    suspend fun setFeature(feature: MiCloudFeature, enabled: Boolean) {
        setRead(feature, enabled)
        if (enabled && feature.writable) setWrite(feature, true)
    }

    suspend fun clear()
}

private val Context.miCloudCapabilitiesDataStore by preferencesDataStore(name = "micloud_capabilities")

private val NOTES_READ = booleanPreferencesKey("notes_read")
private val NOTES_WRITE = booleanPreferencesKey("notes_write")
private val TODOS_READ = booleanPreferencesKey("todos_read")
private val TODOS_WRITE = booleanPreferencesKey("todos_write")
private val SMS_READ = booleanPreferencesKey("sms_read")
private val CONTACTS_READ = booleanPreferencesKey("contacts_read")
private val GALLERY_READ = booleanPreferencesKey("gallery_read")
private val RECORDINGS_READ = booleanPreferencesKey("recordings_read")
private val ACCOUNT_READ = booleanPreferencesKey("account_read")

class MiCloudPreferences(
    context: Context,
    private val onChange: () -> Unit = {},
) : MiCloudSettings {
    private val store = context.applicationContext.miCloudCapabilitiesDataStore
    private val mutableCapabilities = MutableStateFlow(MiCloudCapabilities())
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val capabilities: StateFlow<MiCloudCapabilities> = mutableCapabilities.asStateFlow()

    init {
        scope.launch {
            val persisted = try {
                store.data.first()
            } catch (error: Exception) {
                null
            }
            mutex.withLock {
                if (persisted != null) mutableCapabilities.value = decode(persisted)
                loaded.complete(Unit)
            }
        }
    }

    override suspend fun setRead(feature: MiCloudFeature, enabled: Boolean) {
        update { current -> current.withRead(feature, enabled) }
    }

    override suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean) {
        update { current -> current.withWrite(feature, enabled) }
    }

    override suspend fun setFeature(feature: MiCloudFeature, enabled: Boolean) {
        update { current ->
            if (feature.writable) {
                if (enabled) current.withRead(feature, true).withWrite(feature, true)
                else current.withRead(feature, false)
            } else {
                current.withRead(feature, enabled)
            }
        }
    }

    override suspend fun clear() {
        loaded.await()
        mutex.withLock {
            store.edit { values -> write(values, MiCloudCapabilities()) }
            mutableCapabilities.value = MiCloudCapabilities()
        }
        onChange()
    }

    private suspend fun update(transform: (MiCloudCapabilities) -> MiCloudCapabilities) {
        loaded.await()
        val changed = mutex.withLock {
            val current = mutableCapabilities.value
            val next = transform(current)
            if (next == current) {
                false
            } else {
                store.edit { values -> write(values, next) }
                mutableCapabilities.value = next
                true
            }
        }
        if (changed) onChange()
    }
}

private fun decode(values: Preferences): MiCloudCapabilities {
    val notes = (values[NOTES_READ] ?: false) || (values[NOTES_WRITE] ?: false)
    val todos = (values[TODOS_READ] ?: false) || (values[TODOS_WRITE] ?: false)
    return MiCloudCapabilities(
        notesRead = notes,
        notesWrite = notes,
        todosRead = todos,
        todosWrite = todos,
        smsRead = values[SMS_READ] ?: false,
        contactsRead = values[CONTACTS_READ] ?: false,
        galleryRead = values[GALLERY_READ] ?: false,
        recordingsRead = values[RECORDINGS_READ] ?: false,
        accountRead = values[ACCOUNT_READ] ?: false,
    )
}

private fun write(values: MutablePreferences, capabilities: MiCloudCapabilities) {
    values[NOTES_READ] = capabilities.notesRead
    values[NOTES_WRITE] = capabilities.notesWrite
    values[TODOS_READ] = capabilities.todosRead
    values[TODOS_WRITE] = capabilities.todosWrite
    values[SMS_READ] = capabilities.smsRead
    values[CONTACTS_READ] = capabilities.contactsRead
    values[GALLERY_READ] = capabilities.galleryRead
    values[RECORDINGS_READ] = capabilities.recordingsRead
    values[ACCOUNT_READ] = capabilities.accountRead
}
