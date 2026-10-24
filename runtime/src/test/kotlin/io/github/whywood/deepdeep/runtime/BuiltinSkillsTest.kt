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
