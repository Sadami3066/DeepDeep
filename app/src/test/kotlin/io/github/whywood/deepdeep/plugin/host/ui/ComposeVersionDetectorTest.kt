package io.github.whywood.deepdeep.plugin.host.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeVersionDetectorTest {
    private class IntVersion {
        companion object {
            @JvmField
            val version: Int = 13000
        }
    }

    private class StringVersion {
        companion object {
            @JvmField
            val version: String = "1.10.5"
        }
    }

    @Test
    fun readsVersionFromRuntimeVersionEntry() {
        val detected = ComposeVersionDetector.detectFromResource { entry ->
            if (entry == "META-INF/androidx.compose.runtime_runtime.version") " 1.10.5\n" else null
        }
        assertEquals(DetectedComposeVersion("1.10.5", null), detected)
    }

    @Test
    fun missingVersionEntryReturnsNull() {
        assertNull(ComposeVersionDetector.detectFromResource { null })
    }

    @Test
    fun readsStringComposeVersionField() {
        assertEquals(
            DetectedComposeVersion("1.10.5", null),
            ComposeVersionDetector.detectFromClass { StringVersion::class.java },
        )
    }

    @Test
    fun readsIntComposeVersionCompatibilityId() {
        assertEquals(
            DetectedComposeVersion(null, 13000),
            ComposeVersionDetector.detectFromClass { IntVersion::class.java },
        )
    }

    @Test
    fun missingComposeVersionClassReturnsNull() {
        assertNull(ComposeVersionDetector.detectFromClass { null })
    }

    @Test
    fun normalizesVersionStrings() {
        assertEquals("1.10.5", ComposeVersionDetector.normalizeVersion(" v1.10.5 "))
        assertNull(ComposeVersionDetector.normalizeVersion(null))
        assertNull(ComposeVersionDetector.normalizeVersion("   "))
        assertNull(ComposeVersionDetector.normalizeVersion("null"))
    }
}
