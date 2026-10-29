package io.github.whywood.deepdeep.plugin

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    private fun recordsFile() = File(temporaryFolder.root, "plugins/plugins.json")

    private fun newManager(
        scope: TestScope,
        plugin: OfficialPlugin = FakePlugin(),
        isForeground: () -> Boolean = { true },
        onSetupReady: (String) -> Unit = {},
        onContributionsChanged: () -> Unit = {},
    ): PluginManager {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return PluginManager(
            plugins = listOf(plugin),
            recordStore = PluginRecordStore(recordsFile(), dispatcher),
            skillStore = PluginSkillStore(
                skillsDir = File(temporaryFolder.root, "agent/skills"),
                repoDir = File(temporaryFolder.root, "skills"),
                ioDispatcher = dispatcher,
            ),
            strings = strings,
            scope = scope,
            isForeground = isForeground,
            onSetupReady = onSetupReady,
            onContributionsChanged = onContributionsChanged,
        )
    }

    @Test
    fun installWritesRecordAndSkills() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        assertTrue(plugin.installed)
        val record = PluginRecordStore(recordsFile()).load().records.getValue(plugin.id)
        assertEquals("2.0", record.version)
        assertEquals(listOf("fake-skill"), record.skillIds)
        assertTrue(record.enabled)
        listOf(
            File(temporaryFolder.root, "skills/fake-skill"),
            File(temporaryFolder.root, "agent/skills/fake-skill"),
        ).forEach { dir ->
            assertTrue(File(dir, "SKILL.md").isFile)
            assertEquals("fake", File(dir, ".deepdeep-plugin").readText())
        }
    }

    @Test
    fun failedFirstInstallUninstallsPartialPlugin() = runTest {
        val plugin = FakePlugin()
        plugin.installError = IllegalStateException("download failed")
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        assertFalse(plugin.installed)
        assertEquals(1, plugin.uninstallCalls)
        assertNull(PluginRecordStore(recordsFile()).load().records[plugin.id])
    }

    @Test
    fun failedUpgradeKeepsPreviousInstall() = runTest {
        val plugin = FakePlugin()
        plugin.installed = true
        PluginRecordStore(recordsFile()).upsert(
            PluginRecord(plugin.id, "1.0", 0L, listOf("fake-skill")),
        )
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()
        val skillFile = File(temporaryFolder.root, "skills/fake-skill/SKILL.md")
        val skillBefore = skillFile.readText()

        plugin.installError = IllegalStateException("download failed")
        manager.update(plugin.id)
        advanceUntilIdle()

        val record = PluginRecordStore(recordsFile()).load().records.getValue(plugin.id)
        assertEquals("1.0", record.version)
        assertEquals(listOf("fake-skill"), record.skillIds)
        assertTrue(plugin.installed)
        assertEquals(0, plugin.uninstallCalls)
        assertEquals(skillBefore, skillFile.readText())
    }

    @Test
    fun installNotifiesContributionsAfterToolsBecomeVisible() = runTest {
        val plugin = FakePlugin()
        var toolsAtNotification: List<String> = emptyList()
        lateinit var manager: PluginManager
        manager = newManager(
            scope = this,
            plugin = plugin,
            onContributionsChanged = {
                toolsAtNotification = manager.tools().map { it.name }
            },
        )
        manager.install(plugin.id)
        advanceUntilIdle()

        assertEquals(listOf("fake_tool"), toolsAtNotification)
    }

    @Test
    fun unavailablePluginDoesNotInstall() = runTest {
        val plugin = FakePlugin(availability = PluginAvailability.Unavailable("需要 Linux"))
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        assertFalse(plugin.installed)
        assertNull(PluginRecordStore(recordsFile()).load().records[plugin.id])
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
        assertTrue(PluginRecordStore(recordsFile()).load().records.isEmpty())
        assertFalse(File(temporaryFolder.root, "agent/skills/fake-skill").exists())
        assertFalse(File(temporaryFolder.root, "skills/fake-skill").exists())
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
    fun startMigratesSkillsForInstalledPlugins() = runTest {
        val plugin = FakePlugin()
        plugin.installed = true
        PluginRecordStore(recordsFile()).upsert(PluginRecord(plugin.id, "2.0", 0L, listOf("fake-skill")))
        val oldActive = File(temporaryFolder.root, "agent/skills/fake-skill")
        oldActive.mkdirs()
        File(oldActive, "SKILL.md").writeText("old")
        File(oldActive, ".deepdeep-plugin").writeText(plugin.id)

        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()

        assertTrue(File(temporaryFolder.root, "skills/fake-skill/SKILL.md").isFile)
        assertTrue(File(oldActive, "SKILL.md").isFile)
    }

    @Test
    fun toolsFilterByInstalledAndEnabled() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()
        assertTrue(manager.tools().isEmpty())

        manager.install(plugin.id)
        advanceUntilIdle()
        assertEquals(listOf("fake_tool"), manager.tools().map { it.name })

        manager.setEnabled(plugin.id, false)
        advanceUntilIdle()
        assertTrue(manager.tools().isEmpty())
        assertFalse(manager.state.value.snapshot(plugin.id)!!.enabled)

        manager.setEnabled(plugin.id, true)
        advanceUntilIdle()
        assertEquals(listOf("fake_tool"), manager.tools().map { it.name })
    }

    @Test
    fun disabledPluginSkipsStatusAndRejectsOperations() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()
        manager.setEnabled(plugin.id, false)
        advanceUntilIdle()

        plugin.statusCalls = 0
        manager.refresh()
        advanceUntilIdle()
        assertEquals(0, plugin.statusCalls)
        val snapshot = manager.state.value.snapshot(plugin.id)!!
        assertFalse(snapshot.enabled)
        assertNull(snapshot.status)
        assertNull(manager.promptBody())

        val setup = manager.setup(plugin.id)
        assertFalse(setup.ok)
        assertEquals("res:${R.string.plugins_error_disabled}", setup.error)
        assertTrue(setup.installed)

        manager.runAction(plugin.id, "do")
        advanceUntilIdle()
        assertEquals("res:${R.string.plugins_error_disabled}", manager.state.value.error)
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
        PluginRecordStore(recordsFile()).upsert(
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

    @Test
    fun actionLeavesNoStaleInteraction() = runTest {
        val plugin = FakePlugin()
        val manager = newManager(this, plugin)
        manager.install(plugin.id)
        advanceUntilIdle()

        plugin.actionHook = { ui -> ui.stage(R.string.plugins_title) }
        manager.runAction(plugin.id, "do")
        advanceUntilIdle()

        assertNull(manager.state.value.interaction)
        assertFalse(manager.state.value.busy)
    }

    @Test
    fun onAppForegroundCompletesWaitingSetupWhenReady() = runTest {
        val plugin = FakePlugin()
        plugin.installed = true
        plugin.statusValue = PluginStatus.NotReady("未连接")
        PluginRecordStore(recordsFile()).upsert(
            PluginRecord(plugin.id, "2.0", 0L, listOf("fake-skill")),
        )
        val manager = newManager(this, plugin)
        manager.start()
        advanceUntilIdle()

        val started = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        plugin.setupHook = { ui ->
            ui.stage(R.string.plugins_title)
            started.complete(Unit)
            blocked.await()
        }
        val result = async { manager.setup(plugin.id) }
        advanceUntilIdle()
        started.await()
        assertNotNull(manager.state.value.interaction)

        plugin.statusValue = PluginStatus.Ready("已连接", "外部账号")
        manager.onAppForeground()
        advanceUntilIdle()

        val setup = result.await()
        assertTrue(setup.ok)
        assertTrue(setup.ready)
        assertEquals("外部账号", setup.account)
        assertNull(manager.state.value.interaction)
    }

    @Test
    fun setupReadyInBackgroundCallsOnSetupReady() = runTest {
        var foreground = true
        var readyLabel: String? = null
        val plugin = FakePlugin()
        plugin.installed = true
        plugin.statusValue = PluginStatus.NotReady("未连接")
        PluginRecordStore(recordsFile()).upsert(
            PluginRecord(plugin.id, "2.0", 0L, listOf("fake-skill")),
        )
        val manager = newManager(
            scope = this,
            plugin = plugin,
            isForeground = { foreground },
            onSetupReady = { readyLabel = it },
        )
        manager.start()
        advanceUntilIdle()

        plugin.setupHook = { _ -> foreground = false }
        val setup = manager.setup(plugin.id)
        advanceUntilIdle()

        assertTrue(setup.ok)
        assertEquals("res:${R.string.plugins_title}", readyLabel)
    }

    @Test
    fun actionReadyInBackgroundCallsOnSetupReady() = runTest {
        var foreground = true
        var readyLabel: String? = null
        val plugin = FakePlugin()
        plugin.installed = true
        plugin.statusValue = PluginStatus.NotReady("未连接")
        PluginRecordStore(recordsFile()).upsert(
            PluginRecord(plugin.id, "2.0", 0L, listOf("fake-skill")),
        )
        val manager = newManager(
            scope = this,
            plugin = plugin,
            isForeground = { foreground },
            onSetupReady = { readyLabel = it },
        )
        manager.start()
        advanceUntilIdle()

        plugin.actionHook = { _ ->
            foreground = false
            plugin.statusValue = PluginStatus.Ready("已连接", "ready")
        }
        manager.runAction(plugin.id, "do")
        advanceUntilIdle()

        assertEquals("res:${R.string.plugins_title}", readyLabel)
    }

    private class FakePlugin(
        var availability: PluginAvailability = PluginAvailability.Available,
    ) : OfficialPlugin {
        override val id: String = "fake"
        override val icon: DdIcon = DdIcons.Globe
        override val nameRes: Int = R.string.plugins_title
        override val summaryRes: Int = R.string.plugins_title
        override val version: String = "2.0"
        override val sizeLabelRes: Int = R.string.plugins_title

        var installed: Boolean = false
        var statusValue: PluginStatus = PluginStatus.NotReady("未连接")
        var statusCalls: Int = 0
        var installError: Exception? = null
        var uninstallCalls: Int = 0
        var setupHook: (suspend (PluginUi) -> Unit)? = null
        var actionHook: (suspend (PluginUi) -> Unit)? = null

        override suspend fun availability(): PluginAvailability = availability

        override suspend fun install(progress: PluginProgress) {
            installError?.let { throw it }
            installed = true
        }

        override suspend fun uninstall() {
            uninstallCalls++
            installed = false
        }

        override suspend fun status(): PluginStatus {
            statusCalls++
            if (!installed) return PluginStatus.Failed("未安装")
            return statusValue
        }

        override suspend fun setup(ui: PluginUi) {
            setupHook?.invoke(ui)
            if (statusValue !is PluginStatus.Ready) {
                statusValue = PluginStatus.Ready("已连接", "ready")
            }
        }

        override fun actions(status: PluginStatus): List<PluginAction> =
            listOf(PluginAction("do", R.string.plugins_title))

        override suspend fun runAction(actionId: String, ui: PluginUi) {
            actionHook?.invoke(ui)
        }

        override fun skills(): List<PluginSkill> = listOf(PluginSkill("fake-skill", "body"))

        override fun tools(): List<CodingTool> = listOf(testTool(), testTool())

        override fun promptBody(status: PluginStatus): String = "PROMPT:${status.label}"
    }

    private companion object {
        fun testTool(): CodingTool = CodingTool(
            name = "fake_tool",
            description = "fake",
            parameters = buildJsonObject { put("type", "object") },
            label = "fake_tool",
            execute = { _, _, _, _ -> textResult("ok") },
        )
    }
}
