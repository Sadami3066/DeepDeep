package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginSkillFile

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
    fun syncKeepsForeignRepoSkill() = runTest {
        val foreign = File(repoDir, "taken")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")

        val skipped = store().sync("feishu", listOf(PluginSkill("taken", "x")), emptyList(), active = true)

        assertEquals(listOf("taken"), skipped)
        assertEquals("user skill", File(foreign, "SKILL.md").readText())
        assertFalse(File(foreign, ".deepdeep-plugin").exists())
        assertFalse(File(activeDir, "taken").exists())
    }

    @Test
    fun syncKeepsForeignActiveSkill() = runTest {
        val foreign = File(activeDir, "taken")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")

        val skipped = store().sync("feishu", listOf(PluginSkill("taken", "x")), emptyList(), active = true)

        assertEquals(listOf("taken"), skipped)
        assertEquals("user skill", File(foreign, "SKILL.md").readText())
        assertFalse(File(foreign, ".deepdeep-plugin").exists())
    }

    @Test
    fun syncClaimsForeignRepoSkillWithMatchingContent() = runTest {
        val foreign = File(repoDir, "claimed")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("body")
        File(foreign, "extra.txt").writeText("keep")

        val skipped = store().sync("feishu", listOf(PluginSkill("claimed", "body")), emptyList(), active = true)

        assertTrue(skipped.isEmpty())
        assertEquals("feishu", File(foreign, ".deepdeep-plugin").readText())
        assertFalse(File(foreign, "extra.txt").exists())
        assertEquals("feishu", File(activeDir, "claimed/.deepdeep-plugin").readText())
    }

    @Test
    fun setActiveClaimsForeignActiveSkillWithMatchingContent() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("claimed", "body")), emptyList(), active = false)
        File(repoDir, "claimed/.deepdeep-plugin").delete()
        val foreign = File(activeDir, "claimed")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("body")

        val skipped = store.setActive("feishu", listOf(PluginSkill("claimed", "body")), enabled = true)

        assertTrue(skipped.isEmpty())
        assertEquals("feishu", File(foreign, ".deepdeep-plugin").readText())
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

    @Test
    fun syncWritesSkillFilesIntoRepoAndActive() = runTest {
        val skill = PluginSkill(
            id = "deepdeep-workshop",
            markdown = "# Skill",
            files = listOf(
                PluginSkillFile("references/api.md", "# API"),
                PluginSkillFile("examples/01-hello.html", "<html></html>"),
            ),
        )
        store().sync("workshop", listOf(skill), emptyList(), active = true)

        listOf(File(repoDir, "deepdeep-workshop"), File(activeDir, "deepdeep-workshop")).forEach { dir ->
            assertEquals("# Skill", File(dir, "SKILL.md").readText())
            assertEquals("# API", File(dir, "references/api.md").readText())
            assertEquals("<html></html>", File(dir, "examples/01-hello.html").readText())
            assertEquals("workshop", File(dir, ".deepdeep-plugin").readText())
        }
    }

    @Test
    fun syncRejectsUnsafeSkillFilePaths() = runTest {
        listOf("../escape.md", "/absolute.md", "a//b.md", "a/./b.md", "").forEach { path ->
            val failed = runCatching {
                store().sync(
                    "workshop",
                    listOf(PluginSkill("safe", "body", files = listOf(PluginSkillFile(path, "x")))),
                    emptyList(),
                    active = true,
                )
            }.isFailure
            assertTrue("expected rejection for '$path'", failed)
        }
    }

    @Test
    fun claimWithFilesReplacesWholeDirectory() = runTest {
        val foreign = File(repoDir, "claimed")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("body")
        File(foreign, "old.md").writeText("old")

        store().sync(
            "workshop",
            listOf(
                PluginSkill(
                    id = "claimed",
                    markdown = "body",
                    files = listOf(PluginSkillFile("references/new.md", "# New")),
                ),
            ),
            emptyList(),
            active = true,
        )

        assertFalse(File(foreign, "old.md").exists())
        assertEquals("# New", File(foreign, "references/new.md").readText())
        assertEquals("workshop", File(foreign, ".deepdeep-plugin").readText())
    }

    @Test
    fun legacyOwnedSkillReplacesUnownedDirectory() = runTest {
        val legacyRepo = File(repoDir, "workshop")
        legacyRepo.mkdirs()
        File(legacyRepo, "SKILL.md").writeText("old builtin body")
        File(legacyRepo, "old-examples").mkdirs()
        File(legacyRepo, "old-examples/keep.html").writeText("old")
        val legacyActive = File(activeDir, "workshop")
        legacyActive.mkdirs()
        File(legacyActive, "SKILL.md").writeText("old builtin body")

        store().sync(
            "workshop",
            listOf(
                PluginSkill(
                    id = "workshop",
                    markdown = "new body",
                    files = listOf(PluginSkillFile("examples/01-hello.html", "<html></html>")),
                    legacyOwned = true,
                ),
            ),
            emptyList(),
            active = true,
        )

        listOf(legacyRepo, legacyActive).forEach { dir ->
            assertEquals("new body", File(dir, "SKILL.md").readText())
            assertEquals("workshop", File(dir, ".deepdeep-plugin").readText())
            assertEquals("<html></html>", File(dir, "examples/01-hello.html").readText())
            assertFalse(File(dir, "old-examples").exists())
        }
    }
}
