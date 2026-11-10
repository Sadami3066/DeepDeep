package io.github.whywood.deepdeep.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.whywood.deepdeep.data.HostFiles

internal val LocalHostFiles = staticCompositionLocalOf<HostFiles> { error("HostFiles is not provided") }

@Composable
internal fun ProvideHostFiles(hostFiles: HostFiles, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHostFiles provides hostFiles, content = content)
}
