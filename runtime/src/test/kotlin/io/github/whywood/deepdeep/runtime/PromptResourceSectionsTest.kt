package io.github.whywood.deepdeep.runtime

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptResourceSectionsTest {
    private val fs = FileSystem.SYSTEM

    private fun writeSkill(agentDir: Path, description: String) {
        val dir = agentDir / "skills" / "demo"
        fs.createDirectories(dir)
        fs.write(dir / "SKILL.md") {
            writeUtf8("---\nname: demo\ndescription: $description\n---\nbody")
        }
    }

    @Test
    fun refreshesSkillsAndProjectContextBetweenTurns() = runTest {
        val root = Files.createTempDirectory("prompt-resources").toOkioPath()
        val agentDir = root / "agent"
        val workspace = root / "workspace"
        fs.createDirectories(workspace)
        writeSkill(agentDir, "First")
        fs.write(workspace / "AGENTS.md") { writeUtf8("Rule one.") }

        val sections = PromptResourceSections(workspace, agentDir, fs, emptySet())
        val initial = sections.current()
        assertTrue(initial.getValue("skills").contains("First"))
        assertTrue(initial.getValue("project_context").contains("Rule one."))

        writeSkill(agentDir, "Second")
        fs.write(workspace / "AGENTS.md") { writeUtf8("Rule two.") }

        val updated = sections.current()
        assertTrue(updated.getValue("skills").contains("Second"))
        assertTrue(updated.getValue("project_context").contains("Rule two."))
    }

    @Test
    fun clearsSkillSectionWhenAllSkillsAreRemoved() = runTest {
        val root = Files.createTempDirectory("prompt-resources").toOkioPath()
        val agentDir = root / "agent"
        val workspace = root / "workspace"
        fs.createDirectories(workspace)
        writeSkill(agentDir, "Only")

        val sections = PromptResourceSections(workspace, agentDir, fs, emptySet())
        assertTrue(sections.current().getValue("skills").contains("Only"))

        fs.deleteRecursively(agentDir / "skills" / "demo")
        assertEquals("", sections.current()["skills"])
    }

    @Test
    fun omitsResourceSectionsThatNeverExisted() = runTest {
        val root = Files.createTempDirectory("prompt-resources").toOkioPath()
        val agentDir = root / "agent"
        val workspace = root / "workspace"
        fs.createDirectories(agentDir)
        fs.createDirectories(workspace)

        val sections = PromptResourceSections(workspace, agentDir, fs, emptySet()).current()
        assertTrue(sections.isEmpty())
    }
}
