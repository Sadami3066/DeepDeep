package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.settings.AgentSettingsScreen

@Composable
internal fun AgentSettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    val privilegeState by dependencies.privilege.state.collectAsStateWithLifecycle()
    val screenOps by dependencies.capabilitySettingsStore.screenOps.collectAsStateWithLifecycle(
        initialValue = false,
    )
    AgentSettingsScreen(
        effective = privilegeState.effective,
        screenOps = screenOps,
        onOpenPermission = { navigator.push(AppDestination.Permission) },
        onOpenMcp = { navigator.push(AppDestination.Mcp) },
        onBack = onBack,
    )
}
