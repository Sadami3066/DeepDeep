package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val strings = object : PluginStrings {
        override fun get(id: Int): String = "res:$id"

        override fun format(id: Int, vararg args: Any): String =
            "res:$id:" + args.joinToString(",")
    }

    private fun newManager(scope: TestScope, plugin: OfficialPlugin = FakePlugin()): PluginManager {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return PluginManager(
            plugins = listOf(plugin),
            recordStore = PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json"), dispatcher),
            skillStore = PluginSkillStore(File(temporaryFolder.root, "agent/skills"), dispatcher),
            strings = strings,
            scope = scope,
            isForeground = { true },
        )
    }

    @Test
    fun installWritesRecordAndSkills() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        assertTrue(plugin.installed)
        val records = PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json")).load()
        assertEquals("2.0", records.records.getValue(plugin.id).version)
        assertEquals(listOf("fake-skill"), records.records.getValue(plugin.id).skillIds)
        assertTrue(File(temporaryFolder.root, "agent/skills/fake-skill/SKILL.md").isFile)
        assertEquals("fake", File(temporaryFolder.root, "agent/skills/fake-skill/.deepdeep-plugin").readText())
    }

    @Test
    fun unavailablePluginDoesNotInstall() = runTest {
        val plugin = FakePlugin(availability = PluginAvailability.Unavailable("需要 Linux"))
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        assertFalse(plugin.installed)
        assertNull(
            PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json")).load().records[plugin.id],
        )
        assertTrue(manager.state.value.error?.contains("需要 Linux") == true)
    }

    @Test
    fun uninstallRemovesRecordAndSkills() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()
        manager.uninstall(plugin.id)
        advanceUntilIdle()

        assertFalse(plugin.installed)
        assertTrue(
            PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json")).load().records.isEmpty(),
        )
        assertFalse(File(temporaryFolder.root, "agent/skills/fake-skill").exists())
    }

    @Test
    fun promptBodyOnlyAppearsWhenInstalled() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()
        assertNull(manager.promptBody())

        manager.install(plugin.id)
        advanceUntilIdle()
        assertEquals("PROMPT:未连接", manager.promptBody())
    }

    @Test
    fun setupAsksForConfirmationBeforeInstalling() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()

        val result = async { manager.setup(plugin.id) }
        advanceUntilIdle()
        val interaction = manager.state.value.interaction
        assertNotNull(interaction)
        assertTrue(interaction!!.confirmInstall)
        assertFalse(plugin.installed)

        manager.confirmInstall()
        advanceUntilIdle()
        assertNotNull(result.await())
        assertTrue(plugin.installed)
        assertEquals("ready", (plugin.statusValue as PluginStatus.Ready).account)
        assertNull(manager.state.value.interaction)
    }

    @Test
    fun setupSkipsSheetWhenAlreadyReady() = runTest {
        val plugin = FakePlugin()
        plugin.installed = true
        plugin.statusValue = PluginStatus.Ready("已连接", "张三")
        PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json")).upsert(
            PluginRecord(plugin.id, "2.0", 0L, listOf("fake-skill")),
        )
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()

        val result = manager.setup(plugin.id)
        assertTrue(result.ok)
        assertEquals("张三", result.account)
        assertNull(manager.state.value.interaction)
    }

    private class FakePlugin(
        var availability: PluginAvailability = PluginAvailability.Available,
    ) : OfficialPlugin {
        override val id: String = "fake"
        override val nameRes: Int = R.string.plugins_title
        override val summaryRes: Int = R.string.plugins_title
        override val version: String = "2.0"
        override val sizeLabelRes: Int = R.string.plugins_title

        var installed: Boolean = false
        var statusValue: PluginStatus = PluginStatus.NotReady("未连接")

        override suspend fun availability(): PluginAvailability = availability

        override suspend fun install(progress: PluginProgress) {
            installed = true
        }

        override suspend fun uninstall() {
            installed = false
        }

        override suspend fun status(): PluginStatus {
            if (!installed) return PluginStatus.Failed("未安装")
            return statusValue
        }

        override suspend fun setup(ui: PluginUi) {
            statusValue = PluginStatus.Ready("已连接", "ready")
        }

        override fun skills(): List<PluginSkill> = listOf(PluginSkill("fake-skill", "body"))

        override fun promptBody(status: PluginStatus): String = "PROMPT:${status.label}"
    }
}
