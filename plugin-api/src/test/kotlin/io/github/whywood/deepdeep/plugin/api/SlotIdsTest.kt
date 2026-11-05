package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotIdsTest {
    @Test
    fun buildsSlotIds() {
        assertEquals("setup.content", SlotIds.setupContent())
        assertEquals("setup.content", SlotIds.SETUP_CONTENT)
        assertEquals("plugin.detail.section:account", SlotIds.detailSection("account"))
        assertEquals("tool.card.content:sample_echo", SlotIds.toolCard("sample_echo"))
        assertEquals("chat.message:card", SlotIds.chatMessage("card"))
    }

    @Test
    fun matchesSupportedSlotIds() {
        assertTrue(SlotIds.matches(SlotIds.setupContent()))
        assertTrue(SlotIds.matches(SlotIds.detailSection("account")))
        assertTrue(SlotIds.matches(SlotIds.toolCard("sample_echo")))
        assertTrue(SlotIds.matches(SlotIds.chatMessage("card")))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteSlotIds() {
        listOf(
            "",
            "composer.action",
            "plugin.page",
            "plugin.detail.section",
            "plugin.detail.section:",
            "tool.card.content:",
            "chat.message:",
            "setup.content.extra",
            "tool.card.content:sample_echo.extra",
        ).forEach { slotId ->
            assertFalse(slotId, SlotIds.matches(slotId))
        }
    }
}
