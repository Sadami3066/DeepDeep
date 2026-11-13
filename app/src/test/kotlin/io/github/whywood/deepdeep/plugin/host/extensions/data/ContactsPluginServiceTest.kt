package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsPluginServiceTest {
    @Test
    fun mapsKnownPhoneTypesToLabels() {
        assertEquals("mobile", phoneTypeLabel(Phone.TYPE_MOBILE, null))
        assertEquals("home", phoneTypeLabel(Phone.TYPE_HOME, null))
        assertEquals("work", phoneTypeLabel(Phone.TYPE_WORK, null))
        assertEquals("fax", phoneTypeLabel(Phone.TYPE_FAX_WORK, null))
        assertEquals("main", phoneTypeLabel(Phone.TYPE_MAIN, null))
        assertEquals("other", phoneTypeLabel(Phone.TYPE_OTHER, null))
    }

    @Test
    fun mapsEmailTypesSeparatelyFromPhoneTypes() {
        assertEquals("work", emailTypeLabel(Email.TYPE_WORK, null))
        assertEquals("home", emailTypeLabel(Email.TYPE_HOME, null))
        assertEquals("mobile", emailTypeLabel(Email.TYPE_MOBILE, null))
        assertEquals("other", emailTypeLabel(Email.TYPE_OTHER, null))
        assertEquals("mobile", phoneTypeLabel(Phone.TYPE_MOBILE, null))
    }

    @Test
    fun customLabelWinsOverType() {
        assertEquals("紧急", phoneTypeLabel(Phone.TYPE_MOBILE, "紧急"))
        assertEquals("工作", emailTypeLabel(Email.TYPE_WORK, "工作"))
    }

    @Test
    fun unknownTypeHasNoLabel() {
        assertNull(phoneTypeLabel(99, null))
        assertNull(emailTypeLabel(99, null))
        assertNull(phoneTypeLabel(null, null))
    }

    @Test
    fun buildsContactWithFilteredFields() {
        val contact = buildPluginContact(
            id = "42",
            displayName = "Alice",
            phones = listOf(
                ContactValueRow("13800000000", Phone.TYPE_MOBILE, null),
                ContactValueRow("   ", Phone.TYPE_HOME, null),
            ),
            emails = listOf(
                ContactValueRow("alice@example.com", Email.TYPE_WORK, null),
                ContactValueRow("", Email.TYPE_HOME, null),
            ),
            organization = "DeepDeep",
            note = "  ",
            starred = true,
        )

        assertEquals("42", contact.id)
        assertEquals("Alice", contact.displayName)
        assertEquals(1, contact.phones.size)
        assertEquals("mobile", contact.phones.single().label)
        assertEquals(1, contact.emails.size)
        assertEquals("work", contact.emails.single().label)
        assertEquals("DeepDeep", contact.organization)
        assertNull(contact.note)
        assertTrue(contact.starred)
    }

    @Test
    fun blankOrganizationIsDropped() {
        val contact = buildPluginContact(
            id = "1",
            displayName = "Bob",
            phones = emptyList(),
            emails = emptyList(),
            organization = " ",
            note = "note",
            starred = false,
        )
        assertNull(contact.organization)
        assertEquals("note", contact.note)
        assertTrue(contact.phones.isEmpty())
    }
}
