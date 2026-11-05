package io.github.whywood.deepdeep.plugin.host

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginPathsTest {
    private val filesDir = File("/data/user/0/io.github.whywood.deepdeep/files")
    private val pluginsRoot = File(filesDir, "plugins")

    @Test
    fun pluginsRootSitsUnderFilesDir() {
        assertEquals(File(filesDir, "plugins").path, PluginPaths.pluginsRoot(filesDir).path)
    }

    @Test
    fun pluginAndVersionRootsAreNested() {
        assertEquals(File(pluginsRoot, "sample.plugin").path, PluginPaths.pluginRoot(pluginsRoot, "sample.plugin").path)
        assertEquals(
            File(File(pluginsRoot, "sample.plugin"), "1.2.3").path,
            PluginPaths.versionRoot(pluginsRoot, "sample.plugin", "1.2.3").path,
        )
    }

    @Test
    fun versionDirectoriesAreCanonical() {
        val versionRoot = PluginPaths.versionRoot(pluginsRoot, "sample.plugin", "1.2.3")
        assertEquals(File(versionRoot, "bundle").path, PluginPaths.bundleDir(pluginsRoot, "sample.plugin", "1.2.3").path)
        assertEquals(File(versionRoot, "assets").path, PluginPaths.assetsDir(pluginsRoot, "sample.plugin", "1.2.3").path)
        assertEquals(File(versionRoot, "lib").path, PluginPaths.libDir(pluginsRoot, "sample.plugin", "1.2.3").path)
    }

    @Test
    fun dataDirIsStableAcrossVersions() {
        val data = PluginPaths.dataDir(pluginsRoot, "sample.plugin")
        assertEquals(File(File(pluginsRoot, "sample.plugin"), "data").path, data.path)
        assertEquals(data.path, PluginPaths.dataDir(pluginsRoot, "sample.plugin").path)
    }

    @Test
    fun suffixedVersionsKeepSuffixInPath() {
        assertEquals("1.2.3-dev", PluginPaths.versionRoot(pluginsRoot, "sample.plugin", "1.2.3-dev").name)
    }
}
