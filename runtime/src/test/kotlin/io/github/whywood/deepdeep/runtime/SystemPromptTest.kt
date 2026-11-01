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
    fun defaultPromptExplainsWorkspacePathsAndExitCodes() {
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("绝对路径"))
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("[exit: N]"))
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
        LEGACY_SYSTEM_PROMPTS.forEach { legacy ->
            file.writeText(legacy + "\n")

            ensureSystemPromptFile(directory)

            assertEquals(DEEP_DEEP_SYSTEM_PROMPT, file.readText().trimEnd())
        }
    }

    @Test
    fun keepsUserEditThatResemblesLegacyPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        LEGACY_SYSTEM_PROMPTS.forEach { legacy ->
            file.writeText(legacy + "\nMy extra rule.\n")

            ensureSystemPromptFile(directory)

            assertTrue(file.readText().contains("My extra rule."))
        }
    }

    @Test
    fun ruleSectionCarriesGlobalRules() {
        val rules = buildToolRuleSection(setOf("screen_observe", "present", "bash", "create_goal", "schedule_create", "job_start")).getValue("rules")

        assertTrue(rules.startsWith("<rules>"))
        assertTrue(rules.endsWith("</rules>"))
        assertTrue(rules.contains("- Never type, read, copy or repeat passwords"))
        assertTrue(rules.contains("outside that scope"))
        assertTrue(rules.contains("Use present when delivering a file"))
        assertFalse(rules.contains("promptGuidelines"))
    }

    @Test
    fun rulesOnlyMentionAvailableCapabilities() {
        val rules = buildToolRuleSection(setOf("read", "write", "bash", "job_start")).getValue("rules")
        assertTrue(rules.contains("Linux /bin/sh"))
        assertTrue(rules.contains("job_start"))
        assertFalse(rules.contains("schedule_create"))
        assertFalse(rules.contains("create_goal"))
        assertFalse(rules.contains("present"))
        assertFalse(rules.contains("virtual_screen"))
        assertFalse(rules.contains("fails twice"))
    }
    @Test
    fun androidShellRulesRemainAvailableWithoutLinux() {
        val rules = buildToolRuleSection(setOf("root_shell")).getValue("rules")
        assertTrue(rules.contains("Android system shell"))
        assertTrue(rules.contains("upstream output may already be incomplete"))
        assertTrue(rules.contains("do not automatically retry"))
        assertFalse(rules.contains("Linux /bin/sh"))
    }

}
