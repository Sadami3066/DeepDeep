package io.github.whywood.deepdeep.plugin.gui

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import dev.pi.ai.TextContent
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        assertEquals("2.0.0", plugin.version)
        assertEquals(DdIcons.HandClick, plugin.icon)
        assertEquals(R.string.plugin_gui_name, plugin.nameRes)
        assertEquals(R.string.plugin_gui_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_gui_size, plugin.sizeLabelRes)
        assertEquals(
            listOf(
                "gui_observe",
                "gui_find",
                "gui_tap",
                "gui_set_text",
                "gui_scroll",
                "gui_global",
                "gui_wait",
                "gui_screenshot",
            ),
            plugin.tools().map { it.name },
        )
    }

    @Test
    fun availabilityIsAlwaysAvailable() = runTest {
        assertEquals(PluginAvailability.Available, plugin().availability())
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
    fun detailRowsReportServiceAndKeepAliveState() = runTest {
        val connected = plugin().detailRows(PluginStatus.Ready("x"))
        assertEquals(5, connected.size)
        assertEquals(R.string.plugin_gui_detail_service, connected[0].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_enabled}", connected[0].value)
        assertEquals(R.string.plugin_gui_detail_how, connected[1].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_how_value}", connected[1].value)
        assertEquals(R.string.plugin_gui_detail_battery, connected[2].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_battery_optimized}", connected[2].value)
        assertEquals(R.string.plugin_gui_detail_background, connected[3].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_background_unrestricted}", connected[3].value)
        assertEquals(R.string.plugin_gui_detail_recent, connected[4].labelRes)

        val disabled = plugin(FakeActions(disconnected = true)).detailRows(PluginStatus.NotReady("x"))
        assertEquals("res:${R.string.plugin_gui_detail_disabled}", disabled[0].value)
    }

    @Test
    fun screenshotToolUsesConfiguredDirectories() = runTest {
        val png = byteArrayOf(5, 6, 7)
        val actions = FakeActions().apply { screenshotResult = GuiScreenshot(png, 720, 1600) }
        val plugin = plugin(actions = actions, linuxScreenshotDir = "/workspace/shots")
        val tool = plugin.tools().first { it.name == "gui_screenshot" }
        val result = tool.execute("call", buildJsonObject { }, null, null)
        assertNull(result.isError)
        val text = (result.content.single() as TextContent).text
        assertTrue(text.contains("\"path\":\"/workspace/shots/gui-"))
        val file = screenshotDir.listFiles()!!.single()
        assertTrue(file.name.startsWith("gui-"))
        assertArrayEquals(png, file.readBytes())
    }

    @Test
    fun skillHasFrontmatterAndV2Guidance() {
        val skills = plugin().skills()
        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("deepdeep-gui", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-gui"))
        assertTrue(frontmatter.contains("description: 通过无障碍服务"))
        listOf(
            "gui_observe",
            "gui_find",
            "gui_tap",
            "gui_set_text",
            "gui_scroll",
            "gui_global",
            "gui_wait",
            "gui_screenshot",
            "forTextGone",
            "verified",
            "FLAG_SECURE",
            "ref",
            "密码",
            "验证码",
            "权限弹窗",
            "设备管理员",
            "锁屏",
            "当前活动窗口",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
    }

    private fun plugin(
        actions: GuiActions = FakeActions(),
        linuxScreenshotDir: String = GUI_DEFAULT_LINUX_SCREENSHOT_DIR,
    ): GuiPlugin = GuiPlugin(context, actions, screenshotDir, linuxScreenshotDir)

    private class FakeActions(
        var disconnected: Boolean = false,
    ) : GuiActions {
        var screenshotResult: GuiScreenshot = GuiScreenshot(byteArrayOf(1), 1, 1)

        override val connected: Boolean
            get() = !disconnected

        override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot =
            throw UnsupportedOperationException()

        override suspend fun tap(selector: GuiSelector, durationMs: Int) =
            throw UnsupportedOperationException()

        override suspend fun setText(selector: GuiSelector, text: String): GuiTextResult =
            throw UnsupportedOperationException()

        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) =
            throw UnsupportedOperationException()

        override suspend fun global(action: GuiGlobalAction) =
            throw UnsupportedOperationException()

        override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): Boolean =
            throw UnsupportedOperationException()

        override suspend fun screenshot(): GuiScreenshot = screenshotResult
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
