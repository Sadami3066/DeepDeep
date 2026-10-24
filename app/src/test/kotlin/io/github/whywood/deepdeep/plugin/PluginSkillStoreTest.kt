package io.github.whywood.deepdeep.plugin

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginSkillStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val activeDir get() = File(temporaryFolder.root, "agent/skills")
    private val repoDir get() = File(temporaryFolder.root, "skills")

    private fun store(): PluginSkillStore = PluginSkillStore(activeDir, repoDir)

    @Test
    fun syncWritesRepoAndActiveWithMarker() = runTest {
        store().sync(
            pluginId = "feishu",
            skills = listOf(PluginSkill("deepdeep-feishu", "---\nname: x\ndescription: y\n---\nbody")),
            previousIds = emptyList(),
            active = true,
        )
        listOf(File(repoDir, "deepdeep-feishu"), File(activeDir, "deepdeep-feishu")).forEach { dir ->
            assertTrue(File(dir, "SKILL.md").isFile)
            assertEquals("feishu", File(dir, ".deepdeep-plugin").readText())
        }
    }

    @Test
    fun inactiveSyncKeepsRepoOnly() = runTest {
        store().sync("feishu", listOf(PluginSkill("a", "x")), emptyList(), active = false)
        assertTrue(File(repoDir, "a/SKILL.md").isFile)
        assertFalse(File(activeDir, "a").exists())
    }

    @Test
    fun setActiveTogglesActiveCopy() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("a", "x")), emptyList(), active = false)
        store.setActive("feishu", listOf(PluginSkill("a", "x")), enabled = true)
        assertTrue(File(activeDir, "a/SKILL.md").isFile)
        assertEquals("feishu", File(activeDir, "a/.deepdeep-plugin").readText())

        store.setActive("feishu", listOf(PluginSkill("a", "x")), enabled = false)
        assertFalse(File(activeDir, "a").exists())
        assertTrue(File(repoDir, "a/SKILL.md").isFile)
    }

    @Test
    fun syncRemovesStaleSkillsForPlugin() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("a", "x")), emptyList(), active = true)
        store.sync("feishu", listOf(PluginSkill("b", "y")), listOf("a"), active = true)
        assertFalse(File(repoDir, "a").exists())
        assertFalse(File(activeDir, "a").exists())
        assertTrue(File(repoDir, "b").isDirectory)
        assertTrue(File(activeDir, "b").isDirectory)
    }

    @Test
    fun ensureRepoMigratesOldActiveDirectory() = runTest {
        val active = File(activeDir, "deepdeep-feishu")
        active.mkdirs()
        File(active, "SKILL.md").writeText("old body")
        File(active, ".deepdeep-plugin").writeText("feishu")

        store().ensureRepo("feishu", listOf(PluginSkill("deepdeep-feishu", "new body")))

        assertTrue(File(repoDir, "deepdeep-feishu/SKILL.md").isFile)
        assertEquals("feishu", File(repoDir, "deepdeep-feishu/.deepdeep-plugin").readText())
        assertTrue(File(active, "SKILL.md").isFile)
    }

    @Test
    fun ensureRepoCreatesMissingRepoSkill() = runTest {
        store().ensureRepo("feishu", listOf(PluginSkill("a", "body")))
        assertEquals("body", File(repoDir, "a/SKILL.md").readText())
        assertFalse(File(activeDir, "a").exists())
    }

    @Test
    fun syncRefusesToOverwriteForeignRepoSkill() = runTest {
        val foreign = File(repoDir, "taken")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")
        val failed = runCatching {
            store().sync("feishu", listOf(PluginSkill("taken", "x")), emptyList(), active = true)
        }.isFailure
        assertTrue(failed)
        assertEquals("user skill", File(foreign, "SKILL.md").readText())
    }

    @Test
    fun syncRefusesToOverwriteForeignActiveSkill() = runTest {
        val foreign = File(activeDir, "taken")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")
        val failed = runCatching {
            store().sync("feishu", listOf(PluginSkill("taken", "x")), emptyList(), active = true)
        }.isFailure
        assertTrue(failed)
        assertEquals("user skill", File(foreign, "SKILL.md").readText())
    }

    @Test
    fun removeDeletesBothDirectories() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("owned", "x")), emptyList(), active = true)
        store.remove("feishu", listOf("owned"))
        assertFalse(File(repoDir, "owned").exists())
        assertFalse(File(activeDir, "owned").exists())
    }

    @Test
    fun removeOnlyDeletesOwnedSkills() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("owned", "x")), emptyList(), active = true)
        val foreignRepo = File(repoDir, "foreign")
        foreignRepo.mkdirs()
        File(foreignRepo, "SKILL.md").writeText("user skill")
        val foreignActive = File(activeDir, "foreign")
        foreignActive.mkdirs()
        File(foreignActive, "SKILL.md").writeText("user skill")

        store.remove("feishu", listOf("owned", "foreign"))

        assertFalse(File(repoDir, "owned").exists())
        assertFalse(File(activeDir, "owned").exists())
        assertTrue(File(foreignRepo, "SKILL.md").isFile)
        assertTrue(File(foreignActive, "SKILL.md").isFile)
    }
}
