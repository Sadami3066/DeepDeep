package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclaredServiceTest {
    @Test
    fun undeclaredServiceIsRejectedEvenWhenAvailable() {
        val error = runCatching {
            declaredService("chat", setOf(PluginCapability.FILES, PluginCapability.APP), "service")
        }.exceptionOrNull()

        assertTrue(error is PluginServiceUnavailable)
        assertEquals(
            "plugin service is not available: chat (not declared in manifest)",
            error?.message,
        )
    }

    @Test
    fun declaredButUnavailableServiceReportsUnavailable() {
        val error = runCatching {
            declaredService<String>("screen", setOf(PluginCapability.SCREEN), null)
        }.exceptionOrNull()

        assertEquals("plugin service is not available: screen (unavailable)", error?.message)
    }

    @Test
    fun declaredAvailableServiceIsReturned() {
        assertEquals("service", declaredService("chat", setOf(PluginCapability.CHAT), "service"))
    }
}
