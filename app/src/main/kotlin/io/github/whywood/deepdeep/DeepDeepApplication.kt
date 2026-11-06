package io.github.whywood.deepdeep

import android.app.Application
import io.github.whywood.deepdeep.design.DdFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

private val fontWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class DeepDeepApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        graph = AppGraph(this)
        graph.processRuntime.startAsync()
        fontWarmupScope.launch { DdFonts.preload(this@DeepDeepApplication) }
    }
}
