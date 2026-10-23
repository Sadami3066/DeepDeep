package io.github.whywood.deepdeep.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun pushAndPopMaintainStackOrder() {
        val navigator = AppNavigator()
        assertTrue(navigator.current == AppDestination.Chat)
        assertFalse(navigator.canPop)
        navigator.push(AppDestination.Settings)
        navigator.push(AppDestination.Providers)
        navigator.push(AppDestination.ProviderDetail("deepseek"))
        assertEquals(3, navigator.current.depth)
        assertTrue(navigator.canPop)
        assertTrue(navigator.pop())
        assertTrue(navigator.current == AppDestination.Providers)
        navigator.pop()
        assertTrue(navigator.current == AppDestination.Settings)
        navigator.pop()
        assertTrue(navigator.current == AppDestination.Chat)
        assertFalse(navigator.pop())
    }

    @Test
    fun resetReplacesStack() {
        val navigator = AppNavigator()
        navigator.push(AppDestination.Settings)
        navigator.push(AppDestination.Providers)
        navigator.reset(AppDestination.Chat)
        assertEquals(1, navigator.stack.size)
        assertTrue(navigator.current == AppDestination.Chat)
    }

    @Test
    fun directionFollowsDepth() {
        assertEquals(1, AppScreenTransition.direction(AppDestination.Chat, AppDestination.Settings))
        assertEquals(-1, AppScreenTransition.direction(AppDestination.Settings, AppDestination.Chat))
        assertEquals(1, AppScreenTransition.direction(AppDestination.Settings, AppDestination.RuntimeSettings))
        assertEquals(0, AppScreenTransition.direction(AppDestination.Chat, AppDestination.Chat))
    }

    @Test
    fun routeKeysRoundTrip() {
        val destinations = listOf(
            AppDestination.Chat,
            AppDestination.Settings,
            AppDestination.Providers,
            AppDestination.ProviderDetail("openai"),
            AppDestination.Archived,
            AppDestination.RuntimeSettings,
            AppDestination.Skills,
            AppDestination.WorkspaceSettings("workspace-1"),
        )
        destinations.forEach { destination ->
            assertEquals(destination, AppDestination.fromRouteKey(destination.routeKey))
        }
        assertEquals(null, AppDestination.fromRouteKey("workspaces//settings"))
    }
}
