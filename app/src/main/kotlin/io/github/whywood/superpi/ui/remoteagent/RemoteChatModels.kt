package io.github.whywood.superpi.ui.remoteagent

import io.github.whywood.superpi.data.relay.RemoteCallCodes
import io.github.whywood.superpi.data.relay.RemoteCallError

internal sealed interface RemoteChatBlock {
    val key: Long

    data class User(override val key: Long, val text: String) : RemoteChatBlock

    data class Assistant(override val key: Long, val text: String, val reasoning: String) : RemoteChatBlock

    data class Tool(
        override val key: Long,
        val callId: String,
        val name: String,
        val arguments: String,
        val result: String?,
        val isError: Boolean,
    ) : RemoteChatBlock

    data class Failure(override val key: Long, val failure: RemoteChatFailure) : RemoteChatBlock
}

internal sealed interface RemoteChatFailure {
    data object NotConnected : RemoteChatFailure

    data object Disconnected : RemoteChatFailure

    data object Timeout : RemoteChatFailure

    data object DeviceOffline : RemoteChatFailure

    data object BotUnavailable : RemoteChatFailure

    data object Rejected : RemoteChatFailure

    data class Remote(val message: String) : RemoteChatFailure

    companion object {
        fun from(error: RemoteCallError): RemoteChatFailure = when (error.code) {
            RemoteCallCodes.NOT_CONNECTED -> NotConnected
            RemoteCallCodes.DISCONNECTED -> Disconnected
            RemoteCallCodes.TIMEOUT -> Timeout
            "device_offline" -> DeviceOffline
            "bot_not_found" -> BotUnavailable
            else -> Rejected
        }
    }
}
