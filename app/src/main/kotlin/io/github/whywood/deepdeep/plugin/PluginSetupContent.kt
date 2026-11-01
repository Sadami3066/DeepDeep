package io.github.whywood.deepdeep.plugin

import androidx.compose.runtime.Composable

class PluginSetupContent(
    val key: String,
    val render: @Composable () -> Unit,
)
