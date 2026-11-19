package io.github.whywood.superpi

import android.app.Application
import io.github.whywood.superpi.data.crash.CrashReporter
import io.github.whywood.superpi.design.DdFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

private val fontWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SuperPIApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        graph = AppGraph(this)
        graph.processRuntime.startAsync()
        fontWarmupScope.launch { DdFonts.preload(this@SuperPIApplication) }
    }
}
