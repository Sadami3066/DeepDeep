package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.AppLifecycleEvent
import io.github.whywood.deepdeep.plugin.api.BackgroundTaskEvent
import io.github.whywood.deepdeep.plugin.api.DeepLinkReceivedEvent
import io.github.whywood.deepdeep.plugin.api.NotificationActionEvent
import io.github.whywood.deepdeep.plugin.api.NotificationReceivedEvent
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginNotificationRecord
import io.github.whywood.deepdeep.plugin.api.ShareReceivedEvent
import io.github.whywood.deepdeep.plugin.api.SharedStream
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.events.PluginHookFailure
import io.github.whywood.deepdeep.plugin.host.events.PluginHookReporter
import io.github.whywood.deepdeep.plugin.host.events.PluginHookStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

data class PluginSharedStream(
    val path: String,
    val mimeType: String? = null,
    val name: String? = null,
)

internal class PluginSystemEvents(
    private val scope: CoroutineScope,
    private val snapshot: () -> PluginRegistrySnapshot,
) {
    private val queue = Channel<suspend () -> Unit>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val started = AtomicBoolean(false)

    fun notificationReceived(record: PluginNotificationRecord, removed: Boolean) {
        dispatch(
            capability = PluginCapability.NOTIFICATIONS,
            requireComponent = { it.notificationListener },
        ) { hooks ->
            hooks.notificationReceived(HostNotificationReceivedEvent(record, removed))
        }
    }

    fun notificationAction(pluginId: String, notificationId: String, actionId: String, payload: JsonObject) {
        val target = singleTarget(pluginId, PluginCapability.NOTIFICATIONS) ?: return
        enqueueTo(listOf(target)) { hooks ->
            hooks.notificationAction(HostNotificationActionEvent(notificationId, actionId, payload))
        }
    }

    fun backgroundTask(pluginId: String, taskId: String, kind: PluginBackgroundKind) {
        val target = singleTarget(pluginId, PluginCapability.BACKGROUND)?.takeIf { (_, hooks) ->
            val manifest = snapshot().plugin(pluginId)?.manifest
            manifest?.components?.let { it.background || it.foregroundService } == true
        } ?: return
        enqueueTo(listOf(target)) { hooks ->
            hooks.backgroundTask(HostBackgroundTaskEvent(taskId, kind, System.currentTimeMillis()))
        }
    }

    fun shareReceived(text: String?, title: String?, streams: List<PluginSharedStream>) {
        dispatch(
            capability = PluginCapability.SHARE,
            requireComponent = { it.share },
        ) { hooks ->
            hooks.shareReceived(HostShareReceivedEvent(text, title, streams.map(::HostSharedStream)))
        }
    }

    fun deepLinkReceived(pluginId: String, path: String, arguments: JsonObject) {
        val current = snapshot()
        val manifest = current.plugin(pluginId)?.manifest ?: return
        if (PluginCapability.DEEPLINK !in manifest.capabilities) return
        val components = manifest.components ?: return
        if (components.deeplinks.none { declared -> matchesDeepLink(declared, path) }) return
        val target = singleTarget(pluginId, PluginCapability.DEEPLINK) ?: return
        enqueueTo(listOf(target)) { hooks ->
            hooks.deepLinkReceived(HostDeepLinkReceivedEvent(path, arguments))
        }
    }

    fun appLifecycle(foreground: Boolean) {
        dispatch(capability = PluginCapability.APP) { hooks ->
            hooks.appLifecycle(HostAppLifecycleEvent(foreground))
        }
    }

    private fun singleTarget(pluginId: String, capability: PluginCapability): Pair<String, PluginHooks>? {
        val current = snapshot()
        val manifest = current.plugin(pluginId)?.manifest ?: return null
        if (capability !in manifest.capabilities) return null
        val hooks = current.hooks.firstOrNull { it.pluginId == pluginId }?.hooks ?: return null
        return pluginId to hooks
    }

    private fun dispatch(
        capability: PluginCapability,
        requireComponent: ((io.github.whywood.deepdeep.plugin.api.PluginComponentDeclaration) -> Boolean)? = null,
        block: suspend (PluginHooks) -> Unit,
    ) {
        val current = snapshot()
        val targets = current.hooks.mapNotNull { entry ->
            val manifest = current.plugin(entry.pluginId)?.manifest ?: return@mapNotNull null
            if (capability !in manifest.capabilities) return@mapNotNull null
            if (requireComponent != null) {
                val components = manifest.components ?: return@mapNotNull null
                if (!requireComponent(components)) return@mapNotNull null
            }
            entry.pluginId to entry.hooks
        }
        enqueueTo(targets, block)
    }

    private fun enqueueTo(targets: List<Pair<String, PluginHooks>>, block: suspend (PluginHooks) -> Unit) {
        if (targets.isEmpty()) return
        start()
        queue.trySend {
            targets.forEach { (pluginId, hooks) ->
                runCatching {
                    withTimeout(PluginHooks.OBSERVE_TIMEOUT_MS) { block(hooks) }
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    PluginHookReporter.Logging.report(
                        PluginHookFailure(
                            pluginId = pluginId,
                            stage = PluginHookStage.OBSERVE,
                            message = "system event hook failed: ${error.message}",
                            cause = error,
                        ),
                    )
                }
            }
        }
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (task in queue) {
                runCatching { task() }
            }
        }
    }

    private fun matchesDeepLink(declared: String, path: String): Boolean =
        path == declared || path.startsWith("$declared/")

    private class HostNotificationReceivedEvent(
        override val notification: PluginNotificationRecord,
        override val removed: Boolean,
    ) : NotificationReceivedEvent

    private class HostNotificationActionEvent(
        override val notificationId: String,
        override val actionId: String,
        override val payload: JsonObject,
    ) : NotificationActionEvent

    private class HostBackgroundTaskEvent(
        override val taskId: String,
        override val kind: PluginBackgroundKind,
        override val firedAtMs: Long,
    ) : BackgroundTaskEvent

    private class HostSharedStream(
        stream: PluginSharedStream,
    ) : SharedStream {
        override val path: String = stream.path
        override val mimeType: String? = stream.mimeType
        override val name: String? = stream.name
    }

    private class HostShareReceivedEvent(
        override val text: String?,
        override val title: String?,
        override val streams: List<SharedStream>,
    ) : ShareReceivedEvent

    private class HostDeepLinkReceivedEvent(
        override val path: String,
        override val arguments: JsonObject,
    ) : DeepLinkReceivedEvent

    private class HostAppLifecycleEvent(
        override val foreground: Boolean,
    ) : AppLifecycleEvent
}
