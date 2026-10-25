package io.github.whywood.deepdeep.plugin.gui

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiPluginTest {
    private val context = FakeContext()

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()
        assertEquals("gui", plugin.id)
        assertEquals("1.0.0", plugin.version)
        assertEquals(DdIcons.Monitor, plugin.icon)
        assertEquals(R.string.plugin_gui_name, plugin.nameRes)
        assertEquals(R.string.plugin_gui_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_gui_size, plugin.sizeLabelRes)
        assertEquals(
            listOf("gui_observe", "gui_tap", "gui_set_text", "gui_scroll", "gui_global", "gui_wait"),
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
    fun actionsOpenAccessibilitySettings() {
        val actions = plugin().actions(PluginStatus.NotReady("x"))
        assertEquals(1, actions.size)
        assertEquals("accessibility_settings", actions.single().id)
        assertEquals(R.string.plugin_gui_action_settings, actions.single().labelRes)
    }

    @Test
    fun detailRowsReportServiceState() = runTest {
        val connected = plugin().detailRows(PluginStatus.Ready("x"))
        assertEquals(1, connected.size)
        assertEquals(R.string.plugin_gui_detail_service, connected.single().labelRes)
        assertEquals("res:${R.string.plugin_gui_detail_enabled}", connected.single().value)

        val disabled = plugin(FakeActions(disconnected = true)).detailRows(PluginStatus.NotReady("x"))
        assertEquals("res:${R.string.plugin_gui_detail_disabled}", disabled.single().value)
    }

    @Test
    fun skillHasFrontmatterAndSafetyGuidance() {
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
            "gui_tap",
            "gui_set_text",
            "gui_scroll",
            "gui_global",
            "gui_wait",
            "ref",
            "密码",
            "验证码",
            "权限弹窗",
            "设备管理员",
            "锁屏",
            "当前活动窗口",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
    }

    private fun plugin(actions: GuiActions = FakeActions()): GuiPlugin = GuiPlugin(context, actions)

    private class FakeActions(
        var disconnected: Boolean = false,
    ) : GuiActions {
        override val connected: Boolean
            get() = !disconnected

        override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot =
            throw UnsupportedOperationException()

        override suspend fun tap(selector: GuiSelector, durationMs: Int) =
            throw UnsupportedOperationException()

        override suspend fun setText(selector: GuiSelector, text: String) =
            throw UnsupportedOperationException()

        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) =
            throw UnsupportedOperationException()

        override suspend fun global(action: GuiGlobalAction) =
            throw UnsupportedOperationException()

        override suspend fun waitFor(packageName: String?, text: String?, timeoutMs: Long): Boolean =
            throw UnsupportedOperationException()
    }

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources
    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }
}
