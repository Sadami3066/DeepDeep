package io.github.whywood.deepdeep.plugin.gui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import dev.pi.ai.TextContent
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenAccess
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.privilege.PrivilegeState
import io.github.whywood.deepdeep.screen.screenToolNames
import io.github.whywood.deepdeep.shell.RootStatus
import io.github.whywood.deepdeep.vscreen.VirtualScreenFailureInfo
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuiPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = FakeContext()
    private val screenshotDir: File get() = File(temporaryFolder.root, "gui/screenshots")

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()
        assertEquals("gui", plugin.id)
        assertEquals("3.1.0", plugin.version)
        assertEquals(DdIcons.HandClick, plugin.icon)
        assertEquals(R.string.plugin_screen_name, plugin.nameRes)
        assertEquals(R.string.plugin_screen_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_gui_size, plugin.sizeLabelRes)
        assertEquals(screenToolNames(), plugin.tools().map { it.name })
        assertEquals(11, plugin.tools().size)
        assertTrue(plugin.requiresPrivilege)
        assertFalse(plugin.defaultEnabled)
    }

    @Test
    fun availabilityFollowsPrivilegeState() = runTest {
        assertEquals(PluginAvailability.Available, plugin().availability())

        val privilege = FakePrivilege(available = false)
        val unauthorized = plugin(privilege = privilege).availability()
        assertTrue(unauthorized is PluginAvailability.Unavailable)
        assertEquals(
            "res:${R.string.plugin_screen_privilege_required}",
            (unauthorized as PluginAvailability.Unavailable).reason,
        )

        privilege.setAvailable(true)
        assertEquals(PluginAvailability.Available, plugin(privilege = privilege).availability())
    }

    @Test
    fun disconnectedServiceIsNotReady() = runTest {
        val status = plugin(FakeActions(disconnected = true)).status()
        assertEquals("res:${R.string.plugin_gui_status_disabled}", status.label)
        assertTrue(status is PluginStatus.NotReady)
    }

    @Test
    fun connectedServiceIsReady() = runTest {
        val status = plugin().status()
        assertEquals("res:${R.string.plugin_gui_status_connected}", status.label)
        assertTrue(status is PluginStatus.Ready)
    }

    @Test
    fun actionsOpenAccessibilitySettingsAndOfferBatteryExemption() {
        val actions = plugin().actions(PluginStatus.NotReady("x"))
        assertEquals(
            listOf("accessibility_settings", "battery_exemption"),
            actions.map { it.id },
        )
        assertEquals(R.string.plugin_gui_action_settings, actions.first().labelRes)
        assertEquals(R.string.plugin_gui_action_battery, actions.last().labelRes)
    }

    @Test
    fun actionsIncludeVirtualScreenLifecycleWhenFeatureIsPresent() {
        val actions = plugin(virtualScreen = feature()).actions(PluginStatus.NotReady("x"))
        assertEquals(
            listOf("virtual_start", "accessibility_settings", "battery_exemption"),
            actions.map { it.id },
        )
        assertEquals(R.string.plugin_virtual_screen_action_start, actions.first().labelRes)
    }

    @Test
    fun stoppedVirtualScreenOffersStopAction() {
        val actions = plugin(virtualScreen = feature(FakeScreen(VirtualScreenState.Online(3, 1080, 1920, "adb"))))
            .actions(PluginStatus.Ready("x"))
        assertEquals("virtual_stop", actions.first().id)
        assertEquals(R.string.plugin_virtual_screen_action_stop, actions.first().labelRes)
    }

    @Test
    fun privilegeGatesVirtualScreenToolsAndActions() {
        val privilege = FakePrivilege(available = false)
        val plugin = plugin(privilege = privilege, virtualScreen = feature())
        assertTrue(plugin.tools().isEmpty())
        assertTrue(plugin.actions(PluginStatus.Ready("x")).none { it.id.startsWith("virtual_") })

        privilege.setAvailable(true)
        assertEquals(screenToolNames() + "virtual_screen", plugin.tools().map { it.name })
        assertEquals("virtual_start", plugin.actions(PluginStatus.Ready("x")).first().id)
    }

    @Test
    fun detailRowsReportAccessibilityAndVirtualScreenState() = runTest {
        val connected = plugin().detailRows(PluginStatus.Ready("x"))
        assertEquals(1, connected.size)
        assertEquals(R.string.plugin_gui_detail_service, connected[0].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_enabled}", connected[0].value)

        val disabled = plugin(FakeActions(disconnected = true)).detailRows(PluginStatus.NotReady("x"))
        assertEquals("res:${R.string.plugin_gui_detail_disabled}", disabled[0].value)

        val online = plugin(virtualScreen = feature(FakeScreen(VirtualScreenState.Online(3, 1080, 1920, "adb"))))
            .detailRows(PluginStatus.Ready("x"))
        assertEquals(3, online.size)
        assertEquals(R.string.plugin_virtual_screen_detail_state, online[1].labelRes)
        assertEquals("res:${R.string.plugin_virtual_screen_status_online}", online[1].value)
        assertEquals(R.string.plugin_virtual_screen_detail_resolution, online[2].labelRes)
        assertEquals("1080 × 1920", online[2].value)

        val offline = plugin(virtualScreen = feature()).detailRows(PluginStatus.Ready("x"))
        assertEquals("res:${R.string.plugin_virtual_screen_status_offline}", offline[1].value)
        assertEquals("res:${R.string.plugin_virtual_screen_value_offline}", offline[2].value)
    }

    @Test
    fun toolsIncludeVirtualScreenLifecycleWhenFeatureIsPresent() {
        val tools = plugin(virtualScreen = feature()).tools()
        assertEquals(screenToolNames() + "virtual_screen", tools.map { it.name })
        assertEquals(12, tools.size)
    }

    @Test
    fun mainScreenshotWithoutAccessibilityFailsBeforeWriting() = runTest {
        val tool = plugin().tools().first { it.name == "screen_screenshot" }
        val result = tool.execute("legacy", buildJsonObject { put("target", "main") }, null, null)
        assertEquals(true, result.isError)
        assertTrue((result.content.single() as TextContent).text.contains("accessibility"))
        assertTrue(!screenshotDir.exists())
    }

    @Test
    fun virtualScreenshotDoesNotFallBackToMain() = runTest {
        val actions = FakeActions()
        val plugin = plugin(actions = actions)
        val tool = plugin.tools().first { it.name == "screen_screenshot" }
        val result = tool.execute(
            "call",
            buildJsonObject { put("target", "virtual") },
            null,
            null,
        )
        assertEquals(true, result.isError)
        assertTrue((result.content.single() as TextContent).text.contains("Virtual screen engine is not ready"))
    }

    @Test
    fun skillHasFrontmatterAndScreenGuidance() {
        val skills = plugin().skills()
        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("deepdeep-gui", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-gui"))
        assertTrue(frontmatter.contains("description: 优先操作后台虚拟屏"))
        screenToolNames().forEach { toolName -> assertTrue(toolName, skill.markdown.contains(toolName)) }
        listOf(
            "virtual_screen(action=start)",
            "屏幕操作",
            "onExpectedScreen",
            "forTextGone",
            "verified",
            "ref",
            "readNodes",
            "密码",
            "验证码",
            "权限弹窗",
            "设备管理员",
            "锁屏",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
    }

    @Test
    fun skillDropsUnsupportedToolNames() {
        val markdown = plugin().skills().single().markdown.lowercase()
        listOf(
            "screen_find",
            "virtual_screen_start",
        ).forEach { forbidden -> assertTrue(forbidden, !markdown.contains(forbidden)) }
    }

    private fun plugin(
        actions: GuiActions = FakeActions(),
        privilege: FakePrivilege? = null,
        virtualScreen: VirtualScreenFeature? = null,
    ): GuiPlugin = GuiPlugin(
        context,
        actions,
        screenshotDir,
        privilege = privilege,
        virtualScreen = virtualScreen,
    )

    private fun feature(screen: VirtualScreenAccess = FakeScreen()): VirtualScreenFeature =
        VirtualScreenFeature(context, screen)

    private class FakePrivilege(available: Boolean) : PrivilegeController {
        private val flow = MutableStateFlow(privilegeState(available))
        override val state: StateFlow<PrivilegeState> get() = flow

        fun setAvailable(available: Boolean) {
            flow.value = privilegeState(available)
        }

        override suspend fun select(level: PrivilegeLevel) = Unit

        override suspend fun refresh() = Unit

        override suspend fun probeRoot(): RootStatus = RootStatus.NotRequested

        override suspend fun connect() = Unit

        override fun startPairing() = Unit

        override fun openWirelessDebugging(context: Context) = Unit
    }

    private class FakeScreen(
        initial: VirtualScreenState = VirtualScreenState.Offline,
    ) : VirtualScreenAccess {
        override val state = MutableStateFlow(initial)
        override var lastFailure: VirtualScreenFailureInfo? = null
        override var lastTransportNotice: String? = null

        override suspend fun start(): VirtualScreenResult {
            state.value = VirtualScreenState.Online(3, 1080, 1920, "adb")
            return VirtualScreenResult(true)
        }

        override suspend fun stop() {
            state.value = VirtualScreenState.Offline
        }
    }

    private class FakeActions(
        var disconnected: Boolean = false,
    ) : GuiActions {

        override val connected: Boolean
            get() = !disconnected

        override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot =
            throw UnsupportedOperationException()

        override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) =
            throw UnsupportedOperationException()

        override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult =
            throw UnsupportedOperationException()

        override suspend fun waitFor(displayId: Int, packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome =
            throw UnsupportedOperationException()

        override fun invalidateSnapshots() = Unit
        override fun bounds(displayId: Int, selector: GuiSelector): GuiBounds = throw UnsupportedOperationException()
    }

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources

        override fun getSystemService(name: String): Any? = null

    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }
}

private fun privilegeState(available: Boolean): PrivilegeState = if (available) {
    PrivilegeState(
        selected = PrivilegeLevel.ADB,
        effective = PrivilegeLevel.ADB,
        adb = AdbBridgeState(paired = true, daemon = true),
    )
} else {
    PrivilegeState()
}
