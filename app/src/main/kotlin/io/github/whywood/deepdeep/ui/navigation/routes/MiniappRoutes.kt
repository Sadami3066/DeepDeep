package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.miniapp.MiniappScreen
import io.github.whywood.deepdeep.ui.miniapp.MiniappViewerScreen

@Composable
internal fun MiniappRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onOpenMiniappCreate: () -> Unit,
    onBack: () -> Unit,
) {
    MiniappScreen(
        store = dependencies.miniappStore,
        onBack = onBack,
        onOpen = { id -> navigator.push(AppDestination.MiniappDetail(id)) },
        onCreate = onOpenMiniappCreate,
    )
}

@Composable
internal fun MiniappDetailRoute(
    dependencies: AppDependencies,
    creationId: String,
    onBack: () -> Unit,
) {
    MiniappViewerScreen(
        store = dependencies.miniappStore,
        creationId = creationId,
        agentGateway = dependencies.miniappAgentService,
        onBack = onBack,
    )
}
