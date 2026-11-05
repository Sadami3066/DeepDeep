package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.ComposerAction
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.UiNode
import io.github.whywood.deepdeep.plugin.api.UiNodeTypes
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistry
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.TestPlugin
import io.github.whywood.deepdeep.plugin.host.testManifest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginUiCatalogTest {
    private val pluginsRoot = File("/tmp/deepdeep-plugins")

    private fun page(id: String, title: String = id): PluginPage = PluginPage(
        id = id,
        title = LocalizedText.Literal(title),
        icon = IconRef.Asset("icons/$id.png"),
    )

    private fun action(id: String, label: String = id): ComposerAction = ComposerAction(
        id = id,
        label = LocalizedText.Literal(label),
    )

    private fun binding(slotId: String): SlotBinding = SlotBinding(
        slotId = slotId,
        priority = 0,
        content = SlotContent.Declarative(UiNode(UiNodeTypes.TEXT, text = "bound")),
    )

    private fun ui(
        pages: List<PluginPage> = emptyList(),
        actions: List<ComposerAction> = emptyList(),
        bindings: List<SlotBinding> = emptyList(),
    ): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = bindings
        override val pages: List<PluginPage> = pages
        override val composerActions: List<ComposerAction> = actions
    }

    private fun descriptor(
        id: String,
        phase: PluginPhase,
        contributions: PluginContributions = PluginContributions.Empty,
    ): PluginDescriptor = PluginDescriptor(
        manifest = testManifest(id),
        plugin = TestPlugin(),
        phase = phase,
        contributions = contributions,
    )

    private fun snapshot(vararg plugins: PluginDescriptor): PluginRegistrySnapshot =
        PluginRegistrySnapshot(version = 1, plugins = plugins.toList())

    @Test
    fun groupsContributionsPerPlugin() {
        val active = descriptor(
            id = "io.example.active",
            phase = PluginPhase.ACTIVE,
            contributions = PluginContributions(
                ui = ui(
                    pages = listOf(page("home"), page("settings")),
                    actions = listOf(action("sync")),
                    bindings = listOf(binding("plugin.page:home")),
                ),
            ),
        )
        val disabled = descriptor(
            id = "io.example.disabled",
            phase = PluginPhase.DISABLED,
            contributions = PluginContributions(ui = ui(pages = listOf(page("home")))),
        )
        val catalog = PluginUiCatalog.from(snapshot(active, disabled), pluginsRoot)

        assertEquals(2, catalog.plugins.size)
        val activeEntry = catalog.plugin("io.example.active")!!
        assertTrue(activeEntry.available)
        assertEquals(2, activeEntry.pages.size)
        assertEquals(1, activeEntry.composerActions.size)
        assertEquals(1, activeEntry.bindings.size)
        assertEquals(
            File(pluginsRoot, "io.example.active/1.0.0/assets"),
            activeEntry.assetsDir,
        )
        assertEquals(
            File(pluginsRoot, "io.example.active/1.0.0/assets"),
            activeEntry.pages.first().assetsDir,
        )

        val disabledEntry = catalog.plugin("io.example.disabled")!!
        assertFalse(disabledEntry.available)
        assertFalse(disabledEntry.pages.single().available)
    }

    @Test
    fun omitsPluginsWithoutUiContributions() {
        val plain = descriptor(id = "io.example.plain", phase = PluginPhase.ACTIVE)
        assertTrue(PluginUiCatalog.from(snapshot(plain), pluginsRoot).plugins.isEmpty())
        assertTrue(PluginUiCatalog.empty().plugins.isEmpty())
    }

    @Test
    fun filtersUnavailablePagesAndActionsByDefault() {
        val disabled = descriptor(
            id = "io.example.disabled",
            phase = PluginPhase.FAILED,
            contributions = PluginContributions(
                ui = ui(
                    pages = listOf(page("home")),
                    actions = listOf(action("retry")),
                ),
            ),
        )
        val catalog = PluginUiCatalog.from(snapshot(disabled), pluginsRoot)

        assertTrue(catalog.pagesOf("io.example.disabled").isEmpty())
        assertEquals(1, catalog.pagesOf("io.example.disabled", includeUnavailable = true).size)
        assertTrue(catalog.composerActionsFor().isEmpty())
        assertEquals(1, catalog.composerActionsFor(includeUnavailable = true).size)
    }

    @Test
    fun resolvesPageByPluginAndPageId() {
        val plugin = descriptor(
            id = "io.example.pages",
            phase = PluginPhase.ACTIVE,
            contributions = PluginContributions(ui = ui(pages = listOf(page("home", "Home")))),
        )
        val catalog = PluginUiCatalog.from(snapshot(plugin), pluginsRoot)

        val resolved = catalog.page("io.example.pages", "home")
        assertEquals("home", resolved?.page?.id)
        assertEquals("Home", (resolved?.page?.title as LocalizedText.Literal).resolve())
        assertNull(catalog.page("io.example.pages", "missing"))
        assertNull(catalog.page("io.example.other", "home"))
    }

    @Test
    fun pageRefExposesSlotIdAndState() {
        val plugin = descriptor(
            id = "io.example.pages",
            phase = PluginPhase.ACTIVE,
            contributions = PluginContributions(ui = ui(pages = listOf(page("history")))),
        )
        val ref = PluginUiCatalog.from(snapshot(plugin), pluginsRoot).pages.single()

        assertEquals("plugin.page:history", ref.slotId)
        assertEquals("plugin.page:", PluginPageSlots.PREFIX)
        val state = ref.state(sessionId = "session-1")
        assertEquals("io.example.pages", state.pluginId)
        assertEquals("history", state.pageId)
        assertEquals("session-1", state.sessionId)
        assertNull(ref.state().sessionId)
    }

    @Test
    fun indexesBindingsBySlotId() {
        val plugin = descriptor(
            id = "io.example.bindings",
            phase = PluginPhase.ACTIVE,
            contributions = PluginContributions(
                ui = ui(
                    bindings = listOf(
                        binding("chat.message:card"),
                        binding("tool.card.content:echo"),
                    ),
                ),
            ),
        )
        val catalog = PluginUiCatalog.from(snapshot(plugin), pluginsRoot)

        assertEquals(1, catalog.bindingsFor("chat.message:card").size)
        assertEquals("io.example.bindings", catalog.bindingsFor("chat.message:card").single().pluginId)
        assertEquals(
            setOf("chat.message:card", "tool.card.content:echo"),
            catalog.slotIds(),
        )
        assertTrue(catalog.bindingsFor("setup.content").isEmpty())
    }

    @Test
    fun resolversReadThroughProvider() {
        val plugin = descriptor(
            id = "io.example.provider",
            phase = PluginPhase.ACTIVE,
            contributions = PluginContributions(
                ui = ui(
                    pages = listOf(page("home")),
                    actions = listOf(action("sync")),
                ),
            ),
        )
        val provider = PluginUiCatalogProvider { PluginUiCatalog.from(snapshot(plugin), pluginsRoot) }
        val pages = PluginPageResolver(provider)
        val actions = PluginComposerActionResolver(provider)

        assertEquals(1, pages.pages("io.example.provider").size)
        assertEquals(1, pages.allPages().size)
        assertEquals("home", pages.resolve("io.example.provider", "home")?.page?.id)
        assertNull(pages.resolve("io.example.provider", "missing"))
        assertEquals(1, actions.actions().size)
        assertEquals("sync", actions.actionsOf("io.example.provider").single().action.id)
    }

    @Test
    fun registryProviderPicksUpPhaseChanges() {
        val registry = PluginRegistry()
        val descriptor = descriptor(
            id = "io.example.registry",
            phase = PluginPhase.DISABLED,
            contributions = PluginContributions(ui = ui(pages = listOf(page("home")))),
        )
        assertEquals(true, registry.register(descriptor) is io.github.whywood.deepdeep.plugin.host.PluginRegistration.Registered)
        val provider = pluginUiCatalogProvider(registry, pluginsRoot)
        val resolver = PluginPageResolver(provider)

        assertTrue(resolver.pages("io.example.registry").isEmpty())
        assertEquals(1, resolver.pages("io.example.registry", includeUnavailable = true).size)

        registry.updatePhase("io.example.registry", PluginPhase.ACTIVE)
        assertEquals(1, resolver.pages("io.example.registry").size)
    }
}
