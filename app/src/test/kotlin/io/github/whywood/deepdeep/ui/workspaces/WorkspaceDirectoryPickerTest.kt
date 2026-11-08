package io.github.whywood.deepdeep.ui.workspaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceDirectoryPickerTest {
    @Test
    fun resolvesPrimaryStorageDirectory() {
        assertEquals(
            "/storage/emulated/0/Documents/notes",
            sharedStorageDirectory("com.android.externalstorage.documents", "primary:Documents/notes"),
        )
    }

    @Test
    fun trimsTrailingSlash() {
        assertEquals(
            "/storage/emulated/0/Documents",
            sharedStorageDirectory("com.android.externalstorage.documents", "primary:Documents/"),
        )
    }

    @Test
    fun rejectsOtherAuthorities() {
        assertNull(sharedStorageDirectory("com.android.providers.downloads.documents", "primary:Download"))
        assertNull(sharedStorageDirectory(null, "primary:Download"))
    }

    @Test
    fun rejectsNonPrimaryVolumes() {
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "1234-5678:Documents"))
    }

    @Test
    fun rejectsRootAndMalformedIds() {
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "primary:"))
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "primary"))
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", null))
    }

    @Test
    fun rejectsParentSegments() {
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "primary:Documents/../secret"))
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "primary:Documents/./notes"))
        assertNull(sharedStorageDirectory("com.android.externalstorage.documents", "primary:Documents//notes"))
    }
}
