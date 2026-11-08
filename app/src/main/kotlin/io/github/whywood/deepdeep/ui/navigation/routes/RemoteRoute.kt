package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.ui.remote.RemoteScreen

@Composable
internal fun RemoteRoute(onBack: () -> Unit) {
    RemoteScreen(onBack = onBack)
}
