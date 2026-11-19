package io.github.whywood.superpi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.whywood.superpi.data.HostFiles

internal val LocalHostFiles = staticCompositionLocalOf<HostFiles> { error("HostFiles is not provided") }

@Composable
internal fun ProvideHostFiles(hostFiles: HostFiles, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHostFiles provides hostFiles, content = content)
}
