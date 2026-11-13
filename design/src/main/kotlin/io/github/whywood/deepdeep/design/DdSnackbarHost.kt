package io.github.whywood.deepdeep.design

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class DdSnackbarHostState {
    internal val hostState = SnackbarHostState()

    val currentSnackbarData: SnackbarData?
        get() = hostState.currentSnackbarData

    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ): SnackbarResult = hostState.showSnackbar(message, actionLabel, withDismissAction, duration)

    fun dismiss() {
        hostState.currentSnackbarData?.dismiss()
    }
}

@Composable
fun DdSnackbarHost(
    state: DdSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    SnackbarHost(hostState = state.hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = DdShapes.Medium,
            containerColor = colors.raised,
            contentColor = colors.ink,
            actionColor = colors.accent,
            actionContentColor = colors.accent,
            dismissActionContentColor = colors.inkTertiary,
        )
    }
}
