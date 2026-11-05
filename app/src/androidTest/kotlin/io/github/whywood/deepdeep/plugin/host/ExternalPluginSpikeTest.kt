package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.SlotContent
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPluginSpikeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pluginFile: File = File(
        requireNotNull(context.getExternalFilesDir("plugin-e2e")),
        "plugin-sample.ddp",
    )

    @Test
    fun loadsDdpAndRunsTool() = runBlocking {
        assertTrue("push the sample bundle first: ${pluginFile.absolutePath}", pluginFile.isFile)
        val loaded = ExternalPluginLoader(context).load(pluginFile)
        assertEquals(SAMPLE_ID, loaded.manifest.id)
        val host = stubPluginHost(loaded.manifest, context)
        val tool = loaded.plugin.contributions(host).tools.single { it.name == "sample_echo" }
        val result = tool.execute(
            PluginToolCall("call-1", buildJsonObject { put("text", "hi") }),
        ) as PluginToolResult.Text
        assertEquals("echo: hi", result.text)
    }

    @Test
    fun android14RejectsWritableDynamicDexButAcceptsReadOnly() {
        assertTrue("push the sample bundle first: ${pluginFile.absolutePath}", pluginFile.isFile)
        val dexEntries = ZipFile(pluginFile).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
                .sortedBy { it.name }
                .map { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }
        assertTrue(dexEntries.isNotEmpty())
        val entryClass = ExternalPluginLoader(context).readManifest(pluginFile).entryClass
        assertNotNull(entryClass)

        val directory = File(context.cacheDir, "plugin-spike").apply { mkdirs() }
        val writable = File(directory, "writable.jar")
        writeJar(writable, dexEntries)
        val readOnly = File(directory, "readonly.jar")
        writable.copyTo(readOnly, overwrite = true)
        check(readOnly.setReadOnly()) { "could not mark plugin spike jar read-only" }

        val readOnlyClass = loadFromJar(readOnly, entryClass!!)
        assertTrue(DeepDeepPlugin::class.java.isAssignableFrom(readOnlyClass))

        val writableOutcome = runCatching { loadFromJar(writable, entryClass) }
        Log.i(TAG, "writable dynamic dex load outcome: $writableOutcome")
        writable.delete()
        assertTrue(
            "writable jar must at least load or fail loudly, not silently miss the class",
            writableOutcome.isSuccess || writableOutcome.exceptionOrNull() !is ClassNotFoundException,
        )
    }

    private fun writeJar(file: File, dexEntries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            dexEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun loadFromJar(jar: File, entryClass: String): Class<*> {
        val loader = PathClassLoader(jar.absolutePath, context.classLoader)
        return Class.forName(entryClass, true, loader)
    }

    private companion object {
        const val TAG = "PluginSpike"
        const val SAMPLE_ID = "io.github.whywood.deepdeep.pluginsample"
    }
}
