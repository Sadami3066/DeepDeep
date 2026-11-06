package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotIdsTest {
    @Test
    fun buildsSlotIds() {
        assertEquals("tool.card.content:sample_echo", SlotIds.toolCard("sample_echo"))
        assertEquals("chat.message:card", SlotIds.chatMessage("card"))
    }

    @Test
    fun matchesSupportedSlotIds() {
        assertTrue(SlotIds.matches(SlotIds.toolCard("sample_echo")))
        assertTrue(SlotIds.matches(SlotIds.chatMessage("sample.card")))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteSlotIds() {
        listOf(
            "",
            "setup.content",
            "composer.action",
            "plugin.element:home",
            "plugin.page",
            "plugin.page:",
            "plugin.page:home",
            "tool.card.content:",
            "chat.message:",
            "tool.card.content:sample_echo.extra",
        ).forEach { slotId ->
            assertFalse(slotId, SlotIds.matches(slotId))
        }
    }
}
