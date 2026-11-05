package io.github.whywood.deepdeep.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class MaskApiKeyTest {
    @Test
    fun masksAsManyStarsAsTheKeyLength() {
        assertEquals("sk-**********", maskApiKey("sk-1234567890"))
    }

    @Test
    fun longKeyKeepsPrefixAndCapsTheStars() {
        assertEquals("sk-" + "*".repeat(24), maskApiKey("sk-" + "a".repeat(32)))
    }

    @Test
    fun shortKeyIsFullyMasked() {
        assertEquals("*****", maskApiKey("sk-12"))
        assertEquals("*****", maskApiKey("abc"))
    }

    @Test
    fun emptyKeyUsesFallbackMask() {
        assertEquals("*****", maskApiKey(null))
        assertEquals("*****", maskApiKey("   "))
    }

    @Test
    fun trimsBeforeMasking() {
        assertEquals("sk-**********", maskApiKey("  sk-1234567890  "))
    }
}
