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
        AppDestination.Search,
        AppDestination.Skills,
        AppDestination.Schedules,
        AppDestination.Plugins,
        AppDestination.Workshop,
        AppDestination.WorkshopDetail("demo-workshop"),
        AppDestination.PluginDetail("demo-plugin"),
        AppDestination.WorkspaceSettings("workspace-1"),
    )
    private val detailDestinations = allDestinations.filter {
        it is AppDestination.PluginDetail || it is AppDestination.WorkshopDetail
    }
    private val rootDestinations = allDestinations - detailDestinations.toSet()

    @Test
    fun allRoutesRoundTripThroughRouteKey() {
        assertEquals(20, allDestinations.size)
        allDestinations.forEach { assertEquals(it, AppDestination.fromRouteKey(it.routeKey)) }
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
    fun onlyPluginAndWorkshopDetailDeclareParentRouteKey() {
        assertEquals("plugins", routeSpec(AppDestination.PluginDetail("demo-plugin")).parentRouteKey)
        assertEquals("workshop", routeSpec(AppDestination.WorkshopDetail("demo-workshop")).parentRouteKey)
    }

    @Test
    fun rootRoutesDeclareNoParentRouteKey() {
        rootDestinations.forEach { assertNull(routeSpec(it).parentRouteKey) }
    }

    @Test
    fun pluginDetailDeepLinkAddsPluginsParent() {
        assertEquals(
            listOf(AppDestination.Plugins, AppDestination.PluginDetail("demo-plugin")),
            deepLinkStack(AppDestination.PluginDetail("demo-plugin")),
        )
    }

    @Test
    fun workshopDetailDeepLinkAddsWorkshopParent() {
        assertEquals(
            listOf(AppDestination.Workshop, AppDestination.WorkshopDetail("demo-workshop")),
            deepLinkStack(AppDestination.WorkshopDetail("demo-workshop")),
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
