package io.github.whywood.deepdeep.plugin.host.extensions.data

import io.github.whywood.deepdeep.plugin.api.PluginGlobalAction
import io.github.whywood.deepdeep.voice.SpeechError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPluginServiceTest {
    @Test
    fun mapsEveryGlobalAction() {
        PluginGlobalAction.ALL.forEach { action ->
            assertNotNull("missing accessibility mapping for ${action.wire}", AccessibilityEventHub.globalActionConstant(action.wire))
        }
    }

    @Test
    fun powerDialogMapsToGlobalAction() {
        assertEquals(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            AccessibilityEventHub.globalActionConstant(PluginGlobalAction.POWER_DIALOG.wire),
        )
    }

    @Test
    fun unknownActionHasNoMapping() {
        assertNull(AccessibilityEventHub.globalActionConstant("launch-missiles"))
    }

    @Test
    fun speechErrorsMapToReadableMessages() {
        SpeechError.entries.forEach { error ->
            val message = speechErrorMessage(error)
            assertTrue("empty message for $error", message.isNotBlank())
        }
        assertEquals("Microphone permission was not granted", speechErrorMessage(SpeechError.INSUFFICIENT_PERMISSIONS))
        assertTrue(speechErrorMessage(SpeechError.UNAVAILABLE).contains("No speech recognition service"))
    }
}
