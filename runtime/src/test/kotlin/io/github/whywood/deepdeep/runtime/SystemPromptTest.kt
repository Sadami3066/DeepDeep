package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemPromptTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-prompt").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun createsSystemPromptFileWhenMissing() {
        ensureSystemPromptFile(directory)

        val file = File(directory, SYSTEM_PROMPT_FILE)
        assertTrue(file.isFile)
        assertEquals(DEEP_DEEP_SYSTEM_PROMPT, file.readText().trimEnd())
        assertTrue(file.readText().contains("DeepDeep"))
        assertFalse(file.readText().contains("expert coding assistant"))
        assertFalse(file.readText().contains("proot"))
    }

    @Test
    fun keepsUserEditedSystemPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        file.writeText("custom prompt")

        ensureSystemPromptFile(directory)

        assertEquals("custom prompt", file.readText())
    }

    @Test
    fun migratesLegacyDefaultSystemPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        file.writeText(LEGACY_SYSTEM_PROMPTS.single() + "\n")

        ensureSystemPromptFile(directory)

        assertEquals(DEEP_DEEP_SYSTEM_PROMPT, file.readText().trimEnd())
    }

    @Test
    fun keepsUserEditThatResemblesLegacyPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        file.writeText(LEGACY_SYSTEM_PROMPTS.single() + "\nMy extra rule.\n")

        ensureSystemPromptFile(directory)

        assertTrue(file.readText().contains("My extra rule."))
    }

    @Test
    fun ruleSectionCarriesGlobalRules() {
        val rules = buildToolRuleSection().getValue("rules")

        assertTrue(rules.startsWith("<rules>"))
        assertTrue(rules.endsWith("</rules>"))
        assertTrue(rules.contains("- Never type, read, copy or repeat passwords"))
        assertTrue(rules.contains("- Ask the user to confirm before destructive or irreversible actions"))
        assertTrue(rules.contains("- Deliver finished work with present"))
        assertFalse(rules.contains("promptGuidelines"))
    }

    @Test
    fun ruleSectionStaysSmall() {
        val rules = buildToolRuleSection().getValue("rules")
        val bullets = rules.lines().count { it.startsWith("- ") }

        assertTrue("rules should stay curated, got $bullets bullets", bullets <= 20)
        assertTrue("rules should stay small, got ${rules.length} chars", rules.length <= 2500)
    }
}
