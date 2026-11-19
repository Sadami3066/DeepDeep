package io.github.whywood.superpi.graph

import android.content.Context
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal class DataGraphDeps(
    val appContext: Context,
    val uiContext: Context,
    val scope: CoroutineScope,
    val hostEnvironmentProvider: () -> HostEnvironmentProvider,
    val stagingRoot: File,
)
