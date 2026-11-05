package io.github.whywood.deepdeep.plugin.jwxt

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.cliJwxtManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtPluginTest {
    @Test
    fun manifestMatchesPluginContract() {
        val manifest = cliJwxtManifest()
        assertEquals("jwxt", manifest.id)
        assertEquals(JwxtCli.VERSION, manifest.version)
        assertEquals(PluginSource.EXTERNAL, manifest.source)
        assertEquals(LocalizedText.Resource(R.string.plugin_jwxt_name), manifest.name)
        assertEquals(LocalizedText.Resource(R.string.plugin_jwxt_summary), manifest.summary)
        assertEquals(LocalizedText.Resource(R.string.plugin_jwxt_size), manifest.sizeLabel)
        assertEquals(IconRef.Resource(DdIcons.Globe.res), manifest.icon)
        assertEquals(listOf(JwxtCli.SKILL_ID), manifest.provides.skills)
        assertEquals(listOf("netid", "password"), manifest.provides.settings)
        assertTrue(manifest.installable)
    }
}
