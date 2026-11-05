package io.github.whywood.deepdeep.plugin.host.services

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginUiBridgeTest {
    @After
    fun clearPending() {
        PluginUiBridge.pendingPage.value?.let(PluginUiBridge::consumePage)
    }

    @Test
    fun pageRequestIsStoredUntilConsumed() {
        PluginUiBridge.requestPage("demo", "home")

        val request = PluginUiBridge.pendingPage.value
        assertEquals("demo", request?.pluginId)
        assertEquals("home", request?.pageId)

        PluginUiBridge.consumePage(request!!)

        assertNull(PluginUiBridge.pendingPage.value)
    }

    @Test
    fun pageRequestRejectsBlankIds() {
        assertThrows(IllegalArgumentException::class.java) { PluginUiBridge.requestPage(" ", "home") }
        assertThrows(IllegalArgumentException::class.java) { PluginUiBridge.requestPage("demo", "") }
    }

    @Test
    fun newerRequestWinsAndStaleConsumeIsIgnored() {
        PluginUiBridge.requestPage("demo", "first")
        val first = PluginUiBridge.pendingPage.value!!
        PluginUiBridge.requestPage("demo", "second")

        PluginUiBridge.consumePage(first)

        assertEquals("second", PluginUiBridge.pendingPage.value?.pageId)
    }
}
