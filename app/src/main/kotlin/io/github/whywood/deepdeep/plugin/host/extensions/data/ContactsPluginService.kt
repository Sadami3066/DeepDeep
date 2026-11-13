package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import io.github.whywood.deepdeep.plugin.api.ContactsService
import io.github.whywood.deepdeep.plugin.api.PluginContact
import io.github.whywood.deepdeep.plugin.api.PluginContactDraft
import io.github.whywood.deepdeep.plugin.api.PluginContactField
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ContactValueRow(
    val value: String,
    val type: Int? = null,
    val label: String? = null,
)

internal fun phoneTypeLabel(type: Int?, label: String?): String? {
    if (!label.isNullOrBlank()) return label
    return when (type) {
        Phone.TYPE_MOBILE -> "mobile"
        Phone.TYPE_HOME -> "home"
        Phone.TYPE_WORK -> "work"
        Phone.TYPE_FAX_HOME, Phone.TYPE_FAX_WORK -> "fax"
        Phone.TYPE_MAIN -> "main"
        Phone.TYPE_OTHER -> "other"
        else -> null
    }
}

internal fun emailTypeLabel(type: Int?, label: String?): String? {
    if (!label.isNullOrBlank()) return label
    return when (type) {
        Email.TYPE_HOME -> "home"
        Email.TYPE_WORK -> "work"
        Email.TYPE_MOBILE -> "mobile"
        Email.TYPE_OTHER -> "other"
        else -> null
    }
}

internal fun buildPluginContact(
    id: String,
    displayName: String,
    phones: List<ContactValueRow>,
    emails: List<ContactValueRow>,
    organization: String?,
    note: String?,
    starred: Boolean,
): PluginContact = PluginContact(
    id = id,
    displayName = displayName,
    phones = phones.filter { it.value.isNotBlank() }.map { row ->
        PluginContactField(value = row.value, label = phoneTypeLabel(row.type, row.label))
    },
    emails = emails.filter { it.value.isNotBlank() }.map { row ->
        PluginContactField(value = row.value, label = emailTypeLabel(row.type, row.label))
    },
    organization = organization?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    starred = starred,
)

internal class ContactsPluginServiceImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContactsService {
    private val resolver = context.contentResolver

    override val permissionGranted: Boolean
        get() = DataPermissions.granted(context, android.Manifest.permission.READ_CONTACTS)

    override suspend fun list(limit: Int): List<PluginContact> = withContext(ioDispatcher) {
        requireRead()
        queryContacts(selection = null, selectionArgs = null, limit = limit)
    }

    override suspend fun search(query: String, limit: Int): List<PluginContact> = withContext(ioDispatcher) {
        requireRead()
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext emptyList()
        queryContacts(
            selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            selectionArgs = arrayOf("%$keyword%"),
            limit = limit,
        )
    }

    override suspend fun get(id: String): PluginContact? = withContext(ioDispatcher) {
        requireRead()
        queryContacts(
            selection = "${ContactsContract.Contacts._ID} = ?",
            selectionArgs = arrayOf(id),
            limit = 1,
        ).firstOrNull()
    }

    override suspend fun create(draft: PluginContactDraft): PluginContact? = withContext(ioDispatcher) {
        requireWrite()
        val ops = mutableListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, draft.displayName.trim())
            .build()
        appendDetails(ops, draft, rawContactReference = null)
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        val rawContactId = results.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return@withContext null
        val contactId = contactIdOfRawContact(rawContactId) ?: return@withContext null
        queryContacts(
            selection = "${ContactsContract.Contacts._ID} = ?",
            selectionArgs = arrayOf(contactId.toString()),
            limit = 1,
        ).firstOrNull()
    }

    override suspend fun update(id: String, draft: PluginContactDraft): PluginContact? = withContext(ioDispatcher) {
        requireWrite()
        val rawContactId = rawContactIdOfContact(id) ?: return@withContext null
        val ops = mutableListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId.toString(), StructuredName.CONTENT_ITEM_TYPE),
            )
            .withValue(StructuredName.DISPLAY_NAME, draft.displayName.trim())
            .build()
        val detailTypes = arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE)
        ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf(rawContactId.toString(), detailTypes[0], detailTypes[1]),
            )
            .build()
        appendDetails(ops, draft, rawContactReference = rawContactId)
        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        queryContacts(
            selection = "${ContactsContract.Contacts._ID} = ?",
            selectionArgs = arrayOf(id),
            limit = 1,
        ).firstOrNull()
    }

    override suspend fun delete(id: String): Boolean = withContext(ioDispatcher) {
        requireWrite()
        resolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(id),
        ) > 0
    }

    private fun appendDetails(
        ops: MutableList<ContentProviderOperation>,
        draft: PluginContactDraft,
        rawContactReference: Long?,
    ) {
        fun dataInsert(mimeType: String) = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).apply {
            if (rawContactReference == null) {
                withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            } else {
                withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactReference)
            }
            withValue(ContactsContract.Data.MIMETYPE, mimeType)
        }
        draft.phones.map(String::trim).filter(String::isNotEmpty).forEach { phone ->
            ops += dataInsert(Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, phone)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }
        draft.emails.map(String::trim).filter(String::isNotEmpty).forEach { email ->
            ops += dataInsert(Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email)
                .withValue(Email.TYPE, Email.TYPE_OTHER)
                .build()
        }
        draft.organization?.trim()?.takeIf { it.isNotEmpty() }?.let { organization ->
            ops += dataInsert(Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, organization)
                .build()
        }
    }

    private fun queryContacts(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
    ): List<PluginContact> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.STARRED,
        )
        val sortOrder = if (limit > 0) {
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT ${limit.coerceAtMost(MAX_LIMIT)}"
        } else {
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        }
        val ids = mutableListOf<String>()
        val names = mutableMapOf<String, String>()
        val starred = mutableSetOf<String>()
        resolver.query(ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    ids += id
                    names[id] = cursor.getString(1).orEmpty()
                    if (cursor.getInt(2) != 0) starred += id
                }
            }
        if (ids.isEmpty()) return emptyList()
        val phonesByContact = queryValues(ids, Phone.CONTENT_ITEM_TYPE)
        val emailsByContact = queryValues(ids, Email.CONTENT_ITEM_TYPE)
        val organizations = queryOrganization(ids)
        val notes = queryNote(ids)
        return ids.map { id ->
            buildPluginContact(
                id = id,
                displayName = names[id].orEmpty(),
                phones = phonesByContact[id].orEmpty(),
                emails = emailsByContact[id].orEmpty(),
                organization = organizations[id],
                note = notes[id],
                starred = id in starred,
            )
        }
    }

    private fun queryValues(ids: List<String>, mimeType: String): Map<String, List<ContactValueRow>> {
        val result = mutableMapOf<String, MutableList<ContactValueRow>>()
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
        )
        val selection = "${ContactsContract.Data.CONTACT_ID} IN (${placeholders(ids.size)}) " +
            "AND ${ContactsContract.Data.MIMETYPE} = ?"
        val args = ids + mimeType
        resolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args.toTypedArray(), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(0) ?: continue
                    val value = cursor.getString(1)?.trim().orEmpty()
                    if (value.isEmpty()) continue
                    result.getOrPut(contactId) { mutableListOf() } += ContactValueRow(
                        value = value,
                        type = if (cursor.isNull(2)) null else cursor.getInt(2),
                        label = cursor.getString(3)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        return result
    }

    private fun queryOrganization(ids: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val selection = "${ContactsContract.Data.CONTACT_ID} IN (${placeholders(ids.size)}) " +
            "AND ${ContactsContract.Data.MIMETYPE} = ?"
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1),
            selection,
            (ids + Organization.CONTENT_ITEM_TYPE).toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(0) ?: continue
                val company = cursor.getString(1)?.trim().orEmpty()
                if (company.isNotEmpty()) result.putIfAbsent(contactId, company)
            }
        }
        return result
    }

    private fun queryNote(ids: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val selection = "${ContactsContract.Data.CONTACT_ID} IN (${placeholders(ids.size)}) " +
            "AND ${ContactsContract.Data.MIMETYPE} = ?"
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1),
            selection,
            (ids + Note.CONTENT_ITEM_TYPE).toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(0) ?: continue
                val note = cursor.getString(1)?.trim().orEmpty()
                if (note.isNotEmpty()) result.putIfAbsent(contactId, note)
            }
        }
        return result
    }

    private fun contactIdOfRawContact(rawContactId: Long): Long? = resolver.query(
        ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
        arrayOf(ContactsContract.RawContacts.CONTACT_ID),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }

    private fun rawContactIdOfContact(contactId: String): Long? = resolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        arrayOf(ContactsContract.RawContacts._ID),
        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
        arrayOf(contactId),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private fun requireRead() {
        if (!permissionGranted) {
            throw SecurityException(
                "Contacts read requires READ_CONTACTS permission; grant contacts access for DeepDeep and retry",
            )
        }
    }

    private fun requireWrite() {
        if (!DataPermissions.allGranted(context, DataPermissions.contactsWrite)) {
            throw SecurityException(
                "Contacts write requires WRITE_CONTACTS permission; grant contacts access for DeepDeep and retry",
            )
        }
    }

    private companion object {
        const val MAX_LIMIT = 1_000
    }
}
