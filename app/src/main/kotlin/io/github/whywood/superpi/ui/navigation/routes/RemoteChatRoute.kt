package io.github.whywood.superpi.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.whywood.superpi.core.AppDependencies
import io.github.whywood.superpi.ui.navigation.AppDestination
import io.github.whywood.superpi.ui.navigation.AppNavigator
import io.github.whywood.superpi.ui.remoteagent.RemoteChatScreen
import io.github.whywood.superpi.ui.remoteagent.RemoteChatStateHolder
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun RemoteChatRoute(
    deviceId: String,
    botId: String,
    dependencies: AppDependencies,
    navigator: AppNavigator,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val screenScope = rememberCoroutineScope()
    val holder = remember(
        dependencies.relayLive,
        dependencies.relayMirror,
        deviceId,
        botId,
        screenScope,
        scope,
    ) {
        RemoteChatStateHolder(
            relayLive = dependencies.relayLive,
            mirror = dependencies.relayMirror,
            deviceId = deviceId,
            botId = botId,
            scope = screenScope,
            mirrorScope = scope,
        )
    }
    DisposableEffect(holder) {
        onDispose { holder.close() }
    }
    RemoteChatScreen(
        deviceName = holder.deviceName,
        botName = holder.botName,
        deviceOnline = holder.deviceOnline,
        relayState = holder.relayState,
        blocks = holder.blocks,
        running = holder.running,
        settled = holder.settled,
        canAbort = holder.canAbort,
        sendFailure = holder.sendFailure,
        onBack = onBack,
        onSend = holder::send,
        onRetry = holder::retry,
        onAbort = holder::abort,
        onOpenRelay = { navigator.push(AppDestination.Relay) },
    )
}
