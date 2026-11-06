package io.github.whywood.deepdeep.plugin.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginBundleE2ETest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val graph get() = (context.applicationContext as DeepDeepApplication).graph

    private val manager get() = graph.pluginManager

    private val bundleDirectory: File by lazy {
        File(requireNotNull(context.getExternalFilesDir("plugin-e2e")), "")
    }

    @Test
    fun installsEveryPushedBundleAndKeepsThemActive() = runBlocking {
        val bundles = bundleDirectory.listFiles().orEmpty().filter { it.extension == "ddp" }.sortedBy { it.name }
        assertTrue("push plugin bundles to ${bundleDirectory.absolutePath}", bundles.isNotEmpty())
        withTimeout(90_000) { manager.start() }
        val failures = mutableListOf<String>()
        bundles.forEach { bundle ->
            try {
                installVerified(bundle)
            } catch (error: Throwable) {
                failures += "${bundle.name}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        assertTrue("plugin bundle failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private suspend fun installVerified(bundle: File) {
        val manifest = readManifest(bundle)
        val pluginId = manifest.getValue("id").jsonPrimitive.content
        val provides = manifest["provides"]?.takeIf { it is JsonObject }?.jsonObject
        val expectedTools = provides?.get("tools").stringList()
        val expectedSkills = provides?.get("skills").stringList()
        val expectedPages = provides?.get("pages").stringList()
        val expectedCapabilities = manifest["capabilities"].stringList().toSet()

        uninstallIfPresent(pluginId)
        val inspection = manager.inspectExternal(bundle)
        assertNotNull("$bundle could not be inspected", inspection)
        assertEquals("${bundle.name} inspection error", null, inspection!!.error)
        val install = manager.installExternal(bundle, acceptPublisher = true)
        assertTrue("${bundle.name} install failed: ${install.error}", install.success)

        val snapshot = awaitSnapshot(pluginId) { it.installed && it.enabled }
        assertEquals("${bundle.name} pages", expectedPages, snapshot.pages.map { it.id })
        assertEquals("${bundle.name} skills", expectedSkills, snapshot.skills)
        assertEquals("${bundle.name} capabilities", expectedCapabilities, snapshot.capabilities)
        assertTrue("${bundle.name} is not active: ${snapshot.phase}", snapshot.phase == PluginPhaseUi.ACTIVE)
        manager.refreshNow()
        val toolNames = allToolNames()
        val errors = manager.state.value.plugins.joinToString { "${it.id}:${it.phase}" }
        expectedTools.forEach { tool ->
            assertTrue(
                "${bundle.name} tool $tool missing from $toolNames (lastError=${snapshot.lastError}, plugins=$errors)",
                toolNames.contains(tool),
            )
        }
        expectedPages.forEach { page ->
            assertTrue(
                "${bundle.name} page $page is missing",
                manager.pagesOf(pluginId).any { it.id == page },
            )
        }
        val icon = File(
            File(File(context.filesDir, "plugins"), pluginId),
            snapshot.installedVersion.orEmpty() + "/assets/icon.png",
        )
        assertTrue("${bundle.name} icon was not extracted: ${icon.absolutePath}", icon.isFile)
    }

    @Test
    fun updatesPluginWhileIdleWithoutRestart() = runBlocking {
        val base = bundleDirectory.listFiles().orEmpty().firstOrNull { it.name == "interop.ddp" }
        assertNotNull("push interop.ddp to ${bundleDirectory.absolutePath}", base)
        val updateDirectory = requireNotNull(context.getExternalFilesDir("plugin-e2e-update"))
        val update = File(updateDirectory, "interop.ddp")
        assertTrue("push the newer interop bundle to ${update.absolutePath}", update.isFile)
        withTimeout(90_000) { manager.start() }
        installVerified(base!!)
        val updateManifest = readManifest(update)
        val pluginId = updateManifest.getValue("id").jsonPrimitive.content
        val updateVersion = updateManifest.getValue("version").jsonPrimitive.content
        val result = manager.installExternal(update, acceptPublisher = true)
        assertTrue("hot update failed: ${result.error}", result.success)
        assertFalse("hot update should not require a restart", result.restartRequired)
        val snapshot = awaitSnapshot(pluginId) { it.installed && it.enabled && it.installedVersion == updateVersion }
        assertEquals(PluginPhaseUi.ACTIVE, snapshot.phase)
    }

    private fun allToolNames(): Set<String> =
        (manager.tools(SessionBinding()).map { it.name } + manager.deferredTools(SessionBinding()).map { it.name })
            .toSet()

    private fun readManifest(bundle: File): JsonObject = ZipFile(bundle).use { zip ->
        val entry = requireNotNull(zip.getEntry("assets/plugin.json")) { "missing assets/plugin.json in ${bundle.name}" }
        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    private fun kotlinx.serialization.json.JsonElement?.stringList(): List<String> =
        (this as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    private suspend fun awaitSnapshot(
        pluginId: String,
        timeoutMillis: Long = 60_000,
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
}
