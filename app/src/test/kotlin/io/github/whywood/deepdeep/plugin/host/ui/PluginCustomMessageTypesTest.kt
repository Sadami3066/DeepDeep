package io.github.whywood.deepdeep.plugin.host.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCustomMessageTypesTest {
    @Test
    fun buildsSlotIdFromCustomType() {
        assertEquals("chat.message:card", PluginCustomMessageTypes.slotId("card"))
        assertEquals("chat.message:tool.approval", PluginCustomMessageTypes.slotId("tool.approval"))
    }

    @Test
    fun extractsCustomTypeFromSlotId() {
        assertEquals("card", PluginCustomMessageTypes.customTypeOf("chat.message:card"))
        assertEquals("tool.approval", PluginCustomMessageTypes.customTypeOf("chat.message:tool.approval"))
        assertNull(PluginCustomMessageTypes.customTypeOf("card"))
        assertNull(PluginCustomMessageTypes.customTypeOf("chat.message:"))
        assertNull(PluginCustomMessageTypes.customTypeOf("chat.message:Card"))
        assertNull(PluginCustomMessageTypes.customTypeOf("tool.card.content:echo"))
    }

    @Test
    fun validatesCustomTypeFormat() {
        listOf("card", "tool.approval", "card-2", "a_b").forEach { type ->
            assertTrue(type, PluginCustomMessageTypes.isValid(type))
        }
        listOf("", "Card", "2card", "card message", ".card", "card/2").forEach { type ->
            assertFalse(type, PluginCustomMessageTypes.isValid(type))
        }
    }

    @Test
    fun prettyPrintsPayload() {
        val payload = buildJsonObject {
            put("title", "Build finished")
            putJsonObject("detail") { put("ok", true) }
        }
        val pretty = PluginCustomMessageTypes.prettyPayload(payload)
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("\"title\": \"Build finished\""))
        assertTrue(pretty.contains("\"ok\": true"))
        assertTrue(PluginCustomMessageTypes.prettyPayload(JsonObject(emptyMap())).trim().startsWith("{"))
    }

    @Test
    fun truncatesLongPayload() {
        val payload = buildJsonObject { put("text", "x".repeat(200)) }
        val full = PluginCustomMessageTypes.prettyPayload(payload)
        val truncated = PluginCustomMessageTypes.prettyPayload(payload, maxChars = 40)
        assertTrue(full.length > 40)
        assertTrue(truncated.endsWith("…"))
        assertEquals(42, truncated.length)
    }

    @Test
    fun customMessageDefaultsToEmptyPayload() {
        val message = PluginCustomMessage(messageId = "m1", customType = "card")
        assertEquals(JsonObject(emptyMap()), message.payload)
        assertEquals("m1", message.messageId)
        assertEquals("card", message.customType)
    }
}
