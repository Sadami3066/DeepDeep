package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.settings.AgentSettingsScreen
import kotlinx.coroutines.launch

@Composable
internal fun AgentSettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val privilegeState by dependencies.privilege.state.collectAsStateWithLifecycle()
    val screenOps by dependencies.capabilitySettingsStore.screenOps.collectAsStateWithLifecycle(
        initialValue = false,
    )
    AgentSettingsScreen(
        privilege = privilegeState.effective,
        screenOps = screenOps,
        onScreenOpsChange = { enabled ->
            scope.launch { dependencies.capabilitySettingsStore.setScreenOps(enabled) }
        },
        onOpenPermission = { navigator.push(AppDestination.Permission) },
        onBack = onBack,
    )
}
