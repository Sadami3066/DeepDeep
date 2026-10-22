package io.github.whywood.deepdeep

import android.app.Application

class DeepDeepApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
