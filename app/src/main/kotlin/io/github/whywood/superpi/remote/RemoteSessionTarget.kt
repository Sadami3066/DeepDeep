package io.github.whywood.superpi.remote

data class RemoteSessionTarget(
    val deviceId: String,
    val deviceName: String,
    val botId: String,
    val botName: String,
    val isOnline: () -> Boolean,
)
