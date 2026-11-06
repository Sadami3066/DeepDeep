package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundledSkillsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var activeDir: File

    private val assets = mapOf(
        "skills/alpha/SKILL.md" to "# Alpha",
        "skills/alpha/references/api.md" to "# API",
        "skills/beta/SKILL.md" to "# Beta",
        "skills/gamma/notes.md" to "# No skill file",
    )

    @Before
    fun setUp() {
        val root = temporaryFolder.newFolder()
        repoDir = File(root, "skills")
        activeDir = File(root, "agent/skills")
    }

    @Test
    fun copiesSkillsToRepoAndActiveDirectories() = runTest {
        assertEquals(2, skills().sync())

        assertEquals("# Alpha", File(repoDir, "alpha/SKILL.md").readText())
        assertEquals("# API", File(repoDir, "alpha/references/api.md").readText())
        assertEquals("# Beta", File(activeDir, "beta/SKILL.md").readText())
        assertFalse(File(repoDir, "gamma").exists())
    }

    @Test
    fun skipsPluginOwnedDirectories() = runTest {
        pluginOwned(File(repoDir, "alpha"))
        pluginOwned(File(activeDir, "alpha"))

        assertEquals(1, skills().sync())

        assertFalse(File(repoDir, "alpha/SKILL.md").exists())
        assertFalse(File(activeDir, "alpha/SKILL.md").exists())
        assertTrue(File(activeDir, "beta/SKILL.md").isFile)
    }

    @Test
    fun excludedSkillAlsoRemovesPluginOwnedCopy() = runTest {
        pluginOwned(File(repoDir, "alpha"))
        pluginOwned(File(activeDir, "alpha"))

        skills().sync { it != "alpha" }

        assertFalse(File(repoDir, "alpha").exists())
        assertFalse(File(activeDir, "alpha").exists())
        assertTrue(File(activeDir, "beta/SKILL.md").isFile)
    }

    @Test
    fun doesNotOverwriteUserOwnedContent() = runTest {
        File(repoDir, "alpha").mkdirs()
        File(repoDir, "alpha/SKILL.md").writeText("# User alpha")

        assertEquals(1, skills().sync())

        assertEquals("# User alpha", File(repoDir, "alpha/SKILL.md").readText())
        assertFalse(File(activeDir, "alpha").exists())
        assertTrue(File(activeDir, "beta/SKILL.md").isFile)
    }

    @Test
    fun keepsIdenticalCopiesAndRepairsOwnedOnes() = runTest {
        val skills = skills()
        assertEquals(2, skills.sync())
        File(activeDir, "alpha/SKILL.md").delete()

        assertEquals(2, skills.sync())

        assertEquals("# Alpha", File(activeDir, "alpha/SKILL.md").readText())
    }

    @Test
    fun doesNotReactivateSkillTheUserDisabled() = runTest {
        val skills = skills()
        skills.sync()
        File(activeDir, "alpha").deleteRecursively()

        skills.sync()

        assertFalse(File(activeDir, "alpha").exists())
    }

    @Test
    fun removesBundledSkillWhenExcluded() = runTest {
        val skills = skills()
        skills.sync()

        assertEquals(1, skills.sync { it != "alpha" })

        assertFalse(File(repoDir, "alpha").exists())
        assertFalse(File(activeDir, "alpha").exists())
        assertTrue(File(repoDir, "beta/SKILL.md").isFile)
    }

    @Test
    fun keepsUserOwnedSkillWhenExcluded() = runTest {
        File(repoDir, "alpha").mkdirs()
        File(repoDir, "alpha/SKILL.md").writeText("# User alpha")

        assertEquals(1, skills().sync { it != "alpha" })

        assertEquals("# User alpha", File(repoDir, "alpha/SKILL.md").readText())
    }

    @Test
    fun restoresBundledSkillWhenIncludedAgain() = runTest {
        val skills = skills()
        skills.sync()
        skills.sync { it != "alpha" }

        assertEquals(2, skills.sync())

        assertEquals("# Alpha", File(repoDir, "alpha/SKILL.md").readText())
        assertEquals("# Alpha", File(activeDir, "alpha/SKILL.md").readText())
    }

    @Test
    fun marksOwnedDirectories() = runTest {
        skills().sync()

        assertTrue(File(repoDir, "alpha/.deepdeep-bundled").isFile)
        assertTrue(File(activeDir, "alpha/.deepdeep-bundled").isFile)
    }

    @Test
    fun ignoresDirectoriesWithoutSkillFile() = runTest {
        skills().sync()

        assertFalse(File(repoDir, "gamma").exists())
        assertFalse(File(activeDir, "gamma").exists())
    }

    private fun skills(): BundledSkills = BundledSkills(
        repoDir = repoDir,
        activeDir = activeDir,
        list = ::list,
        read = { path -> assets[path]?.toByteArray() },
    )

    private fun list(path: String): List<String>? {
        val prefix = "$path/"
        return assets.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .sorted()
            .takeIf { it.isNotEmpty() }
    }

    private fun pluginOwned(directory: File) {
        directory.mkdirs()
        File(directory, ".deepdeep-plugin").writeText("plugin-id")
    }
}
