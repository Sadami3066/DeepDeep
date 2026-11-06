package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.host.PluginHooksEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHookSourceTest {
    private val baseHooks = probeHooks()
    private val otherHooks = probeHooks()

    private fun entry(pluginId: String, hooks: PluginHooks = baseHooks): PluginHooksEntry = hookEntry(pluginId, hooks)

    @Test
    fun sessionHooksOverrideBaseHooksPerPluginAndKeepOrder() {
        val source = SessionHookSource(
            snapshots = { HookSnapshot(3L, listOf(entry("alpha"), entry("beta"))) },
            sessionHooks = mapOf("beta" to otherHooks),
        )

        val entries = source.entries()

        assertEquals(listOf("alpha", "beta"), entries.map { it.pluginId })
        assertSame(baseHooks, entries[0].hooks)
        assertSame(otherHooks, entries[1].hooks)
        assertEquals(3L, source.snapshot().version)
    }

    @Test
    fun sessionHooksForPluginOutsideBaseAreIgnored() {
        val source = SessionHookSource(
            snapshots = { HookSnapshot(1L, listOf(entry("alpha"))) },
            sessionHooks = mapOf("ghost" to otherHooks),
        )

        assertSame(baseHooks, source.entries().single().hooks)
    }

    @Test
    fun emptySessionHooksKeepBaseHooks() {
        val source = SessionHookSource(
            snapshots = { HookSnapshot(1L, listOf(entry("alpha"))) },
            sessionHooks = mapOf("alpha" to PluginHooks.None),
        )

        assertSame(baseHooks, source.entries().single().hooks)
    }

    @Test
    fun frozenEntriesSurviveBaseSnapshotChanges() {
        var version = 1L
        var entries = listOf(entry("alpha"))
        val source = SessionHookSource(snapshots = { HookSnapshot(version, entries) }, sessionHooks = emptyMap())

        source.freeze("run-1")
        version = 2L
        entries = listOf(entry("beta"))

        assertEquals(listOf("alpha"), source.entries().map { it.pluginId })
        assertEquals(1L, source.snapshot().version)

        val released = source.release("run-1")

        assertEquals(listOf("beta"), released?.entries?.map { it.pluginId })
        assertEquals(2L, released?.version)
        assertEquals(listOf("beta"), source.entries().map { it.pluginId })
    }

    @Test
    fun freezeCapturesSessionOverrides() {
        var entries = listOf(entry("alpha"))
        val source = SessionHookSource(
            snapshots = { HookSnapshot(1L, entries) },
            sessionHooks = mapOf("alpha" to otherHooks),
        )

        source.freeze("run-1")
        entries = listOf(entry("beta"))

        assertSame(otherHooks, source.entries().single().hooks)
    }

    @Test
    fun releaseOnlyMatchesCurrentRun() {
        val source = SessionHookSource(
            snapshots = { HookSnapshot(1L, listOf(entry("alpha"))) },
            sessionHooks = emptyMap(),
        )

        source.freeze("run-1")

        assertNull(source.release("run-2"))
        assertEquals(listOf("alpha"), source.entries().map { it.pluginId })
        assertFalse(source.frozenIsEmpty("run-1"))
        assertTrue(source.release("run-1") != null)
    }

    @Test
    fun startingNewRunReplacesFrozenRun() {
        var version = 1L
        var entries = listOf(entry("alpha"))
        val source = SessionHookSource(snapshots = { HookSnapshot(version, entries) }, sessionHooks = emptyMap())

        source.freeze("run-1")
        version = 2L
        entries = listOf(entry("beta"))

        assertEquals(2L, source.freeze("run-2").version)
        assertEquals(listOf("beta"), source.entries().map { it.pluginId })
        assertNull(source.release("run-1"))
        assertEquals(2L, source.release("run-2")?.version)
    }

    @Test
    fun frozenIsEmptyOnlyForMatchingRun() {
        val source = SessionHookSource(snapshots = { HookSnapshot(1L, emptyList()) }, sessionHooks = emptyMap())

        assertFalse(source.frozenIsEmpty("run-1"))

        source.freeze("run-1")

        assertTrue(source.frozenIsEmpty("run-1"))
        assertFalse(source.frozenIsEmpty("run-2"))

        source.release("run-1")

        assertFalse(source.frozenIsEmpty("run-1"))
    }
}
