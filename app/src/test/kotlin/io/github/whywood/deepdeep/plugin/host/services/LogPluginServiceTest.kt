package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.PluginLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LogPluginServiceTest {
    @Test
    fun forwardsEveryLevelToSink() {
        val events = mutableListOf<Entry>()
        val service = LogPluginService("demo") { level, message, error ->
            events += Entry(level, message, error)
        }
        val failure = IllegalStateException("boom")

        service.debug("d")
        service.info("i")
        service.warn("w", failure)
        service.error("e")

        assertEquals(
            listOf(PluginLogLevel.DEBUG, PluginLogLevel.INFO, PluginLogLevel.WARN, PluginLogLevel.ERROR),
            events.map { it.level },
        )
        assertEquals(listOf("d", "i", "w", "e"), events.map { it.message })
        assertSame(failure, events[2].error)
    }

    private data class Entry(val level: PluginLogLevel, val message: String, val error: Throwable?)
}
