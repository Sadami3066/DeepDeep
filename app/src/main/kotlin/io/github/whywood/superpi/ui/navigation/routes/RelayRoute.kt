package io.github.whywood.superpi.ui.navigation.routes

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.superpi.core.AppDependencies
import io.github.whywood.superpi.ui.relay.RelayScreen

@Composable
internal fun RelayRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
) {
    val state by dependencies.relayController.state.collectAsStateWithLifecycle()
    val model = Build.MODEL.orEmpty().trim()
    RelayScreen(
        state = state,
        defaultDeviceName = remember(model) { model.ifEmpty { "Android" } },
        onBack = onBack,
        onConnect = dependencies.relayController::signIn,
        onSignOut = dependencies.relayController::signOut,
        onDismissError = dependencies.relayController::dismissError,
    )
}
