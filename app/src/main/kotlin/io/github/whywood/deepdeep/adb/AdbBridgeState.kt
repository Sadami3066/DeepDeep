package io.github.whywood.deepdeep.adb

enum class AdbBridgeError {
    WIRELESS_DEBUGGING_DISABLED,
    NOTIFICATION_PERMISSION_REQUIRED,
    NOTIFICATION_UNAVAILABLE,
    PAIRING_CODE_INVALID,
    NO_PAIRING_SERVICE,
    PAIRING_FAILED,
    NOT_PAIRED,
    CONNECT_FAILED,
    DAEMON_START_FAILED,
}

class AdbBridgeException(
    val error: AdbBridgeError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

data class AdbBridgeState(
    val paired: Boolean = false,
    val daemon: Boolean = false,
    val busy: Boolean = false,
    val error: AdbBridgeError? = null,
) {
    fun working(): AdbBridgeState = copy(busy = true, error = null)

    fun failed(error: AdbBridgeError): AdbBridgeState = copy(busy = false, error = error)

    fun pairingSucceeded(): AdbBridgeState = copy(paired = true, busy = true, error = null)

    fun daemonStarted(): AdbBridgeState = copy(paired = true, daemon = true, busy = false, error = null)

    fun trustRevoked(): AdbBridgeState =
        copy(paired = false, daemon = false, busy = false, error = AdbBridgeError.NOT_PAIRED)

    fun refreshed(daemonAlive: Boolean): AdbBridgeState =
        copy(paired = paired || daemonAlive, daemon = daemonAlive)
}
