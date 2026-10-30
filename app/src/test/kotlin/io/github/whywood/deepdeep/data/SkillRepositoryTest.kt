package io.github.whywood.deepdeep.data

import java.io.File
import java.io.IOException
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

    private fun agentSkillsDir(): File = File(agentDir, "skills")

    private fun homeSkillsDir(): File = File(homeDir, ".pi/skills")

    private fun workspaceSkillsDir(): File = File(workspaceDir, ".pi/skills")

    private fun writeActiveSkill(root: File, id: String, description: String = "Active skill"): File {
        val directory = File(root, id)
        directory.mkdirs()
        File(directory, "SKILL.md")
            .writeBytes(SkillTestFixtures.skillMarkdown(name = id, description = description))
        return directory
    }

    @Test
    fun installWritesRepositoryAndListsSkill() = runTest {
        val result = repository.install(skillZip(), "alpha.zip")
        assertTrue(result is SkillArchiveResult.Success)
        assertTrue(File(repoDir, "alpha/SKILL.md").isFile)
        assertTrue(File(repoDir, "alpha/references/notes.md").isFile)
        val skills = repository.list().getOrThrow()
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
        assertTrue(repository.list().getOrThrow().isEmpty())
    }

    @Test
    fun installUpsertsAndRefreshesActiveCopies() = runTest {
        repository.install(skillZip("First"), "alpha.zip")
        assertTrue(repository.setGlobalEnabled("alpha", true))
        val updated = repository.install(skillZip("Second"), "alpha.zip")
        assertTrue(updated is SkillArchiveResult.Success)
        assertEquals("Second", repository.list().getOrThrow().single().description)
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
        assertTrue(repository.list().getOrThrow().single().globalEnabled)
        assertTrue(repository.setGlobalEnabled("alpha", false))
        assertFalse(globalFile().exists())
        assertFalse(repository.list().getOrThrow().single().globalEnabled)
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
        assertTrue(repository.list().getOrThrow().isEmpty())
        assertNull(repository.readContent("alpha"))
    }

    @Test
    fun syncImportsActiveSkillsFromAgentHomeAndWorkspaces() = runTest {
        writeActiveSkill(agentSkillsDir(), "agent-skill", "From agent")
        writeActiveSkill(homeSkillsDir(), "home-skill", "From home")
        writeActiveSkill(workspaceSkillsDir(), "workspace-skill", "From workspace")

        assertEquals(3, repository.syncActiveSkills())

        val skills = repository.list().getOrThrow().associateBy { it.id }
        assertEquals(setOf("agent-skill", "home-skill", "workspace-skill"), skills.keys)
        assertTrue(File(repoDir, "agent-skill/SKILL.md").isFile)
        assertTrue(File(repoDir, "home-skill/SKILL.md").isFile)
        assertTrue(File(repoDir, "workspace-skill/SKILL.md").isFile)
        assertTrue(skills.getValue("agent-skill").globalEnabled)
        assertFalse(skills.getValue("home-skill").globalEnabled)
        assertEquals(1L, repository.changes.value)
    }

    @Test
    fun syncMergesDuplicateIdsAndKeepsFirstSource() = runTest {
        writeActiveSkill(agentSkillsDir(), "shared", "Agent copy")
        writeActiveSkill(workspaceSkillsDir(), "shared", "Workspace copy")

        assertEquals(1, repository.syncActiveSkills())
        assertTrue(File(repoDir, "shared/SKILL.md").readText().contains("Agent copy"))
        assertEquals("Agent copy", repository.list().getOrThrow().single().description)
        assertEquals(0, repository.syncActiveSkills())
        assertEquals(1L, repository.changes.value)
    }

    @Test
    fun syncSkipsPluginMarkedActiveCopies() = runTest {
        val pluginSkill = writeActiveSkill(agentSkillsDir(), "deepdeep-feishu", "Plugin skill")
        File(pluginSkill, ".deepdeep-plugin").writeText("feishu")

        assertEquals(0, repository.syncActiveSkills())
        assertFalse(File(repoDir, "deepdeep-feishu").exists())
        assertTrue(repository.list().getOrThrow().isEmpty())
        assertEquals(0L, repository.changes.value)
    }

    @Test
    fun syncSkipsInvalidActiveSkills() = runTest {
        writeActiveSkill(agentSkillsDir(), "valid", "Valid skill")
        val missingDescription = File(agentSkillsDir(), "missing-description").apply { mkdirs() }
        File(missingDescription, "SKILL.md").writeBytes(
            SkillTestFixtures.skillMarkdown(name = "missing-description", description = null),
        )
        File(agentSkillsDir(), "missing-skill-md").mkdirs()
        writeActiveSkill(agentSkillsDir(), "unsafe name", "Unsafe id")
        writeActiveSkill(agentSkillsDir(), ".hidden", "Hidden")

        assertEquals(1, repository.syncActiveSkills())
        assertEquals(listOf("valid"), repository.list().getOrThrow().map { it.id })
    }

    @Test
    fun syncIfChangedImportsOnlyWhenSourcesChange() = runTest {
        writeActiveSkill(agentSkillsDir(), "first", "First")

        assertEquals(1, repository.syncIfChanged())
        assertEquals(0, repository.syncIfChanged())

        writeActiveSkill(agentSkillsDir(), "second", "Second")

        assertEquals(1, repository.syncIfChanged())
        assertEquals(0, repository.syncIfChanged())
        assertEquals(listOf("first", "second"), repository.list().getOrThrow().map { it.id })
        assertEquals(2L, repository.changes.value)
    }

    @Test
    fun changesFlowTracksRepositoryMutations() = runTest {
        assertEquals(0L, repository.changes.value)

        assertTrue(repository.install(skillZip(), "alpha.zip") is SkillArchiveResult.Success)
        assertEquals(1L, repository.changes.value)

        assertTrue(repository.setGlobalEnabled("alpha", true))
        assertEquals(2L, repository.changes.value)

        assertTrue(repository.applyProjectSkills(workspaceDir.absolutePath, setOf("alpha")))
        assertEquals(3L, repository.changes.value)

        assertFalse(repository.setGlobalEnabled("missing", true))
        assertEquals(3L, repository.changes.value)

        assertTrue(repository.delete("alpha"))
        assertEquals(4L, repository.changes.value)
    }

    @Test
    fun listReportsFailureInsteadOfEmptyList() = runTest {
        val failingRepository = SkillRepository(
            repoDir = object : File(repoDir.path) {
                override fun listFiles(): Array<File>? = throw IOException("boom")
            },
            agentDir = agentDir,
            homeDir = homeDir,
            workspacesDir = workspacesDir,
        )
        val result = failingRepository.list()
        assertTrue(result.isFailure)
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
