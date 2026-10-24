package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCatalogTest {
    @Test
    fun expandsAgentSkillWithArguments() = runTest {
        val agentDir = tempDir("workshop-agent")
        writeSkill(File(agentDir, "skills/foo/SKILL.md"), "foo", "Foo helper", "FOO BODY")
        val catalog = SkillCatalog(agentDir)

        val expansion = catalog.expand(tempDir("workshop-project").absolutePath, "foo", "  extra args  ")

        assertNotNull(expansion)
        assertEquals("foo", expansion!!.name)
        assertTrue(expansion.prompt.contains("<skill name=\"foo\""))
        assertTrue(expansion.prompt.contains("FOO BODY"))
        assertTrue(expansion.prompt.contains("extra args"))
        assertTrue(expansion.prompt.endsWith("extra args"))
    }

    @Test
    fun expansionOmitsArgumentsWhenBlank() = runTest {
        val agentDir = tempDir("workshop-agent")
        writeSkill(File(agentDir, "skills/foo/SKILL.md"), "foo", "Foo helper", "FOO BODY")

        val expansion = SkillCatalog(agentDir).expand(tempDir("workshop-project").absolutePath, "foo", "   ")

        assertNotNull(expansion)
        assertTrue(expansion!!.prompt.endsWith("FOO BODY\n</skill>"))
    }

    @Test
    fun expandReadsFreshSkillContent() = runTest {
        val agentDir = tempDir("workshop-agent")
        val skillFile = File(agentDir, "skills/foo/SKILL.md")
        writeSkill(skillFile, "foo", "Foo helper", "OLD BODY")
        val catalog = SkillCatalog(agentDir)
        val project = tempDir("workshop-project").absolutePath

        assertTrue(catalog.expand(project, "foo", "")!!.prompt.contains("OLD BODY"))

        writeSkill(skillFile, "foo", "Foo helper", "NEW BODY")
        val refreshed = catalog.expand(project, "foo", "")

        assertTrue(refreshed!!.prompt.contains("NEW BODY"))
        assertTrue(!refreshed.prompt.contains("OLD BODY"))
    }

    @Test
    fun expandsProjectSkill() = runTest {
        val agentDir = tempDir("workshop-agent")
        val projectDir = tempDir("workshop-project")
        writeSkill(File(projectDir, ".pi/skills/bar/SKILL.md"), "bar", "Bar helper", "BAR BODY")

        val expansion = SkillCatalog(agentDir).expand(projectDir.absolutePath, "bar", "")

        assertNotNull(expansion)
        assertEquals("bar", expansion!!.name)
        assertTrue(expansion.prompt.contains("BAR BODY"))
        assertTrue(expansion.prompt.contains(File(projectDir, ".pi/skills/bar").absolutePath))
    }

    @Test
    fun missingSkillReturnsNull() = runTest {
        val agentDir = tempDir("workshop-agent")
        writeSkill(File(agentDir, "skills/foo/SKILL.md"), "foo", "Foo helper", "FOO BODY")
        val catalog = SkillCatalog(agentDir)

        assertNull(catalog.expand(tempDir("workshop-project").absolutePath, "nope", ""))
        assertNull(catalog.expand(tempDir("workshop-project").absolutePath, "", ""))
    }

    @Test
    fun listsSkillsWithDescriptions() = runTest {
        val agentDir = tempDir("workshop-agent")
        val projectDir = tempDir("workshop-project")
        writeSkill(File(agentDir, "skills/foo/SKILL.md"), "foo", "Foo helper", "FOO BODY")
        writeSkill(File(projectDir, ".pi/skills/bar/SKILL.md"), "bar", "Bar helper", "BAR BODY")

        val listed = SkillCatalog(agentDir).list(projectDir.absolutePath).toMap()

        assertEquals("Foo helper", listed["foo"])
        assertEquals("Bar helper", listed["bar"])
    }

    private fun writeSkill(file: File, name: String, description: String, body: String) {
        file.parentFile.mkdirs()
        file.writeText("---\nname: $name\ndescription: $description\n---\n\n$body\n")
    }

    private fun tempDir(prefix: String): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        dir.deleteOnExit()
        return dir
    }
}
