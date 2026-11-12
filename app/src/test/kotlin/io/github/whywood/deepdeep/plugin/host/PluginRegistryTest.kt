package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PromptSection
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.composerAction
import io.github.whywood.deepdeep.plugin.api.pluginSkill
import io.github.whywood.deepdeep.plugin.api.promptSection

class PluginRegistryTest {
    @Test
    fun startsEmptyAtVersionZero() {
        val snapshot = PluginRegistry().snapshot()
        assertEquals(0L, snapshot.version)
        assertTrue(snapshot.plugins.isEmpty())
        assertTrue(snapshot.tools.isEmpty())
        assertNull(snapshot.plugin("missing"))
    }

    @Test
    fun registerBumpsVersionAndExposesPlugins() {
        val registry = PluginRegistry()
        assertEquals(PluginRegistration.Registered, registry.register(testDescriptor("alpha")))

        val snapshot = registry.snapshot()
        assertEquals(1L, snapshot.version)
        assertEquals("alpha", snapshot.plugins.single().id)
        assertEquals(PluginPhase.ACTIVE, snapshot.plugins.single().phase)
        assertEquals("alpha", registry.descriptor("alpha")?.id)
    }

    @Test
    fun duplicatePluginIdIsRejectedWithoutVersionBump() {
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha"))
        val rejected = registry.register(testDescriptor("alpha"))

        assertTrue(rejected is PluginRegistration.Rejected)
        assertEquals("alpha", (rejected as PluginRegistration.Rejected).pluginId)
        assertEquals(1L, registry.snapshot().version)
        assertEquals(1, registry.snapshot().plugins.size)
    }

    @Test
    fun unregisterRemovesPluginAndBumpsVersion() {
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha"))
        assertTrue(registry.unregister("alpha"))

        val snapshot = registry.snapshot()
        assertEquals(2L, snapshot.version)
        assertTrue(snapshot.plugins.isEmpty())
        assertNull(registry.descriptor("alpha"))
        assertEquals(false, registry.unregister("missing"))
        assertEquals(2L, registry.snapshot().version)
    }

    @Test
    fun phaseControlsSnapshotcontributions() {
        val registry = PluginRegistry()
        val contributions = PluginContributions(tools = listOf(testTool("alpha_tool")))
        registry.register(testDescriptor("alpha", phase = PluginPhase.FAILED, contributions = contributions))
        assertEquals(0, registry.snapshot().tools.size)

        assertTrue(registry.update("alpha") { it.copy(phase = PluginPhase.ACTIVE) })
        val active = registry.snapshot()
        assertEquals(2L, active.version)
        assertEquals(listOf("alpha_tool"), active.tools.map { it.tool.name })

        assertTrue(registry.update("alpha") { it.copy(phase = PluginPhase.DISABLED) })
        assertEquals(0, registry.snapshot().tools.size)

        assertEquals(false, registry.update("missing") { it })
    }

    @Test
    fun updateContributionsRebuildsSnapshot() {
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha"))
        val contributions = PluginContributions(tools = listOf(testTool("late_tool")))

        assertTrue(registry.update("alpha") { it.copy(contributions = contributions) })
        val snapshot = registry.snapshot()
        assertEquals(2L, snapshot.version)
        assertEquals(listOf("late_tool"), snapshot.tools.map { it.tool.name })
        assertEquals(listOf("late_tool"), registry.snapshot().contributionsOf("alpha")!!.tools.map { it.name })
        assertNull(registry.snapshot().contributionsOf("missing"))
    }

    @Test
    fun duplicateToolNamesKeepFirstRegistrationAndReportConflict() {
        val registry = PluginRegistry()
        registry.register(
            testDescriptor(
                "alpha",
                contributions = PluginContributions(tools = listOf(testTool("shared"), testTool("alpha_tool"))),
            ),
        )
        registry.register(
            testDescriptor(
                "beta",
                contributions = PluginContributions(tools = listOf(testTool("shared"), testTool("beta_tool"))),
            ),
        )

        val snapshot = registry.snapshot()
        assertEquals(listOf("shared", "alpha_tool", "beta_tool"), snapshot.tools.map { it.tool.name })
        assertEquals("alpha", snapshot.tools.first().pluginId)
        assertEquals(1, snapshot.conflicts.size)
        assertEquals(
            PluginConflict(PluginConflictKind.TOOL, "shared", listOf("alpha", "beta")),
            snapshot.conflicts.single(),
        )
    }

    @Test
    fun duplicateSkillAndPromptSectionIdsAreReported() {
        val registry = PluginRegistry()
        registry.register(
            testDescriptor(
                "alpha",
                contributions = PluginContributions(
                    skills = listOf(pluginSkill("shared_skill", "alpha body")),
                    promptSections = listOf(promptSection("shared_section", "alpha body")),
                ),
            ),
        )
        registry.register(
            testDescriptor(
                "beta",
                contributions = PluginContributions(
                    skills = listOf(pluginSkill("shared_skill", "beta body")),
                    promptSections = listOf(promptSection("shared_section", "beta body")),
                ),
            ),
        )

        val snapshot = registry.snapshot()
        assertEquals(listOf("alpha body"), snapshot.skills.map { it.skill.markdown })
        assertEquals(
            listOf(
                PluginConflict(PluginConflictKind.SKILL, "shared_skill", listOf("alpha", "beta")),
                PluginConflict(PluginConflictKind.PROMPT_SECTION, "shared_section", listOf("alpha", "beta")),
            ),
            snapshot.conflicts,
        )
    }

    @Test
    fun aggregatesPerPluginContributions() {
        val registry = PluginRegistry()
        registry.register(
            testDescriptor(
                "alpha",
                contributions = PluginContributions(hooks = PluginHooks.None),
            ),
        )

        val snapshot = registry.snapshot()
        assertEquals(listOf("alpha"), snapshot.ui.map { it.pluginId })
        assertEquals(listOf("alpha"), snapshot.hooks.map { it.pluginId })
        assertTrue(snapshot.bindings.isEmpty())
        assertTrue(snapshot.pages.isEmpty())
        assertTrue(snapshot.composerActions.isEmpty())
    }

    @Test
    fun nonContributingPhasesAreExcludedFromAggregation() {
        val contributions = PluginContributions(tools = listOf(testTool("alpha_tool")))
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha", phase = PluginPhase.FAILED, contributions = contributions))
        assertTrue(registry.snapshot().tools.isEmpty())
        assertTrue(registry.snapshot().ui.isEmpty())
        assertTrue(registry.snapshot().plugins.isNotEmpty())
    }

    @Test
    fun snapshotIsStableAfterLaterMutations() {
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha"))
        val snapshot = registry.snapshot()

        registry.register(testDescriptor("beta"))
        assertEquals(1L, snapshot.version)
        assertEquals(1, snapshot.plugins.size)
        assertNull(snapshot.plugin("beta"))

        val next = registry.snapshot()
        assertEquals(2L, next.version)
        assertEquals(listOf("alpha", "beta"), next.plugins.map { it.id })
    }

    @Test
    fun updateKeepsRegistrationOrder() {
        val registry = PluginRegistry()
        registry.register(testDescriptor("alpha"))
        registry.register(testDescriptor("beta"))
        assertTrue(registry.update("alpha") { it.copy(phase = PluginPhase.DEGRADED, error = "boom") })

        val snapshot = registry.snapshot()
        assertEquals(listOf("alpha", "beta"), snapshot.plugins.map { it.id })
        assertEquals("boom", snapshot.plugin("alpha")?.error)
        assertEquals(PluginPhase.DEGRADED, snapshot.plugin("alpha")?.phase)
        assertNull(snapshot.plugin("beta")?.error)
    }

    @Test
    fun concurrentRegistrationsKeepVersionConsistent() {
        val registry = PluginRegistry()
        val start = CountDownLatch(1)
        val threads = (0 until 8).map { index ->
            Thread {
                start.await()
                registry.register(testDescriptor("plugin_$index"))
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)

        val snapshot = registry.snapshot()
        assertEquals(8, snapshot.plugins.size)
        assertEquals(8L, snapshot.version)
        assertEquals((0 until 8).map { "plugin_$it" }.toSet(), snapshot.plugins.map { it.id }.toSet())
        assertNotNull(snapshot.plugin("plugin_3"))
    }
}
