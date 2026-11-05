package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.testDescriptor
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.UiNode
import io.github.whywood.deepdeep.plugin.api.UiNodeTypes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SlotRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun registry(vararg bindings: SlotBinding): PluginRegistry = PluginRegistry().apply {
        register(
            testDescriptor(
                id = "alpha",
                contributions = PluginContributions(
                    ui = object : PluginUiContributions {
                        override val bindings: List<SlotBinding> = bindings.toList()
                    },
                ),
            ),
        )
    }

    private fun slotRegistry(registry: PluginRegistry): SlotRegistry =
        SlotRegistry({ registry.snapshot() }, File(temporaryFolder.root, "plugins"))

    @Test
    fun exposesVersionedBindings() {
        val binding = SlotBinding(
            slotId = SlotIds.SETUP_CONTENT,
            content = SlotContent.Declarative(UiNode(type = UiNodeTypes.TEXT, text = "hello")),
        )
        val entries = slotRegistry(registry(binding)).bindings(SlotIds.SETUP_CONTENT)

        assertEquals(1, entries.size)
        assertEquals("alpha", entries.single().pluginId)
        assertEquals("1.0.0", entries.single().manifestVersion)
        assertTrue(entries.single().uiTreeValid)
        assertNotNull(slotRegistry(registry(binding)).firstRenderable(SlotIds.SETUP_CONTENT))
    }

    @Test
    fun invalidDeclarativeTreeIsNotRenderable() {
        val binding = SlotBinding(
            slotId = SlotIds.SETUP_CONTENT,
            content = SlotContent.Declarative(UiNode(type = "not-a-node")),
        )
        val registry = registry(binding)

        val entry = slotRegistry(registry).bindings(SlotIds.SETUP_CONTENT).single()
        assertFalse(entry.uiTreeValid)
        assertNull(slotRegistry(registry).firstRenderable(SlotIds.SETUP_CONTENT))
    }
}
