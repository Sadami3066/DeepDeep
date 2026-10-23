package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
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
    }

    @Test
    fun keepsUserEditedSystemPrompt() {
        val file = File(directory, SYSTEM_PROMPT_FILE)
        file.writeText("custom prompt")

        ensureSystemPromptFile(directory)

        assertEquals("custom prompt", file.readText())
    }

    @Test
    fun toolSectionsCarrySnippetsRulesAndWrapping() {
        val tools = listOf(
            tool("read", "Read file contents", listOf("Use read to examine files instead of cat or sed.")),
            tool("bash", "Execute bash commands", null),
        )

        val sections = buildToolPromptSections(tools)

        assertTrue(sections.getValue("tools").startsWith("<tools>"))
        assertTrue(sections.getValue("tools").contains("- read: Read file contents"))
        assertTrue(sections.getValue("tools").contains("other custom tools"))
        assertTrue(sections.getValue("rules").startsWith("<rules>"))
        assertTrue(sections.getValue("rules").contains("- Use read to examine files instead of cat or sed."))
        assertTrue(sections.getValue("rules").contains("- Be concise in your responses"))
        assertTrue(sections.getValue("rules").contains("- Show file paths clearly when working with files"))
    }

    @Test
    fun toolSectionsDeduplicateRules() {
        val tools = listOf(
            tool("a", "Tool A", listOf("Same rule.")),
            tool("b", "Tool B", listOf("Same rule.", "Other rule.")),
        )

        val rules = buildToolPromptSections(tools).getValue("rules")
        assertEquals(1, Regex("- Same rule\\.").findAll(rules).count())
        assertTrue(rules.contains("- Other rule."))
    }

    private fun tool(name: String, snippet: String, guidelines: List<String>?): CodingTool = CodingTool(
        name = name,
        description = name,
        parameters = buildJsonObject { },
        label = name,
        promptSnippet = snippet,
        promptGuidelines = guidelines,
        execute = { _, _, _, _ -> dev.pi.coding.tools.textResult("ok") },
    )
}
