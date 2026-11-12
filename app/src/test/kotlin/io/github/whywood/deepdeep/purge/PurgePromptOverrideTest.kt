package io.github.whywood.deepdeep.purge

import io.github.whywood.deepdeep.sdk.coding.prompt.SystemPromptInput
import io.github.whywood.deepdeep.sdk.coding.prompt.buildSystemPrompt
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgePromptOverrideTest {
    @Test
    fun `custom rules section replaces the host rules`() {
        val input = SystemPromptInput(
            preamble = "HOST PREAMBLE",
            cwd = "/".toPath(),
            customSections = mapOf("rules" to "PURGE RULES"),
        )

        val sections = buildSystemPrompt(input)

        assertEquals("HOST PREAMBLE", sections["preamble"])
        val rules = sections["rules"].orEmpty()
        assertTrue(rules.contains("PURGE RULES"))
        assertFalse(rules.contains("Be concise in your responses"))
    }

    @Test
    fun `purge sections land in the android system prompt`() {
        val injected = PurgePolicy.sections(PurgeTuning(enabled = true), "AUTHENTIC PURGE INJECTION")
        val input = SystemPromptInput(
            preamble = injected.getValue("preamble"),
            cwd = "/".toPath(),
            customSections = mapOf("rules" to injected.getValue("rules")),
        )

        val sections = buildSystemPrompt(input)
        val preamble = sections["preamble"].orEmpty()

        assertTrue(preamble.contains("AUTHENTIC PURGE INJECTION"))
        assertTrue(preamble.contains("dsh_boost"))
        val rules = sections["rules"].orEmpty()
        assertTrue(rules.contains(PurgeText.RULES_BODY))
        assertFalse(rules.contains("Be concise"))
    }
}
