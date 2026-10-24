package io.github.whywood.deepdeep.runtime

import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopAgentPromptTest {
    @Test
    fun promptNamesTheAppAndItsFolder() {
        val creation = creation()
        val prompt = buildPrompt(WorkshopRunMode.SANDBOX, creation, "Add a header", null)

        assertTrue(
            prompt.startsWith(
                "You are editing the DeepDeep workshop app Garden (board) located at " +
                    creation.directory.absolutePath + ".",
            ),
        )
        assertTrue(prompt.contains("Follow the AGENTS.md in this folder."))
        assertTrue(prompt.endsWith("Add a header"))
        assertFalse(prompt.contains("Context from the page"))
    }

    @Test
    fun promptIncludesPageContext() {
        val context = buildJsonObject {
            put("selection", "card-1")
            put("action", "rename")
        }
        val prompt = buildPrompt(WorkshopRunMode.SANDBOX, creation(), "Rename the card", context)

        assertTrue(prompt.contains("Context from the page:\n" + context.toString()))
    }

    @Test
    fun promptSkipsContextWhenAbsent() {
        val prompt = buildPrompt(WorkshopRunMode.FULL, creation(), "Fix the build", null)

        assertFalse(prompt.contains("Context from the page"))
        assertTrue(prompt.contains("Fix the build"))
    }

    @Test
    fun promptUsesTypeWireName() {
        val creation = creation(type = WorkshopType.WORKFLOW)
        val prompt = buildPrompt(WorkshopRunMode.SANDBOX, creation, "Run it", null)

        assertTrue(prompt.contains("(workflow)"))
    }

    @Test
    fun promptCapsOversizedContext() {
        val context = buildJsonObject { put("payload", "x".repeat(40_000)) }
        val prompt = buildPrompt(WorkshopRunMode.SANDBOX, creation(), "Inspect", context)

        assertTrue(prompt.contains("Context from the page:"))
        assertTrue(prompt.length < 34_000)
    }

    private fun creation(
        type: WorkshopType = WorkshopType.BOARD,
        name: String = "Garden",
    ): WorkshopCreation {
        val directory = Files.createTempDirectory("workshop-prompt-test").toFile()
        directory.deleteOnExit()
        return WorkshopCreation(
            manifest = WorkshopManifest(id = "garden", name = name, type = type),
            directory = directory,
        )
    }
}
