package io.github.whywood.deepdeep.adb

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AdbBridgeRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var bootstrap: AdbBootstrap? = null

    val scope: CoroutineScope
        get() = runtimeScope

    fun install(context: Context): AdbBootstrap = obtain(context.applicationContext)

    fun get(context: Context): AdbBootstrap = obtain(context.applicationContext)

    private fun obtain(context: Context): AdbBootstrap {
        bootstrap?.let { return it }
        return synchronized(this) {
            bootstrap ?: AdbBootstrap(context).also { created ->
                bootstrap = created
                runtimeScope.launch { created.refreshState() }
            }
        }
    }
}
