package io.github.whywood.deepdeep.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEntryTest {
    @Test
    fun powerSettingsActionsFollowFallbackOrder() {
        assertEquals(
            listOf(
                "android.settings.ACTION_POWER_MENU_SETTINGS",
                "android.settings.ASSIST_GESTURE_SETTINGS",
                "android.settings.VOICE_INPUT_SETTINGS",
            ),
            AssistantEntry.powerSettingsActions(),
        )
    }

    @Test
    fun powerSettingsActionsAreDistinctSystemSettings() {
        val actions = AssistantEntry.powerSettingsActions()
        assertTrue(actions.all { it.startsWith("android.settings.") })
        assertEquals(actions.size, actions.distinct().size)
    }
}
