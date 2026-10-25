package io.github.whywood.deepdeep.plugin.micloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudPreferencesTest {
    private val allOn = MiCloudCapabilities(
        notesRead = true,
        notesWrite = true,
        todosRead = true,
        todosWrite = true,
        smsRead = true,
        contactsRead = true,
        galleryRead = true,
        recordingsRead = true,
        accountRead = true,
    )

    @Test
    fun defaultsAreAllOff() {
        val capabilities = MiCloudCapabilities()
        assertFalse(capabilities.notesRead)
        assertFalse(capabilities.notesWrite)
        assertFalse(capabilities.todosRead)
        assertFalse(capabilities.todosWrite)
        assertFalse(capabilities.smsRead)
        assertFalse(capabilities.contactsRead)
        assertFalse(capabilities.galleryRead)
        assertFalse(capabilities.recordingsRead)
        assertFalse(capabilities.accountRead)
    }

    @Test
    fun readSwitchMapsToFeatureFields() {
        assertTrue(allOn.readEnabled(MiCloudFeature.NOTES))
        assertTrue(allOn.readEnabled(MiCloudFeature.TODOS))
        assertTrue(allOn.readEnabled(MiCloudFeature.SMS))
        assertTrue(allOn.readEnabled(MiCloudFeature.CONTACTS))
        assertTrue(allOn.readEnabled(MiCloudFeature.GALLERY))
        assertTrue(allOn.readEnabled(MiCloudFeature.RECORDINGS))
        assertTrue(allOn.readEnabled(MiCloudFeature.ACCOUNT))
    }

    @Test
    fun writeSwitchOnlyExistsForNotesAndTodos() {
        assertTrue(allOn.writeEnabled(MiCloudFeature.NOTES))
        assertTrue(allOn.writeEnabled(MiCloudFeature.TODOS))
        assertFalse(allOn.writeEnabled(MiCloudFeature.SMS))
        assertFalse(allOn.writeEnabled(MiCloudFeature.CONTACTS))
        assertFalse(allOn.writeEnabled(MiCloudFeature.GALLERY))
        assertFalse(allOn.writeEnabled(MiCloudFeature.RECORDINGS))
        assertFalse(allOn.writeEnabled(MiCloudFeature.ACCOUNT))
    }

    @Test
    fun writableFlagMatchesWriteSupport() {
        assertTrue(MiCloudFeature.NOTES.writable)
        assertTrue(MiCloudFeature.TODOS.writable)
        assertFalse(MiCloudFeature.SMS.writable)
        assertFalse(MiCloudFeature.ACCOUNT.writable)
    }

    @Test
    fun turningReadOffAlsoTurnsWriteOff() {
        val notes = MiCloudCapabilities(notesRead = true, notesWrite = true)
            .withRead(MiCloudFeature.NOTES, false)
        assertFalse(notes.notesRead)
        assertFalse(notes.notesWrite)

        val todos = MiCloudCapabilities(todosRead = true, todosWrite = true)
            .withRead(MiCloudFeature.TODOS, false)
        assertFalse(todos.todosRead)
        assertFalse(todos.todosWrite)
    }

    @Test
    fun turningReadOnKeepsOtherFeaturesUntouched() {
        val updated = MiCloudCapabilities()
            .withRead(MiCloudFeature.GALLERY, true)
        assertTrue(updated.galleryRead)
        assertFalse(updated.notesRead)
        assertFalse(updated.accountRead)
    }

    @Test
    fun turningWriteOnAlsoTurnsReadOn() {
        val notes = MiCloudCapabilities().withWrite(MiCloudFeature.NOTES, true)
        assertTrue(notes.notesRead)
        assertTrue(notes.notesWrite)

        val todos = MiCloudCapabilities().withWrite(MiCloudFeature.TODOS, true)
        assertTrue(todos.todosRead)
        assertTrue(todos.todosWrite)
    }

    @Test
    fun turningWriteOffKeepsReadOn() {
        val notes = MiCloudCapabilities(notesRead = true, notesWrite = true)
            .withWrite(MiCloudFeature.NOTES, false)
        assertTrue(notes.notesRead)
        assertFalse(notes.notesWrite)
    }

    @Test
    fun writeOnReadOnlyFeatureIsIgnored() {
        val capabilities = MiCloudCapabilities(smsRead = true)
        assertEquals(capabilities, capabilities.withWrite(MiCloudFeature.SMS, true))
        assertEquals(capabilities, capabilities.withWrite(MiCloudFeature.CONTACTS, true))
    }
}
