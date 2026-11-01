package io.github.whywood.deepdeep.plugin.gui

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.screen.screenToolNames
import dev.pi.ai.TextContent
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        assertEquals("3.1.0", plugin.version)
        assertEquals(DdIcons.HandClick, plugin.icon)
        assertEquals(R.string.plugin_screen_name, plugin.nameRes)
        assertEquals(R.string.plugin_screen_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_gui_size, plugin.sizeLabelRes)
        assertEquals(screenToolNames(), plugin.tools().map { it.name })
        assertEquals(11, plugin.tools().size)
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
        assertEquals(6, connected.size)
        assertEquals(R.string.plugin_gui_detail_service, connected[0].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_enabled}", connected[0].value)
        assertEquals(R.string.plugin_screen_detail_virtual, connected[1].labelRes)
        assertEquals("res:${R.string.plugin_screen_detail_virtual_value}", connected[1].value)
        assertEquals(R.string.plugin_gui_detail_how, connected[2].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_how_value}", connected[2].value)
        assertEquals(R.string.plugin_gui_detail_battery, connected[3].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_battery_optimized}", connected[3].value)
        assertEquals(R.string.plugin_gui_detail_background, connected[4].labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_background_unrestricted}", connected[4].value)
        assertEquals(R.string.plugin_gui_detail_recent, connected[5].labelRes)

        val disabled = plugin(FakeActions(disconnected = true)).detailRows(PluginStatus.NotReady("x"))
        assertEquals("res:${R.string.plugin_gui_detail_disabled}", disabled[0].value)
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
    ): GuiPlugin = GuiPlugin(context, actions, screenshotDir)

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
