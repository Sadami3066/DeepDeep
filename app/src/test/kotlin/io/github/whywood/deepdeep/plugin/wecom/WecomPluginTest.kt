package io.github.whywood.deepdeep.plugin.wecom

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginActionStyle
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDownloader
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.shell.LinuxResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WecomPluginTest {
    private val context = FakeContext()

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()
        assertEquals("wecom", plugin.id)
        assertEquals("1.3.4", plugin.version)
        assertEquals(DdIcons.Message, plugin.icon)
        assertEquals(R.string.plugin_wecom_name, plugin.nameRes)
        assertEquals(R.string.plugin_wecom_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_wecom_size, plugin.sizeLabelRes)
    }

    @Test
    fun availabilityRequiresLinuxEnvironment() = runTest {
        assertEquals(
            "res:${R.string.plugin_wecom_unavailable_device}",
            unavailableReason(plugin(FakeRuntime(supported = false))),
        )
        assertEquals(
            "res:${R.string.plugin_wecom_unavailable_linux}",
            unavailableReason(plugin(FakeRuntime(installed = false))),
        )
        assertEquals(
            "res:${R.string.plugin_wecom_unavailable_disabled}",
            unavailableReason(plugin(FakeRuntime(enabled = false))),
        )
        assertEquals(PluginAvailability.Available, plugin().availability())
    }

    @Test
    fun authorizedStatusReportsBotIdAndDetails() = runTest {
        val runtime = FakeRuntime()
        val plugin = plugin(runtime)
        val status = plugin.status()
        assertTrue(status is PluginStatus.Ready)
        assertEquals("bot-123", (status as PluginStatus.Ready).account)
        assertTrue(status.label.endsWith("bot-123"))
        assertTrue(runtime.commands.any { it.contains("--version") })
        assertTrue(runtime.commands.any { it.contains("auth show --status") })
        assertTrue(runtime.commands.any { it.contains("auth show") })

        val rows = plugin.detailRows(status)
        assertEquals(2, rows.size)
        assertEquals(R.string.plugin_wecom_detail_cli_version, rows[0].labelRes)
        assertEquals("1.3.4", rows[0].value)
        assertEquals(R.string.plugin_wecom_detail_bot_id, rows[1].labelRes)
        assertEquals("bot-123", rows[1].value)
    }

    @Test
    fun unauthorizedStatusIsNotReady() = runTest {
        val plugin = plugin(FakeRuntime(statusOutput = "unauthorized\n"))
        val status = plugin.status()
        assertTrue(status is PluginStatus.NotReady)
        assertEquals("res:${R.string.plugin_wecom_status_not_connected}", status.label)
        assertEquals(1, plugin.detailRows(status).size)
    }

    @Test
    fun actionsFollowConnectionState() {
        val readyActions = plugin().actions(PluginStatus.Ready("connected"))
        assertEquals(listOf("reauthorize", "logout"), readyActions.map { it.id })
        assertEquals(PluginActionStyle.DESTRUCTIVE, readyActions[1].style)

        val idleActions = plugin().actions(PluginStatus.NotReady("idle"))
        assertEquals(listOf("connect"), idleActions.map { it.id })
        assertEquals(R.string.plugin_wecom_action_connect, idleActions.single().labelRes)
    }

    @Test
    fun skillCoversWecomCliWorkflow() {
        val skill = plugin().skills().single()
        assertEquals("deepdeep-wecom", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-wecom"))
        assertTrue(frontmatter.contains("description:"))
        listOf(
            "wecom-cli",
            "--dry-run",
            "sessions list",
            "chat_id",
            "media_id",
            "--output-dir",
            "auth init",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
    }

    @Test
    fun promptBodyKeepsAuthorizationInThePluginPage() {
        val ready = plugin().promptBody(PluginStatus.Ready("connected", "bot-123")).orEmpty()
        assertTrue(ready.contains("bot-123"))
        assertTrue(ready.contains("auth init"))
        val idle = plugin().promptBody(PluginStatus.NotReady("idle")).orEmpty()
        assertTrue(idle.contains("plugin_setup"))
        assertTrue(idle.contains("auth init"))
    }

    private suspend fun unavailableReason(plugin: WecomPlugin): String? =
        (plugin.availability() as? PluginAvailability.Unavailable)?.reason

    private fun plugin(runtime: FakeRuntime = FakeRuntime()): WecomPlugin = WecomPlugin(
        context = context,
        runtime = runtime,
        stagingRuntime = { runtime },
        downloader = PluginDownloader(),
        workspaceDir = File("build/tmp/wecom-test"),
    )

    private class FakeRuntime(
        var supported: Boolean = true,
        var installed: Boolean = true,
        var enabled: Boolean = true,
        var versionOutput: String = "wecom-cli 1.3.4 (npm 2026-03-24T12:00:00Z abc123)\n",
        var statusOutput: String = "authorized\n",
        var showOutput: String = "Status: authorized\nBot ID: bot-123\n",
    ) : WecomRuntime {
        val commands = mutableListOf<String>()

        override val isSupported: Boolean
            get() = supported

        override suspend fun isInstalled(): Boolean = installed

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult {
            commands += command
            return result(
                when {
                    command.contains("--version") -> versionOutput
                    command.contains("auth show --status") -> statusOutput
                    command.contains("auth show") -> showOutput
                    else -> ""
                },
            )
        }

        override suspend fun executeStreaming(
            command: String,
            timeoutMillis: Long,
            onOutputLine: (String) -> Unit,
        ): LinuxResult {
            commands += command
            return result("")
        }

        private fun result(output: String): LinuxResult = LinuxResult(
            exitCode = 0,
            output = output,
            truncated = false,
            timedOut = false,
            durationMillis = 0,
        )
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
