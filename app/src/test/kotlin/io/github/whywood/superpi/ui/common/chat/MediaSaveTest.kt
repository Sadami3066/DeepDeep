package io.github.whywood.superpi.ui.common.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSaveTest {
    @Test
    fun fileProviderAuthorityMatchesManifest() {
        assertEquals(
            "io.github.whywood.superpi.files",
            mediaFileProviderAuthority("io.github.whywood.superpi"),
        )
    }

    @Test
    fun fileProviderAuthorityFollowsPackageName() {
        assertEquals("com.example.app.files", mediaFileProviderAuthority("com.example.app"))
    }
}
