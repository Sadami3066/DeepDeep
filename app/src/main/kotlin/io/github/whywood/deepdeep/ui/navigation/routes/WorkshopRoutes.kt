package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.workshop.WorkshopScreen
import io.github.whywood.deepdeep.ui.workshop.WorkshopViewerScreen

@Composable
internal fun WorkshopRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onOpenWorkshopCreate: () -> Unit,
    onBack: () -> Unit,
) {
    WorkshopScreen(
        store = dependencies.workshopStore,
        onBack = onBack,
        onOpen = { id -> navigator.push(AppDestination.WorkshopDetail(id)) },
        onCreate = onOpenWorkshopCreate,
    )
}

@Composable
internal fun WorkshopDetailRoute(
    dependencies: AppDependencies,
    creationId: String,
    onBack: () -> Unit,
) {
    WorkshopViewerScreen(
        store = dependencies.workshopStore,
        creationId = creationId,
        agentGateway = dependencies.workshopAgentService,
        onBack = onBack,
    )
}
