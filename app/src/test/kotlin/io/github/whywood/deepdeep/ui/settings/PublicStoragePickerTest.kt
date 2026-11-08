package io.github.whywood.deepdeep.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicStoragePickerTest {
    @Test
    fun primaryStorageTreeBecomesPublicPath() {
        assertEquals(
            "/storage/emulated/0/Obsidian",
            publicStoragePath("com.android.externalstorage.documents", "primary:Obsidian"),
        )
    }

    @Test
    fun nestedDirectoryBecomesPublicPath() {
        assertEquals(
            "/storage/emulated/0/Documents/Notes",
            publicStoragePath("com.android.externalstorage.documents", "primary:Documents/Notes"),
        )
    }

    @Test
    fun foreignAuthorityIsRejected() {
        assertNull(publicStoragePath("com.google.android.apps.docs", "primary:Obsidian"))
    }

    @Test
    fun rootAndOtherVolumesAreRejected() {
        assertNull(publicStoragePath("com.android.externalstorage.documents", "primary:"))
        assertNull(publicStoragePath("com.android.externalstorage.documents", "secondary:Obsidian"))
    }

    @Test
    fun parentTraversalIsRejected() {
        assertNull(publicStoragePath("com.android.externalstorage.documents", "primary:../Obsidian"))
    }
}
