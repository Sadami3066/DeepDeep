package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.plugin.api.PluginSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinPluginManifestsTest {
    @Test
    fun downloadablePluginsAreInstallable() {
        val manifests = listOf(cliFeishuManifest(), cliWecomManifest(), cliJwxtManifest())
        manifests.forEach { manifest ->
            assertEquals(manifest.id, PluginSource.EXTERNAL, manifest.source)
            assertTrue(manifest.id, manifest.installable)
        }
    }

    @Test
    fun micloudIsExternalButNotInstallable() {
        val manifest = androidMiCloudManifest()
        assertEquals(PluginSource.EXTERNAL, manifest.source)
        assertFalse(manifest.installable)
    }
}
