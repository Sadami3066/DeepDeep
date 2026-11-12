package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionInfoTest {
    @Test
    fun snapshotReadsLiveLookupEveryTime() {
        var current: PluginSessionSnapshot? = null
        val info = object : PluginSessionInfo {
            override val supportsChatDelivery: Boolean = true

            override fun snapshot(): PluginSessionSnapshot? = current
        }

        assertNull(info.snapshot())

        current = PluginSessionSnapshot("session-1", "/tmp/session-1.jsonl", "/workspace")
        assertEquals(PluginSessionSnapshot("session-1", "/tmp/session-1.jsonl", "/workspace"), info.snapshot())

        current = PluginSessionSnapshot("session-2", null, "/other")
        assertEquals("session-2", info.snapshot()?.sessionId)
        assertNull(info.snapshot()?.file)

        current = null
        assertNull(info.snapshot())
    }

    @Test
    fun supportsChatDeliveryIsExposed() {
        val enabled = object : PluginSessionInfo {
            override val supportsChatDelivery: Boolean = true

            override fun snapshot(): PluginSessionSnapshot? = null
        }
        val disabled = object : PluginSessionInfo {
            override val supportsChatDelivery: Boolean = false

            override fun snapshot(): PluginSessionSnapshot? = null
        }

        assertTrue(enabled.supportsChatDelivery)
        assertFalse(disabled.supportsChatDelivery)
    }
}
