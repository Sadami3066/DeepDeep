package io.github.whywood.deepdeep.plugin.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginBinaryCompatTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val graph get() = (context.applicationContext as DeepDeepApplication).graph

    private val manager get() = graph.pluginManager

    private val fixtureDirectory: File = requireNotNull(context.getExternalFilesDir(FIXTURE_DIR_NAME))

    private val fixtureV1: File = File(fixtureDirectory, FIXTURE_V1)

    private val fixtureV2: File = File(fixtureDirectory, FIXTURE_V2)

    @Test
    fun installsEnablesDisablesUpgradesAndUninstalls() = runBlocking {
        try {
            step("check-fixtures") {
                assertTrue("missing ${fixtureV1.absolutePath}; run scripts/plugin-binary-compat.sh", fixtureV1.isFile)
                assertTrue("missing ${fixtureV2.absolutePath}; run scripts/plugin-binary-compat.sh", fixtureV2.isFile)
            }
            step("start-host") {
                withTimeout(60_000) { manager.start() }
                assertFalse("plugin manager is still loading", manager.state.value.loading)
            }
            step("clean-previous-install") {
                uninstallIfPresent()
            }
            step("inspect-v1") {
                val inspection = manager.inspectExternal(fixtureV1)
                assertNotNull("v1 package could not be inspected", inspection)
                assertNull("v1 inspection error: ${inspection!!.error}", inspection.error)
                assertEquals(PLUGIN_ID, inspection.pluginId)
                assertEquals(V1, inspection.version)
                assertEquals(EXPECTED_KEY_ID, inspection.publisherKeyId)
            }
            step("install-v1") {
                val install = manager.installExternal(fixtureV1, acceptPublisher = true)
                assertTrue("v1 install failed: ${install.error}", install.success)
            }
            step("active-v1") {
                val active = awaitSnapshot(V1, PluginPhaseUi.ACTIVE)
                assertTrue(active.enabled)
                assertEquals(V1, active.installedVersion)
                assertTrue("v1 plugin is not available", active.availability is PluginAvailabilityUi.Available)
                assertTrue("v1 pages missing", manager.pagesOf(PLUGIN_ID).any { it.id == PAGE_ID })
            }
            step("tool-v1") {
                assertEquals("echo: before-disable", callEcho("before-disable"))
            }
            step("disable") {
                manager.setEnabled(PLUGIN_ID, false)
                val disabled = awaitSnapshot(V1, PluginPhaseUi.DISABLED)
                assertFalse(disabled.enabled)
                assertNull(
                    "disabled plugin must not contribute tools",
                    manager.tools(SessionBinding()).firstOrNull { it.name == ECHO_TOOL },
                )
            }
            step("enable") {
                manager.setEnabled(PLUGIN_ID, true)
                val enabled = awaitSnapshot(V1, PluginPhaseUi.ACTIVE)
                assertTrue(enabled.enabled)
                assertEquals("echo: after-enable", callEcho("after-enable"))
            }
            step("install-v2") {
                val install = manager.installExternal(fixtureV2, acceptPublisher = true)
                assertTrue("v2 upgrade failed: ${install.error}", install.success)
                if (install.restartRequired) {
                    val pending = awaitSnapshot(V1, PluginPhaseUi.PENDING_RESTART)
                    assertEquals(V1, pending.installedVersion)
                    withTimeout(120_000) { manager.start() }
                }
            }
            step("active-v2") {
                val upgraded = awaitSnapshot(V2, PluginPhaseUi.ACTIVE)
                assertEquals(V2, upgraded.installedVersion)
                assertTrue("v2 plugin is not available", upgraded.availability is PluginAvailabilityUi.Available)
                assertEquals("echo: after-upgrade", callEcho("after-upgrade"))
            }
            step("uninstall") {
                manager.uninstall(PLUGIN_ID)
                awaitGone(PLUGIN_ID)
                assertNull("plugin snapshot must disappear after uninstall", manager.state.value.snapshot(PLUGIN_ID))
                assertTrue(
                    "plugin directory was not removed",
                    waitGone(File(File(context.filesDir, PLUGINS_DIR), PLUGIN_ID)),
                )
            }
        } finally {
            runCatching { uninstallIfPresent() }
        }
    }

    private suspend fun <T> step(name: String, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        throw AssertionError("[step: $name] ${error.message ?: error.javaClass.simpleName}", error)
    }

    private suspend fun callEcho(text: String): String {
        val tools = manager.tools(SessionBinding())
        val tool = tools.firstOrNull { it.name == ECHO_TOOL }
        assertNotNull("$ECHO_TOOL missing from ${tools.map { it.name }}", tool)
        val result = tool!!.execute("binary-compat-call", buildJsonObject { put("text", text) }, null, null)
        val content = result.content.toString()
        assertTrue("unexpected tool result: $content", content.contains("echo: $text"))
        return "echo: $text"
    }

    private suspend fun awaitSnapshot(
        expectedVersion: String,
        expectedPhase: PluginPhaseUi,
        timeoutMillis: Long = 30_000,
    ): PluginSnapshot = withTimeout(timeoutMillis) {
        while (true) {
            val snapshot = manager.state.value.snapshot(PLUGIN_ID)
            if (
                snapshot != null &&
                snapshot.installedVersion == expectedVersion &&
                snapshot.phase == expectedPhase
            ) {
                return@withTimeout snapshot
            }
            delay(200)
        }
        error("unreachable")
    }

    private suspend fun awaitGone(pluginId: String, timeoutMillis: Long = 30_000) {
        withTimeout(timeoutMillis) {
            while (manager.state.value.snapshot(pluginId) != null) delay(200)
        }
    }

    private suspend fun waitGone(file: File, timeoutMillis: Long = 10_000): Boolean = withTimeout(timeoutMillis) {
        while (file.exists()) delay(100)
        true
    }

    private suspend fun uninstallIfPresent() {
        if (manager.state.value.snapshot(PLUGIN_ID) == null) return
        manager.uninstall(PLUGIN_ID)
        awaitGone(PLUGIN_ID)
    }

    private companion object {
        const val FIXTURE_DIR_NAME = "plugin-binary-compat"

        const val FIXTURE_V1 = "sample-1.0.0.ddp"

        const val FIXTURE_V2 = "sample-1.0.1.ddp"

        const val PLUGIN_ID = "io.github.whywood.deepdeep.pluginsample"

        const val ECHO_TOOL = "sample_echo"

        const val PAGE_ID = "sample.main"

        const val V1 = "1.0.0"

        const val V2 = "1.0.1"

        const val EXPECTED_KEY_ID = "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213"

        const val PLUGINS_DIR = "plugins"
    }
}
