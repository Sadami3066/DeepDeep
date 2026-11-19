package io.github.whywood.superpi.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.superpi.core.AppDependencies
import io.github.whywood.superpi.ui.navigation.AppNavigator
import io.github.whywood.superpi.ui.remote.RemoteConnectScreen

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
