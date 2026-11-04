package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayEntryPreferencesTest {
    @Test
    fun parseReadsStoredValues() {
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode("voice"))
        assertEquals(OverlayEntryMode.CAMERA, parseOverlayEntryMode("camera"))
        assertEquals(OverlayEntryMode.NONE, parseOverlayEntryMode("none"))
    }

    @Test
    fun parseFallsBackToVoice() {
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode(null))
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode(""))
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode("VOICE"))
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode("camera "))
        assertEquals(OverlayEntryMode.VOICE, parseOverlayEntryMode("unknown"))
    }

    @Test
    fun storageValueRoundTrips() {
        OverlayEntryMode.entries.forEach { mode ->
            assertEquals(mode, parseOverlayEntryMode(mode.storageValue()))
        }
    }
}
