package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSkillsTest {
    @Test
    fun writesBothSkillCopies() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")

        assertTrue(BuiltinSkills.ensure(repoDir, agentDir, "workshop", "# Workshop skill\n"))

        assertEquals("# Workshop skill\n", File(repoDir, "workshop/SKILL.md").readText())
        assertEquals("# Workshop skill\n", File(agentDir, "skills/workshop/SKILL.md").readText())
    }

    @Test
    fun skipsRewriteWhenContentMatches() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")

        assertTrue(BuiltinSkills.ensure(repoDir, agentDir, "workshop", "same content\n"))
        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "workshop", "same content\n"))
    }

    @Test
    fun updatesChangedContentInBothCopies() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")
        BuiltinSkills.ensure(repoDir, agentDir, "workshop", "old content\n")

        assertTrue(BuiltinSkills.ensure(repoDir, agentDir, "workshop", "new content\n"))

        assertEquals("new content\n", File(repoDir, "workshop/SKILL.md").readText())
        assertEquals("new content\n", File(agentDir, "skills/workshop/SKILL.md").readText())
    }

    @Test
    fun doesNotReenableDisabledCopy() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")
        BuiltinSkills.ensure(repoDir, agentDir, "workshop", "content\n")
        assertTrue(File(agentDir, "skills/workshop/SKILL.md").delete())

        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "workshop", "content\n"))

        assertFalse(File(agentDir, "skills/workshop/SKILL.md").exists())
        assertEquals("content\n", File(repoDir, "workshop/SKILL.md").readText())
    }

    @Test
    fun writesNestedFilesToBothCopies() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")
        val files = listOf(
            BuiltinSkillFile("SKILL.md", "# Skill\n"),
            BuiltinSkillFile("references/api.md", "api\n"),
            BuiltinSkillFile("examples/hello.html", "<html></html>\n"),
        )

        assertTrue(BuiltinSkills.ensure(repoDir, agentDir, "workshop", files))

        assertEquals("# Skill\n", File(repoDir, "workshop/SKILL.md").readText())
        assertEquals("api\n", File(repoDir, "workshop/references/api.md").readText())
        assertEquals("<html></html>\n", File(repoDir, "workshop/examples/hello.html").readText())
        assertEquals("# Skill\n", File(agentDir, "skills/workshop/SKILL.md").readText())
        assertEquals("api\n", File(agentDir, "skills/workshop/references/api.md").readText())
        assertEquals("<html></html>\n", File(agentDir, "skills/workshop/examples/hello.html").readText())
    }

    @Test
    fun updatesChangedNestedFiles() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")
        val old = listOf(
            BuiltinSkillFile("SKILL.md", "old\n"),
            BuiltinSkillFile("references/api.md", "old api\n"),
        )
        val new = listOf(
            BuiltinSkillFile("SKILL.md", "old\n"),
            BuiltinSkillFile("references/api.md", "new api\n"),
        )
        BuiltinSkills.ensure(repoDir, agentDir, "workshop", old)

        assertTrue(BuiltinSkills.ensure(repoDir, agentDir, "workshop", new))

        assertEquals("new api\n", File(repoDir, "workshop/references/api.md").readText())
        assertEquals("new api\n", File(agentDir, "skills/workshop/references/api.md").readText())
    }

    @Test
    fun doesNotReenableDisabledSkillWithNewFiles() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")
        BuiltinSkills.ensure(repoDir, agentDir, "workshop", listOf(BuiltinSkillFile("SKILL.md", "old\n")))
        assertTrue(File(agentDir, "skills/workshop/SKILL.md").delete())

        assertTrue(
            BuiltinSkills.ensure(
                repoDir,
                agentDir,
                "workshop",
                listOf(
                    BuiltinSkillFile("SKILL.md", "old\n"),
                    BuiltinSkillFile("references/new.md", "new\n"),
                ),
            ),
        )

        assertEquals("new\n", File(repoDir, "workshop/references/new.md").readText())
        assertFalse(File(agentDir, "skills/workshop/references/new.md").exists())
        assertFalse(File(agentDir, "skills/workshop/SKILL.md").exists())
    }

    @Test
    fun rejectsUnsafePaths() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")

        listOf("../escape.md", "/absolute.md", "a//b.md", "a/./b.md", "..").forEach { path ->
            assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "workshop", listOf(BuiltinSkillFile(path, "x"))))
        }
        assertTrue(repoDir.listFiles().isNullOrEmpty())
        assertTrue(agentDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun rejectsDuplicateOrEmptyFiles() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")

        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "workshop", emptyList()))
        assertFalse(
            BuiltinSkills.ensure(
                repoDir,
                agentDir,
                "workshop",
                listOf(BuiltinSkillFile("SKILL.md", "a"), BuiltinSkillFile("SKILL.md", "b")),
            ),
        )
        assertTrue(repoDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun rejectsInvalidId() {
        val repoDir = tempDir("workshop-repo")
        val agentDir = tempDir("workshop-agent")

        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "Bad/Id", "content\n"))
        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "UPPER", "content\n"))
        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "-leading", "content\n"))
        assertFalse(BuiltinSkills.ensure(repoDir, agentDir, "a".repeat(49), "content\n"))
        assertTrue(repoDir.listFiles().isNullOrEmpty())
        assertTrue(agentDir.listFiles().isNullOrEmpty())
    }

    private fun tempDir(prefix: String): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        dir.deleteOnExit()
        return dir
    }
}
