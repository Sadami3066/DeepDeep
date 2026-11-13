package io.github.whywood.deepdeep.plugin.host.extensions.device

internal const val DEVICE_NETWORK_NONE = "none"

internal data class DeviceLiveSnapshot(
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val networkType: String = DEVICE_NETWORK_NONE,
    val connected: Boolean = false,
    val screenOn: Boolean = true,
    val locked: Boolean = false,
)

internal sealed interface DeviceLiveEvent {
    data class Battery(val level: Int, val scale: Int, val charging: Boolean) : DeviceLiveEvent

    data class PowerSave(val enabled: Boolean) : DeviceLiveEvent

    data class Network(val type: String, val connected: Boolean) : DeviceLiveEvent

    data class ScreenOn(val on: Boolean) : DeviceLiveEvent

    data class Locked(val locked: Boolean) : DeviceLiveEvent
}

internal fun reduceDeviceLive(
    previous: DeviceLiveSnapshot,
    event: DeviceLiveEvent,
): DeviceLiveSnapshot = when (event) {
    is DeviceLiveEvent.Battery -> previous.copy(
        batteryPercent = batteryPercentOf(event.level, event.scale) ?: previous.batteryPercent,
        charging = event.charging,
    )

    is DeviceLiveEvent.PowerSave -> previous.copy(powerSave = event.enabled)

    is DeviceLiveEvent.Network -> previous.copy(
        networkType = event.type.takeIf { it.isNotBlank() } ?: DEVICE_NETWORK_NONE,
        connected = event.connected,
    )

    is DeviceLiveEvent.ScreenOn -> previous.copy(
        screenOn = event.on,
        locked = if (event.on) previous.locked else true,
    )

    is DeviceLiveEvent.Locked -> previous.copy(locked = event.locked)
}

internal fun batteryPercentOf(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level.toLong() * 100L) / scale.toLong()).toInt().coerceIn(0, 100)
}
