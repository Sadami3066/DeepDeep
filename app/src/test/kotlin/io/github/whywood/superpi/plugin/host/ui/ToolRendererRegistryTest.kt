package io.github.whywood.superpi.plugin.host.ui

import io.github.whywood.superpi.plugin.api.PluginCapability
import io.github.whywood.superpi.plugin.api.PluginManifest
import io.github.whywood.superpi.plugin.api.PluginUiContributions
import io.github.whywood.superpi.plugin.api.ToolResultRenderer
import io.github.whywood.superpi.plugin.api.ToolResultSource
import io.github.whywood.superpi.plugin.api.UiManifest
import io.github.whywood.superpi.plugin.api.toolResultMatch
import io.github.whywood.superpi.plugin.api.toolResultRenderer
import io.github.whywood.superpi.plugin.host.PluginContributions
import io.github.whywood.superpi.plugin.host.PluginDescriptor
import io.github.whywood.superpi.plugin.host.PluginPhase
import io.github.whywood.superpi.plugin.host.PluginRegistry
import io.github.whywood.superpi.plugin.host.external.HostPluginManifest
import io.github.whywood.superpi.plugin.host.external.HostUiManifest
import io.github.whywood.superpi.plugin.host.external.hostLocalizedText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolRendererRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val hostVersions = HostUiVersions(kotlin = "2.4.21", compose = "1.10.5")

    private fun manifest(id: String, ui: UiManifest? = null): PluginManifest = HostPluginManifest(
        id = id,
        version = "1.0.0",
        ui = ui,
        name = hostLocalizedText("name of $id"),
        summary = hostLocalizedText("summary of $id"),
        capabilities = PluginCapability.ALL.toSet(),
    )

    private fun renderer(
        id: String,
        source: ToolResultSource? = null,
        server: String? = null,
        tool: String? = null,
        toolPrefix: String? = null,
        priority: Int = 0,
    ): ToolResultRenderer = toolResultRenderer(
        id = id,
        match = toolResultMatch(source = source, server = server, tool = tool, toolPrefix = toolPrefix),
        priority = priority,
        content = {},
    )

    private fun descriptor(
        id: String,
        renderers: List<ToolResultRenderer>,
        ui: UiManifest? = null,
        phase: PluginPhase = PluginPhase.ACTIVE,
    ): PluginDescriptor = PluginDescriptor(
        manifest = manifest(id, ui),
        phase = phase,
        contributions = PluginContributions(
            ui = object : PluginUiContributions, io.github.whywood.superpi.plugin.api.ToolResultRendererContributor {
                override val bindings = emptyList<io.github.whywood.superpi.plugin.api.SlotBinding>()
                override val toolResultRenderers: List<ToolResultRenderer> = renderers
            },
        ),
    )

    private fun registry(vararg descriptors: PluginDescriptor): ToolRendererRegistry {
        val registry = PluginRegistry()
        descriptors.forEach(registry::register)
        return ToolRendererRegistry({ registry.snapshot() }, File(temporaryFolder.root, "plugins"), hostVersions)
    }

    @Test
    fun resolvesBySourceServerAndTool() {
        val registry = registry(
            descriptor("alpha", listOf(renderer("a", source = ToolResultSource.MCP, server = "amap"))),
            descriptor("beta", listOf(renderer("b", source = ToolResultSource.MCP, server = "travel"))),
        )

        assertEquals("alpha", registry.resolve(ToolResultSource.MCP, "amap", "route")?.pluginId)
        assertEquals("beta", registry.resolve(ToolResultSource.MCP, "travel", "hotel_search")?.pluginId)
        assertNull(registry.resolve(ToolResultSource.MCP, "other", "route"))
    }

    @Test
    fun prefersHigherPriorityThenMoreSpecificMatchThenRegistrationOrder() {
        val registry = registry(
            descriptor("broad", listOf(renderer("broad", source = ToolResultSource.MCP, priority = 0))),
            descriptor("exact-first", listOf(renderer("exact", source = ToolResultSource.MCP, tool = "route"))),
            descriptor("exact-second", listOf(renderer("exact2", source = ToolResultSource.MCP, tool = "route"))),
        )

        assertEquals("exact-first", registry.resolve(ToolResultSource.MCP, "amap", "route")?.pluginId)
        assertEquals("broad", registry.resolve(ToolResultSource.MCP, "amap", "other")?.pluginId)
    }

    @Test
    fun higherPriorityBeatsSpecificity() {
        val registry = registry(
            descriptor("broad", listOf(renderer("broad", source = ToolResultSource.MCP, priority = 10))),
            descriptor("exact", listOf(renderer("exact", source = ToolResultSource.MCP, tool = "route", priority = 1))),
        )

        assertEquals("broad", registry.resolve(ToolResultSource.MCP, "amap", "route")?.pluginId)
    }

    @Test
    fun skipsNonContributingPluginsAndIncompatibleUi() {
        val registry = registry(
            descriptor(
                "disabled",
                listOf(renderer("disabled", source = ToolResultSource.MCP)),
                phase = PluginPhase.DISABLED,
            ),
            descriptor(
                "incompatible",
                listOf(renderer("incompatible", source = ToolResultSource.MCP)),
                ui = HostUiManifest(compose = "1.9.0"),
            ),
            descriptor(
                "active",
                listOf(renderer("active", source = ToolResultSource.MCP, server = "amap")),
            ),
        )

        assertEquals("active", registry.resolve(ToolResultSource.MCP, "amap", "route")?.pluginId)
        assertNull(registry.resolve(ToolResultSource.MCP, "elsewhere", "route"))
        val incompatible = registry.candidates(ToolResultSource.MCP, "amap", "route").first { it.pluginId == "incompatible" }
        assertTrue(!incompatible.composeCompatible)
    }

    @Test
    fun entriesOfReturnsPluginRenderers() {
        val registry = registry(
            descriptor(
                "alpha",
                listOf(
                    renderer("one", source = ToolResultSource.MCP, server = "amap"),
                    renderer("two", source = ToolResultSource.BUILTIN, tool = "bash"),
                ),
            ),
        )

        assertEquals(listOf("one", "two"), registry.entriesOf("alpha").map { it.renderer.id })
    }
}
