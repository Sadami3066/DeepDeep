package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.whywood.deepdeep.plugin.host.events.HostPluginBusEvent

@OptIn(ExperimentalCoroutinesApi::class)
class PluginBusImplTest {
    @Test
    fun deliversOnlySubscribedTopic() = runTest {
        val bus = PluginBusImpl()
        val received = mutableListOf<PluginBusEvent>()
        val collector = launch { bus.subscribe("alpha").take(2).toList(received) }
        runCurrent()

        bus.publish(HostPluginBusEvent("alpha", "p", buildJsonObject { put("n", 1) }, 0))
        bus.publish(HostPluginBusEvent("beta", "p", kotlinx.serialization.json.JsonObject(emptyMap()), 0))
        bus.publish(HostPluginBusEvent("alpha", "p", kotlinx.serialization.json.JsonObject(emptyMap()), 0))
        advanceUntilIdle()
        collector.join()

        assertEquals(2, received.size)
        assertTrue(received.all { it.topic == "alpha" })
        assertTrue(received.all { it.publishedAt > 0 })
    }

    @Test
    fun scopedBusStampsPublisher() = runTest {
        val bus = PluginBusImpl()
        val scoped = ScopedPluginBus(bus, "demo-plugin")
        val received = async { bus.subscribe("topic").first() }
        runCurrent()

        scoped.publish(HostPluginBusEvent("topic", "ignored", kotlinx.serialization.json.JsonObject(emptyMap()), 0))

        assertEquals("demo-plugin", received.await().publisher)
    }
}
