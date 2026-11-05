package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.runtime.SessionBinding
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    @Test
    fun installsRunsAndUninstallsExternalPlugin() = runBlocking {
        assertTrue("push the signed sample first: ${bundle.absolutePath}", bundle.isFile)
        withTimeout(60_000) { manager.start() }
        try {
            val inspection = manager.inspectExternal(bundle)
            assertNotNull(inspection)
            assertNull(inspection!!.error)
            assertTrue(inspection.sha256.isNotBlank())
            assertTrue(inspection.publisherKeyId.startsWith("ed25519:"))

            val install = manager.installExternal(bundle, acceptPublisher = true)
            assertTrue("install failed: ${install.error}", install.success)

            manager.refreshNow()
            val snapshot = withTimeout(30_000) {
                while (true) {
                    val current = manager.state.value.snapshot(PLUGIN_ID)
                    if (current != null && current.installed) return@withTimeout current
                    delay(200)
                }
                error("unreachable")
            }
            assertTrue(snapshot.enabled)

            val tools = manager.tools(SessionBinding())
            val tool = tools.firstOrNull { it.name == "sample_echo" }
            assertNotNull("sample_echo missing from ${tools.map { it.name }}", tool)
            val result = tool!!.execute(
                "e2e-call",
                buildJsonObject { put("text", "e2e") },
                null,
                null,
            )
            val text = result.content.toString()
            assertTrue("unexpected tool result: $text", text.contains("echo: e2e"))
        } finally {
            manager.uninstall(PLUGIN_ID)
            withTimeout(30_000) {
                while (manager.state.value.snapshot(PLUGIN_ID) != null) delay(200)
            }
        }
    }

    private companion object {
        const val PLUGIN_ID = "io.github.whywood.deepdeep.pluginsample"
    }
}
