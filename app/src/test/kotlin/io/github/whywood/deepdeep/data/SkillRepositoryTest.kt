package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var agentDir: File
    private lateinit var homeDir: File
    private lateinit var workspacesDir: File
    private lateinit var workspaceDir: File
    private lateinit var repository: SkillRepository

    @Before
    fun setUp() {
        val root = temporaryFolder.newFolder()
        repoDir = File(root, "skills")
        agentDir = File(root, "agent")
        homeDir = File(root, "home")
        workspacesDir = File(root, "workspaces")
        workspaceDir = File(workspacesDir, "alpha")
        listOf(repoDir, agentDir, homeDir, workspaceDir).forEach { it.mkdirs() }
        repository = SkillRepository(
            repoDir = repoDir,
            agentDir = agentDir,
            homeDir = homeDir,
            workspacesDir = workspacesDir,
        )
    }

    private fun skillZip(description: String = "Alpha skill"): ByteArray = SkillTestFixtures.skillZip(
        name = "alpha",
        description = description,
        extraEntries = listOf("references/notes.md" to "notes".toByteArray()),
    )

    private fun globalFile(): File = File(File(agentDir, "skills"), "alpha/SKILL.md")

    private fun homeProjectFile(): File = File(homeDir, ".pi/skills/alpha/SKILL.md")

    private fun workspaceProjectFile(): File = File(workspaceDir, ".pi/skills/alpha/SKILL.md")

    @Test
    fun installWritesRepositoryAndListsSkill() = runTest {
        val result = repository.install(skillZip(), "alpha.zip")
        assertTrue(result is SkillArchiveResult.Success)
        assertTrue(File(repoDir, "alpha/SKILL.md").isFile)
        assertTrue(File(repoDir, "alpha/references/notes.md").isFile)
        val skills = repository.list()
        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("alpha", skill.id)
        assertEquals("alpha", skill.name)
        assertEquals("Alpha skill", skill.description)
        assertFalse(skill.disableModelInvocation)
        assertFalse(skill.globalEnabled)
        assertTrue(repository.readContent("alpha")!!.contains("Alpha body"))
    }

    @Test
    fun installRejectsInvalidArchive() = runTest {
        val result = repository.install(byteArrayOf(1, 2, 3, 4), "bad.zip")
        assertTrue(result is SkillArchiveResult.Failure)
        assertEquals(SkillArchiveError.NotZip, (result as SkillArchiveResult.Failure).error)
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun installUpsertsAndRefreshesActiveCopies() = runTest {
        repository.install(skillZip("First"), "alpha.zip")
        assertTrue(repository.setGlobalEnabled("alpha", true))
        val updated = repository.install(skillZip("Second"), "alpha.zip")
        assertTrue(updated is SkillArchiveResult.Success)
        assertEquals("Second", repository.list().single().description)
        assertTrue(globalFile().readText().contains("Second"))
    }

    @Test
    fun globalEnableCopiesAndRemovesProjectCopies() = runTest {
        repository.install(skillZip(), "alpha.zip")
        homeProjectFile().parentFile?.mkdirs()
        homeProjectFile().writeText("stale")
        workspaceProjectFile().parentFile?.mkdirs()
        workspaceProjectFile().writeText("stale")
        assertTrue(repository.setGlobalEnabled("alpha", true))
        assertTrue(globalFile().isFile)
        assertFalse(homeProjectFile().exists())
        assertFalse(workspaceProjectFile().exists())
        assertTrue(repository.list().single().globalEnabled)
        assertTrue(repository.setGlobalEnabled("alpha", false))
        assertFalse(globalFile().exists())
        assertFalse(repository.list().single().globalEnabled)
    }

    @Test
    fun projectTogglesCopyAndRemove() = runTest {
        repository.install(skillZip(), "alpha.zip")
        assertTrue(repository.applyProjectSkills(workspaceDir.absolutePath, setOf("alpha")))
        assertTrue(workspaceProjectFile().isFile)
        assertEquals(setOf("alpha"), repository.projectEnabledIds(workspaceDir.absolutePath))
        assertTrue(repository.applyProjectSkills(workspaceDir.absolutePath, emptySet()))
        assertFalse(workspaceProjectFile().exists())
        assertTrue(repository.projectEnabledIds(workspaceDir.absolutePath).isEmpty())
    }

    @Test
    fun deleteRemovesRepositoryGlobalAndProjectCopies() = runTest {
        repository.install(skillZip(), "alpha.zip")
        assertTrue(repository.setGlobalEnabled("alpha", true))
        assertTrue(repository.applyProjectSkills(workspaceDir.absolutePath, setOf("alpha")))
        assertTrue(workspaceProjectFile().isFile)
        assertTrue(repository.delete("alpha"))
        assertFalse(File(repoDir, "alpha").exists())
        assertFalse(globalFile().exists())
        assertFalse(workspaceProjectFile().exists())
        assertTrue(repository.list().isEmpty())
        assertNull(repository.readContent("alpha"))
    }

    @Test
    fun rejectsUnsafeIds() = runTest {
        assertFalse(repository.setGlobalEnabled("../alpha", true))
        assertFalse(repository.delete("../alpha"))
        assertNull(repository.readContent("../alpha"))
        assertTrue(repository.projectEnabledIds("").isEmpty())
        assertFalse(repository.applyProjectSkills("", setOf("alpha")))
    }
}
