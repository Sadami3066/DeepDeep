package io.github.whywood.deepdeep.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {
    private val allDestinations = listOf(
        AppDestination.Chat,
        AppDestination.Settings,
        AppDestination.Providers,
        AppDestination.ProviderDetail("deepseek"),
        AppDestination.Archived,
        AppDestination.Mcp,
        AppDestination.RuntimeSettings,
        AppDestination.Personalization,
        AppDestination.Fonts,
        AppDestination.ChatSettings,
        AppDestination.Privacy,
        AppDestination.Permission,
        AppDestination.AndroidSettings,
        AppDestination.AgentSettings,
        AppDestination.WebSearch,
        AppDestination.Search,
        AppDestination.Skills,
        AppDestination.Timers,
        AppDestination.Plugins,
        AppDestination.Remote,
        AppDestination.RemoteConnect("demo-computer"),
        AppDestination.Miniapp,
        AppDestination.MiniappDetail("demo-miniapp"),
        AppDestination.PluginPage("demo-plugin", "home"),
        AppDestination.WorkspaceSettings("workspace-1"),
    )
    private val detailDestinations = allDestinations.filter {
        it is AppDestination.PluginPage ||
            it is AppDestination.MiniappDetail ||
            it is AppDestination.RemoteConnect
    }
    private val rootDestinations = allDestinations - detailDestinations.toSet()

    @Test
    fun allRoutesRoundTripThroughRouteKey() {
        assertEquals(25, allDestinations.size)
        allDestinations.forEach { assertEquals(it, AppDestination.fromRouteKey(it.routeKey)) }
    }

    @Test
    fun agentSettingsRouteUsesSettingsAgentKey() {
        assertEquals("settings/agent", AppDestination.AgentSettings.routeKey)
        assertEquals(AppDestination.AgentSettings, AppDestination.fromRouteKey("settings/agent"))
        assertEquals(listOf(AppDestination.AgentSettings), deepLinkStack(AppDestination.AgentSettings))
    }

    @Test
    fun routeSpecKeepsRouteKeyAndDepthForEveryRoute() {
        allDestinations.forEach { destination ->
            val spec = routeSpec(destination)
            assertEquals(destination.routeKey, spec.routeKey)
            assertEquals(destination.depth, spec.depth)
            assertEquals(spec, routeSpec(destination.routeKey))
        }
    }

    @Test
    fun detailRoutesDeclareParentRouteKey() {
        assertEquals("plugins", routeSpec(AppDestination.PluginPage("demo-plugin", "home")).parentRouteKey)
        assertEquals("miniapp", routeSpec(AppDestination.MiniappDetail("demo-miniapp")).parentRouteKey)
        assertEquals("remote", routeSpec(AppDestination.RemoteConnect("demo-computer")).parentRouteKey)
    }

    @Test
    fun rootRoutesDeclareNoParentRouteKey() {
        rootDestinations.forEach { assertNull(routeSpec(it).parentRouteKey) }
    }

    @Test
    fun pluginPageDeepLinkAddsPluginsParent() {
        assertEquals(
            listOf(AppDestination.Plugins, AppDestination.PluginPage("demo-plugin", "home")),
            deepLinkStack(AppDestination.PluginPage("demo-plugin", "home")),
        )
    }

    @Test
    fun miniappDetailDeepLinkAddsMiniappParent() {
        assertEquals(
            listOf(AppDestination.Miniapp, AppDestination.MiniappDetail("demo-miniapp")),
            deepLinkStack(AppDestination.MiniappDetail("demo-miniapp")),
        )
    }

    @Test
    fun remoteConnectDeepLinkAddsRemoteParent() {
        assertEquals(
            listOf(AppDestination.Remote, AppDestination.RemoteConnect("demo-computer")),
            deepLinkStack(AppDestination.RemoteConnect("demo-computer")),
        )
    }

    @Test
    fun rootRoutesDeepLinkToSingleEntry() {
        rootDestinations.forEach { assertEquals(listOf(it), deepLinkStack(it)) }
    }

    @Test
    fun unknownRouteKeyHasNoSpecAndEmptyDeepLinkStack() {
        assertNull(routeSpec("bogus/route"))
        assertTrue(deepLinkStack("bogus/route").isEmpty())
        assertNotNull(routeSpec(AppDestination.Chat.routeKey))
    }
}
