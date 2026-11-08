package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.remote.RemoteConnectScreen

@Composable
internal fun RemoteConnectRoute(
    computerId: String,
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    RemoteConnectScreen(
        computerId = computerId,
        connection = dependencies.remoteConnection,
        onBack = onBack,
        onRepair = { navigator.pop() },
    )
}
