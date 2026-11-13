package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(PluginExperimentalApi::class)
class PluginContractTest {
    @Test
    fun capabilitiesExposeExperimentalPrompt() {
        assertNotNull(PluginCapability.fromWire("prompt"))
        assertEquals(PluginCapability.PROMPT, PluginCapability.fromWire("prompt"))
        assertEquals(PluginCapability.PRIVILEGE, PluginCapability.fromWire("privilege"))
        assertEquals(PluginCapability.CREDENTIALS, PluginCapability.fromWire("credentials"))
        assertNull(PluginCapability.fromWire("unknown"))
        assertEquals(38, PluginCapability.ALL.size)
        assertEquals(18, PluginPermission.ALL.size)
    }

    @Test
    fun localizedTextRequiresDefaultAndResolvesLanguage() {
        val text = localizedText("Hello", zh = "你好")
        assertEquals("Hello", text.resolve("en"))
        assertEquals("你好", text.resolve("zh"))
        assertEquals("Hello", text.resolve(null))
        assertTrue(text.hasDefault())
    }

    @Test
    fun pluginToolFallsBackToNameAsLabel() {
        val tool = pluginTool(
            name = "sample_echo",
            description = "echo",
            parameters = kotlinx.serialization.json.JsonObject(emptyMap()),
        ) {
            textResult(it.args.toString())
        }
        assertEquals("sample_echo", tool.label)
        assertEquals(PluginToolExposure.DIRECT, tool.exposure)
    }

    @Test
    fun contributionsAndPromptOverrideUseValueDefaults() {
        val sections = contributions()
        assertTrue(sections.tools.isEmpty())
        assertEquals(PluginHooks.None, sections.hooks)

        val override = promptOverride(rules = "rules", sections = listOf(promptSection("a", "b")), priority = 3)
        assertEquals("rules", override.rules)
        assertEquals(3, override.priority)
        assertNull(override.preamble)
    }

    @Test
    fun chatFactoriesKeepDefaults() {
        val display = chatDisplay("card", text = "hi")
        assertEquals("card", display.customType)
        assertEquals("hi", display.text)

        val options = chatMessageOptions(display = display)
        assertTrue(options.triggerTurn)
        assertEquals(ChatDelivery.STEER, options.deliverAs)
        assertEquals(ChatSendKind.QUEUED, ChatSendKind.fromWire("queued"))
        assertFalse(PluginCapability.PROMPT.wire.isEmpty())
    }
}
