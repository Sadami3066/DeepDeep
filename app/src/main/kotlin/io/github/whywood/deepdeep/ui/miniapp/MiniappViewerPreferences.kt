package io.github.whywood.deepdeep.ui.miniapp

import android.content.Context
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject

internal class MiniappViewerPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun fullscreen(appId: String): Boolean = preferences.getBoolean("fullscreen_$appId", false)

    fun setFullscreen(appId: String, enabled: Boolean) {
        preferences.edit().putBoolean("fullscreen_$appId", enabled).apply()
    }

    private companion object {
        const val NAME = "miniapp_viewer"
    }
}

internal object MiniappLaunchEvents {
    private val pending = mutableMapOf<String, JsonObject>()
    private val mutableEvents = MutableSharedFlow<Pair<String, JsonObject>>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events = mutableEvents.asSharedFlow()

    @Synchronized
    fun record(appId: String, payload: JsonObject) {
        pending[appId] = payload
        mutableEvents.tryEmit(appId to payload)
    }

    @Synchronized
    fun consume(appId: String): JsonObject? = pending.remove(appId)

    @Synchronized
    fun clear(appId: String) {
        pending.remove(appId)
    }
}
