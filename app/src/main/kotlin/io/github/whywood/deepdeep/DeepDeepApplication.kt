package io.github.whywood.deepdeep

import android.app.Application
import io.github.whywood.deepdeep.design.DdFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val fontWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class DeepDeepApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        fontWarmupScope.launch { DdFonts.preload(this@DeepDeepApplication) }
    }
}
