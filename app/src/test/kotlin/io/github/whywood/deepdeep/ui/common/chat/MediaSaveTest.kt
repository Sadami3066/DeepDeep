package io.github.whywood.deepdeep.ui.common.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSaveTest {
    @Test
    fun fileProviderAuthorityMatchesManifest() {
        assertEquals(
            "io.github.whywood.deepdeep.files",
            mediaFileProviderAuthority("io.github.whywood.deepdeep"),
        )
    }

    @Test
    fun fileProviderAuthorityFollowsPackageName() {
        assertEquals("com.example.app.files", mediaFileProviderAuthority("com.example.app"))
    }
}
