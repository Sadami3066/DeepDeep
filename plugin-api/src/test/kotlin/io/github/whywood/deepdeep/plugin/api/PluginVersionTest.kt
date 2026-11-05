package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginVersionTest {
    @Test
    fun parsesCoreVersion() {
        assertEquals(PluginVersion(1, 2, 3), PluginVersion.parse("1.2.3"))
    }

    @Test
    fun parsesLeadingV() {
        assertEquals(PluginVersion(1, 2, 3), PluginVersion.parse("v1.2.3"))
        assertEquals(PluginVersion(1, 2, 3), PluginVersion.parse("V1.2.3"))
    }

    @Test
    fun parsesAndKeepsSuffix() {
        assertEquals(PluginVersion(1, 2, 3, "-dev"), PluginVersion.parse("1.2.3-dev"))
        assertEquals(PluginVersion(1, 2, 3, "+build.5"), PluginVersion.parse("1.2.3+build.5"))
        assertEquals("1.2.3-dev", PluginVersion.parse("1.2.3-dev").toString())
    }

    @Test
    fun rejectsMalformedVersions() {
        listOf("", "1", "1.2", "1.2.3.4", "abc", "1.2.x", "1.2.3-", "-1.2.3").forEach { raw ->
            assertNull(raw, PluginVersion.parse(raw))
        }
    }

    @Test
    fun comparesCoreComponents() {
        assertTrue(PluginVersion.parse("1.0.0")!! < PluginVersion.parse("1.0.1")!!)
        assertTrue(PluginVersion.parse("1.9.0")!! < PluginVersion.parse("1.10.0")!!)
        assertTrue(PluginVersion.parse("2.0.0")!! > PluginVersion.parse("1.99.99")!!)
    }

    @Test
    fun compareCoreIgnoresSuffix() {
        assertEquals(0, PluginVersion.parse("1.0.0-dev")!!.compareCore(PluginVersion.parse("1.0.0")!!))
        assertEquals(0, PluginVersion.parse("0.2.0-dev")!!.compareCore(PluginVersion.parse("0.2.0+build.9")!!))
        assertTrue(PluginVersion.parse("1.0.0")!!.compareCore(PluginVersion.parse("1.0.1")!!) < 0)
    }

    @Test
    fun releaseIsNewerThanPrerelease() {
        assertTrue(PluginVersion.parse("1.0.0")!! > PluginVersion.parse("1.0.0-dev")!!)
        assertTrue(PluginVersion.parse("1.0.0-rc.1")!! < PluginVersion.parse("1.0.0")!!)
        assertTrue(PluginVersion.compare("1.0.0-dev", "1.0.0")!! < 0)
    }

    @Test
    fun comparesPrereleaseIdentifiers() {
        assertTrue(PluginVersion.parse("1.0.0-alpha")!! < PluginVersion.parse("1.0.0-beta")!!)
        assertTrue(PluginVersion.parse("1.0.0-alpha")!! < PluginVersion.parse("1.0.0-alpha.1")!!)
        assertTrue(PluginVersion.parse("1.0.0-alpha.1")!! < PluginVersion.parse("1.0.0-alpha.2")!!)
        assertTrue(PluginVersion.parse("1.0.0-alpha.2")!! < PluginVersion.parse("1.0.0-alpha.10")!!)
        assertTrue(PluginVersion.parse("1.0.0-1")!! < PluginVersion.parse("1.0.0-alpha")!!)
        assertEquals(0, PluginVersion.parse("1.0.0-alpha")!!.compareTo(PluginVersion.parse("1.0.0-alpha")!!))
    }

    @Test
    fun ignoresBuildMetadata() {
        assertEquals(0, PluginVersion.parse("1.0.0+build.9")!!.compareTo(PluginVersion.parse("1.0.0")!!))
        assertEquals(0, PluginVersion.compare("1.0.0", "v1.0.0+build.9"))
    }

    @Test
    fun compareReturnsNullForInvalidInput() {
        assertNull(PluginVersion.compare("1.0.0", "not-a-version"))
        assertNull(PluginVersion.compare("bad", "1.0.0"))
    }

    @Test
    fun sortsBySemverOrder() {
        val sorted = listOf("2.0.0", "1.2.0", "1.10.0", "1.2.1", "1.2.1-rc.1")
            .map { PluginVersion.parse(it)!! }
            .sorted()
            .map { it.toString() }
        assertEquals(listOf("1.2.0", "1.2.1-rc.1", "1.2.1", "1.10.0", "2.0.0"), sorted)
    }
}
