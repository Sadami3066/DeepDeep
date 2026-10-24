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

    private fun store(): PluginSkillStore =
        PluginSkillStore(File(temporaryFolder.root, "agent/skills"))

    @Test
    fun syncWritesSkillWithMarker() = runTest {
        store().sync(
            pluginId = "feishu",
            skills = listOf(PluginSkill("deepdeep-feishu", "---\nname: x\ndescription: y\n---\nbody")),
            previousIds = emptyList(),
        )
        val dir = File(temporaryFolder.root, "agent/skills/deepdeep-feishu")
        assertTrue(File(dir, "SKILL.md").isFile)
        assertEquals("feishu", File(dir, ".deepdeep-plugin").readText())
    }

    @Test
    fun syncRemovesStaleSkillsForPlugin() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("a", "x")), emptyList())
        store.sync("feishu", listOf(PluginSkill("b", "y")), listOf("a"))
        assertFalse(File(temporaryFolder.root, "agent/skills/a").exists())
        assertTrue(File(temporaryFolder.root, "agent/skills/b").isDirectory)
    }

    @Test
    fun syncRefusesToOverwriteForeignSkill() = runTest {
        val foreign = File(temporaryFolder.root, "agent/skills/taken")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")
        val failed = runCatching {
            store().sync("feishu", listOf(PluginSkill("taken", "x")), emptyList())
        }.isFailure
        assertTrue(failed)
        assertEquals("user skill", File(foreign, "SKILL.md").readText())
    }

    @Test
    fun removeOnlyDeletesOwnedSkills() = runTest {
        val store = store()
        store.sync("feishu", listOf(PluginSkill("owned", "x")), emptyList())
        val foreign = File(temporaryFolder.root, "agent/skills/foreign")
        foreign.mkdirs()
        File(foreign, "SKILL.md").writeText("user skill")
        store.remove("feishu", listOf("owned", "foreign"))
        assertFalse(File(temporaryFolder.root, "agent/skills/owned").exists())
        assertTrue(File(foreign, "SKILL.md").isFile)
    }
}
