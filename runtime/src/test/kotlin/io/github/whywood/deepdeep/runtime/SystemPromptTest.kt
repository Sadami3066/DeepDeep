package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
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
    fun defaultPromptStaysIdentityOnly() {
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.length < 300)
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("DeepDeep"))
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("personal agent"))
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("Android"))
        assertTrue(DEEP_DEEP_SYSTEM_PROMPT.contains("Never invent tool results"))
        assertFalse(DEEP_DEEP_SYSTEM_PROMPT.contains("绝对路径"))
        assertFalse(DEEP_DEEP_SYSTEM_PROMPT.contains("[exit: N]"))
        assertFalse(DEEP_DEEP_SYSTEM_PROMPT.contains("/workspace"))
    }

    @Test
    fun keepsUserEditedSystemPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        file.writeText("custom prompt")

        ensureSystemPromptFile(directory)

        assertEquals("custom prompt", file.readText())
    }

    @Test
    fun ruleSectionCarriesGlobalRulesFirst() {
        val rules = buildToolRuleSection(emptyList()).getValue("rules")

        assertTrue(rules.startsWith("<rules>"))
        assertTrue(rules.endsWith("</rules>"))
        assertTrue(rules.contains("outside that scope"))
        assertFalse(rules.contains("present"))
    }

    @Test
    fun ruleSectionAggregatesToolGuidelines() {
        val rules = buildToolRuleSection(
            listOf(
                tool("bash", "Check the [exit: N] marker on every bash result."),
                tool("read", "Use read to examine files instead of cat or sed."),
            ),
        ).getValue("rules")

        assertTrue(rules.contains("- Check the [exit: N] marker on every bash result."))
        assertTrue(rules.contains("- Use read to examine files instead of cat or sed."))
    }

    @Test
    fun ruleSectionDeduplicatesSharedGuidelines() {
        val shared = "File tool arguments need explicit absolute paths."
        val rules = buildToolRuleSection(listOf(tool("read", shared), tool("write", shared))).getValue("rules")

        assertEquals(1, rules.split(shared).size - 1)
    }

    @Test
    fun ruleSectionLeavesOutGuidelinesOfAbsentTools() {
        val rules = buildToolRuleSection(listOf(tool("job_start", "Use job_output to read new output."))).getValue("rules")

        assertTrue(rules.contains("job_output"))
        assertFalse(rules.contains("timer_create"))
        assertFalse(rules.contains("virtual_screen"))
    }

    private fun tool(name: String, vararg guidelines: String): CodingTool = CodingTool(
        name = name,
        description = "test tool",
        parameters = JsonObject(emptyMap()),
        label = name,
        promptGuidelines = guidelines.toList(),
        execute = { _, _, _, _ -> textResult("ok") },
    )
}
