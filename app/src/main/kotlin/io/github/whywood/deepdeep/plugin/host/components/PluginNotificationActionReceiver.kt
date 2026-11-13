package io.github.whywood.deepdeep.plugin.host.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class PluginNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID).orEmpty()
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID).orEmpty()
        if (notificationId.isBlank() || actionId.isBlank()) return
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
        val payload = intent.getStringExtra(EXTRA_PAYLOAD)?.let { raw ->
            runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        PluginNotificationEvents.dispatchAction(pluginId, notificationId, actionId, payload)
    }

    companion object {
        const val ACTION_PLUGIN_NOTIFICATION = "io.github.whywood.deepdeep.action.PLUGIN_NOTIFICATION"
        const val EXTRA_PLUGIN_ID = "io.github.whywood.deepdeep.extra.NOTIFICATION_PLUGIN"
        const val EXTRA_NOTIFICATION_ID = "io.github.whywood.deepdeep.extra.NOTIFICATION_ID"
        const val EXTRA_ACTION_ID = "io.github.whywood.deepdeep.extra.NOTIFICATION_ACTION"
        const val EXTRA_PAYLOAD = "io.github.whywood.deepdeep.extra.NOTIFICATION_PAYLOAD"
    }
}
