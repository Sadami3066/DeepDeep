package io.github.whywood.deepdeep.graph

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal class DataGraphDeps(
    val appContext: Context,
    val uiContext: Context,
    val scope: CoroutineScope,
)
