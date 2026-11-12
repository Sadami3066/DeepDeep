package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.purge.PurgeScreen

@Composable
internal fun PurgeRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
) {
    PurgeScreen(controller = dependencies.purge, onBack = onBack)
}
