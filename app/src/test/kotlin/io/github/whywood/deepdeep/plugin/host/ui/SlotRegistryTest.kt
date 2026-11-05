package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.UiManifest
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SlotRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val hostVersions = HostUiVersions(kotlin = "2.4.21", compose = "1.10.5")

    private fun manifest(id: String, ui: UiManifest? = null): PluginManifest = PluginManifest(
        id = id,
        version = "1.0.0",
        ui = ui,
        name = LocalizedText.Literal("name of $id"),
        summary = LocalizedText.Literal("summary of $id"),
        source = PluginSource.BUILTIN,
    )

    private fun descriptor(
        id: String,
        bindings: List<SlotBinding>,
        ui: UiManifest? = null,
        phase: PluginPhase = PluginPhase.ACTIVE,
    ): PluginDescriptor = PluginDescriptor(
        manifest = manifest(id, ui),
        source = PluginSource.BUILTIN,
        phase = phase,
        contributions = PluginContributions(
            ui = object : PluginUiContributions {
                override val bindings: List<SlotBinding> = bindings
            },
        ),
    )

    private fun binding(slotId: String, priority: Int = 0): SlotBinding = SlotBinding(
        slotId = slotId,
        priority = priority,
        content = SlotContent.Compose(uiApiLevel = 1) { },
    )

    private fun registry(vararg descriptors: PluginDescriptor): SlotRegistry {
        val registry = PluginRegistry()
        descriptors.forEach(registry::register)
        return SlotRegistry({ registry.snapshot() }, File(temporaryFolder.root, "plugins"), hostVersions)
    }

    @Test
    fun resolvesBindingsBySlotId() {
        val registry = registry(
            descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT))),
            descriptor("beta", listOf(binding(SlotIds.TOOL_CARD_PREFIX + "echo"))),
        )

        val setup = registry.bindings(SlotIds.SETUP_CONTENT)
        assertEquals(listOf("alpha"), setup.map { it.pluginId })
        assertEquals("1.0.0", setup.single().manifestVersion)
        assertNull(setup.single().disabledReason)
        assertEquals(SlotIds.TOOL_CARD_PREFIX + "echo", registry.bindings(SlotIds.TOOL_CARD_PREFIX + "echo").single().slotId)
    }

    @Test
    fun sortsByPriorityThenRegistrationOrder() {
        val registry = registry(
            descriptor("first", listOf(binding(SlotIds.SETUP_CONTENT, priority = 0))),
            descriptor("second", listOf(binding(SlotIds.SETUP_CONTENT, priority = 10))),
            descriptor("third", listOf(binding(SlotIds.SETUP_CONTENT, priority = 0))),
        )

        assertEquals(listOf("second", "first", "third"), registry.bindings(SlotIds.SETUP_CONTENT).map { it.pluginId })
    }

    @Test
    fun entriesOfFiltersByPluginAndSkipsNonContributingPhases() {
        val active = descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)))
        val disabled = descriptor("alpha-disabled", listOf(binding(SlotIds.SETUP_CONTENT)), phase = PluginPhase.DISABLED)
        val registry = registry(active, disabled)

        assertEquals(listOf("alpha"), registry.entriesOf("alpha").map { it.pluginId })
        assertTrue(registry.entriesOf("alpha-disabled").isEmpty())
    }

    @Test
    fun detailSectionsOfExposesSectionIds() {
        val registry = registry(
            descriptor(
                "alpha",
                listOf(
                    binding(SlotIds.detailSection("account")),
                    binding(SlotIds.detailSection("usage")),
                    binding(SlotIds.SETUP_CONTENT),
                ),
            ),
        )

        assertEquals(
            listOf("plugin.detail.section:account", "plugin.detail.section:usage"),
            registry.detailSectionsOf("alpha").map { it.slotId },
        )
    }

    @Test
    fun composeVersionMismatchDisablesBinding() {
        val registry = registry(
            descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)), ui = UiManifest(compose = "1.9.0")),
        )

        val entry = registry.bindings(SlotIds.SETUP_CONTENT).single()
        assertEquals(SlotDisabledReason.ComposeVersionMismatch("1.9.0", "1.10.5"), entry.disabledReason)
        assertEquals(false, entry.composeCompatible)
    }

    @Test
    fun matchingComposeVersionKeepsBinding() {
        val registry = registry(
            descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)), ui = UiManifest(compose = "v1.10.5")),
        )

        assertNull(registry.bindings(SlotIds.SETUP_CONTENT).single().disabledReason)
    }

    @Test
    fun unknownHostComposeVersionDisablesDeclaredBinding() {
        val source = PluginRegistry()
        source.register(descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)), ui = UiManifest(compose = "1.10.5")))
        val registry = SlotRegistry(
            { source.snapshot() },
            File(temporaryFolder.root, "plugins"),
            HostUiVersions(kotlin = "2.4.21", compose = null, composeCompatibilityId = 13000),
        )

        val entry = registry.bindings(SlotIds.SETUP_CONTENT).single()
        assertEquals(SlotDisabledReason.ComposeVersionUnavailable(13000), entry.disabledReason)
        assertEquals(false, entry.composeCompatible)
    }

    @Test
    fun kotlinMinorMismatchDisablesBinding() {
        val registry = registry(
            descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)), ui = UiManifest(kotlin = "2.3.0")),
        )

        assertEquals(
            SlotDisabledReason.KotlinVersionMismatch("2.3.0", "2.4.21"),
            registry.bindings(SlotIds.SETUP_CONTENT).single().disabledReason,
        )
    }

    @Test
    fun uiAndComposeApiLevelMismatchDisableBinding() {
        val uiLevel = registry(descriptor("alpha", listOf(binding(SlotIds.SETUP_CONTENT)), ui = UiManifest(apiLevel = 2)))
        assertEquals(
            SlotDisabledReason.UiApiLevelUnsupported(2, 1),
            uiLevel.bindings(SlotIds.SETUP_CONTENT).single().disabledReason,
        )

        val composeLevel = registry(
            descriptor(
                "beta",
                listOf(
                    SlotBinding(
                        slotId = SlotIds.SETUP_CONTENT,
                        content = SlotContent.Compose(uiApiLevel = 2) { },
                    ),
                ),
            ),
        )
        assertEquals(
            SlotDisabledReason.ComposeApiLevelUnsupported(2, 1),
            composeLevel.bindings(SlotIds.SETUP_CONTENT).single().disabledReason,
        )
    }

    @Test
    fun guardKeyIdentifiesSlotBinding() {
        assertEquals(
            "plugin.sample@1.2.3:tool.card.content:echo",
            slotGuardKey("plugin.sample", "1.2.3", "tool.card.content:echo"),
        )
    }
}
