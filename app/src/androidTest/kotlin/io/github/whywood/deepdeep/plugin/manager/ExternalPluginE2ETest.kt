package io.github.whywood.deepdeep.plugin.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.io.File
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
class ExternalPluginE2ETest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val graph get() = (context.applicationContext as DeepDeepApplication).graph

    private val manager get() = graph.pluginManager

    private val bundle: File = File(
        requireNotNull(context.getExternalFilesDir("plugin-e2e")),
        "plugin-sample-signed.ddp",
    )

    private val fixtureDirectory: File by lazy { File(context.cacheDir, "plugin-e2e-fixtures") }

    @Test
    fun installsRunsDisablesAndUninstallsExternalPlugin() = runBlocking {
        assertTrue("push the signed sample first: ${bundle.absolutePath}", bundle.isFile)
        withTimeout(60_000) { manager.start() }
        try {
            uninstallIfPresent(PLUGIN_ID)
            val inspection = manager.inspectExternal(bundle)
            assertNotNull(inspection)
            assertNull(inspection!!.error)
            assertTrue(inspection.sha256.isNotBlank())
            assertTrue(inspection.publisherKeyId.startsWith("ed25519:"))

            val install = manager.installExternal(bundle, acceptPublisher = true)
            assertTrue("install failed: ${install.error}", install.success)

            val installed = awaitSnapshot(PLUGIN_ID) { it.installed }
            assertTrue(installed.enabled)
            assertEquals(IconRef.Asset("icon.png"), installed.icon)
            assertEquals(listOf("sample.main"), installed.pages.map { it.id })
            assertTrue(
                "sample page missing",
                manager.pagesOf(PLUGIN_ID).any { it.id == "sample.main" },
            )
            val icon = File(
                File(File(context.filesDir, "plugins"), PLUGIN_ID),
                installed.installedVersion.orEmpty() + "/assets/icon.png",
            )
            assertTrue("plugin icon was not extracted: ${icon.absolutePath}", icon.isFile)

            assertEquals("echo: e2e", callEcho("e2e"))

            manager.setEnabled(PLUGIN_ID, false)
            val disabled = awaitSnapshot(PLUGIN_ID) { !it.enabled }
            assertEquals(PluginPhaseUi.DISABLED, disabled.phase)
            assertNull(
                "disabled plugin must not contribute tools",
                manager.tools(SessionBinding()).firstOrNull { it.name == ECHO_TOOL },
            )

            manager.setEnabled(PLUGIN_ID, true)
            val enabled = awaitSnapshot(PLUGIN_ID) { it.enabled }
            assertEquals(PluginPhaseUi.ACTIVE, enabled.phase)
            assertEquals("echo: after-enable", callEcho("after-enable"))
        } finally {
            uninstallIfPresent(PLUGIN_ID)
        }
    }

    @Test
    fun rejectsTamperedPackageWithoutInstalling() = runBlocking {
        withTimeout(60_000) { manager.start() }
        val fixture = ExternalPluginDdpFixture(fixtureDirectory)
        val pluginId = "io.github.whywood.deepdeep.e2etampered"
        val tampered = fixture.build(
            name = "tampered.dpp",
            pluginId = pluginId,
            dexes = listOf(ExternalPluginDdpFixture.corruptDex()),
            tamperedSignature = true,
        )

        val inspection = manager.inspectExternal(tampered)
        assertNotNull(inspection)
        assertNotNull("tampered package must be rejected at inspection", inspection!!.error)
        val install = manager.installExternal(tampered, acceptPublisher = true)
        assertFalse("tampered package must not install", install.success)
        assertNotNull(install.error)
        assertNull(manager.state.value.snapshot(pluginId))
    }

    @Test
    fun brokenCandidatesFailWithoutBreakingOtherPlugins() = runBlocking {
        assertTrue("push the signed sample first: ${bundle.absolutePath}", bundle.isFile)
        withTimeout(60_000) { manager.start() }
        val fixture = ExternalPluginDdpFixture(fixtureDirectory)
        val plugins = listOf(
            "io.github.whywood.deepdeep.e2ebaddex" to fixture.build(
                name = "bad-dex.dpp",
                pluginId = "io.github.whywood.deepdeep.e2ebaddex",
                dexes = listOf(ExternalPluginDdpFixture.corruptDex()),
            ),
            "io.github.whywood.deepdeep.e2elink" to fixture.build(
                name = "bad-link.dpp",
                pluginId = "io.github.whywood.deepdeep.e2elink",
                entryClass = "io.github.whywood.deepdeep.e2elink.MissingPlugin",
                dexes = ExternalPluginDdpFixture.sampleDexes(bundle),
            ),
        )
        try {
            uninstallIfPresent(PLUGIN_ID)
            val good = manager.installExternal(bundle, acceptPublisher = true)
            assertTrue("sample install failed: ${good.error}", good.success)
            awaitSnapshot(PLUGIN_ID) { it.installed && it.phase == PluginPhaseUi.ACTIVE }
            plugins.forEach { (pluginId, file) ->
                val install = manager.installExternal(file, acceptPublisher = true)
                assertFalse("$pluginId must not activate: ${install.error}", install.success)
                manager.refreshNow()
                assertFalse(manager.state.value.loading)
                manager.state.value.snapshot(pluginId)?.let { failed ->
                    assertNotNull("$pluginId should record an error", failed.lastError)
                    assertEquals(PluginPhaseUi.FAILED, failed.phase)
                }
            }
            val stillActive = awaitSnapshot(PLUGIN_ID) { it.phase == PluginPhaseUi.ACTIVE }
            assertEquals(PluginPhaseUi.ACTIVE, stillActive.phase)
            assertEquals("echo: after-broken", callEcho("after-broken"))
        } finally {
            uninstallIfPresent(PLUGIN_ID)
            plugins.forEach { (pluginId, _) -> uninstallIfPresent(pluginId) }
        }
    }

    @Test
    fun updatesToNewVersionAndStaysActive() = runBlocking {
        assertTrue("push the signed sample first: ${bundle.absolutePath}", bundle.isFile)
        withTimeout(60_000) { manager.start() }
        val fixture = ExternalPluginDdpFixture(fixtureDirectory)
        val dexes = ExternalPluginDdpFixture.sampleDexes(bundle)
        try {
            uninstallIfPresent(PLUGIN_ID)
            val v1 = fixture.build(
                name = "update-v1.dpp",
                pluginId = PLUGIN_ID,
                version = "1.0.0",
                entryClass = SAMPLE_ENTRY_CLASS,
                dexes = dexes,
            )
            val first = manager.installExternal(v1, acceptPublisher = true)
            assertTrue("v1 install failed: ${first.error}", first.success)
            val active = awaitSnapshot(PLUGIN_ID) { it.installed && it.phase == PluginPhaseUi.ACTIVE }
            assertEquals("1.0.0", active.installedVersion)
            assertEquals("echo: v1", callEcho("v1"))

            val v2 = fixture.build(
                name = "update-v2.dpp",
                pluginId = PLUGIN_ID,
                version = "2.0.0",
                entryClass = SAMPLE_ENTRY_CLASS,
                dexes = dexes,
            )
            val staged = manager.installExternal(v2, acceptPublisher = true)
            assertTrue("v2 update failed: ${staged.error}", staged.success)
            if (staged.restartRequired) {
                val pending = awaitSnapshot(PLUGIN_ID) { it.phase == PluginPhaseUi.PENDING_RESTART }
                assertEquals("1.0.0", pending.installedVersion)
                withTimeout(120_000) { manager.start() }
            }
            val upgraded = awaitSnapshot(PLUGIN_ID) {
                it.installedVersion == "2.0.0" && it.phase == PluginPhaseUi.ACTIVE
            }
            assertEquals("echo: v2", callEcho("v2"))
        } finally {
            uninstallIfPresent(PLUGIN_ID)
        }
    }

    private suspend fun callEcho(text: String): String {
        val tools = manager.tools(SessionBinding())
        val tool = tools.firstOrNull { it.name == ECHO_TOOL }
        assertNotNull("$ECHO_TOOL missing from ${tools.map { it.name }}", tool)
        val result = tool!!.execute("e2e-call", buildJsonObject { put("text", text) }, null, null)
        val content = result.content.toString()
        assertTrue("unexpected tool result: $content", content.contains("echo: $text"))
        return "echo: $text"
    }

    private suspend fun awaitSnapshot(
        pluginId: String,
        timeoutMillis: Long = 30_000,
        predicate: (PluginSnapshot) -> Boolean,
    ): PluginSnapshot = withTimeout(timeoutMillis) {
        while (true) {
            val snapshot = manager.state.value.snapshot(pluginId)
            if (snapshot != null && predicate(snapshot)) return@withTimeout snapshot
            delay(200)
        }
        error("unreachable")
    }

    private suspend fun uninstallIfPresent(pluginId: String) {
        if (manager.state.value.snapshot(pluginId) == null) return
        manager.uninstall(pluginId)
        withTimeout(30_000) {
            while (manager.state.value.snapshot(pluginId) != null) delay(200)
        }
    }

    private companion object {
        const val PLUGIN_ID = "io.github.whywood.deepdeep.pluginsample"

        const val ECHO_TOOL = "sample_echo"

        const val SAMPLE_ENTRY_CLASS = "io.github.whywood.deepdeep.pluginsample.SamplePlugin"
    }
}
