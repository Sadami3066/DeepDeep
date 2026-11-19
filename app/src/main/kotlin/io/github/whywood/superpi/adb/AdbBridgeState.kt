package io.github.whywood.superpi.adb

enum class AdbConnectMode {
    WIRELESS,
    LOCAL,
}

enum class AdbBridgeError {
    WIRELESS_DEBUGGING_DISABLED,
    PAIRING_CODE_INVALID,
    NO_PAIRING_SERVICE,
    PAIRING_FAILED,
    NOT_PAIRED,
    CONNECT_FAILED,
    DAEMON_START_FAILED,
    LOCAL_ENDPOINT_INVALID,
    LOCAL_CONNECT_FAILED,
    LOCAL_AUTH_REQUIRED,
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
    val mode: AdbConnectMode = AdbConnectMode.WIRELESS,
    val localHost: String = AdbLocalEndpoint.DEFAULT_HOST,
    val localPort: Int = AdbLocalEndpoint.DEFAULT_PORT,
) {
    fun working(): AdbBridgeState = copy(busy = true, error = null)

    fun failed(error: AdbBridgeError): AdbBridgeState = copy(busy = false, error = error)

    fun pairingSucceeded(): AdbBridgeState = copy(paired = true, busy = true, error = null)

    fun daemonStarted(): AdbBridgeState = copy(daemon = true, busy = false, error = null)

    fun trustRevoked(): AdbBridgeState =
        copy(paired = false, daemon = false, busy = false, error = AdbBridgeError.NOT_PAIRED)

    fun refreshed(daemonAlive: Boolean): AdbBridgeState = copy(
        paired = if (mode == AdbConnectMode.WIRELESS) paired || daemonAlive else paired,
        daemon = daemonAlive,
    )

    fun withMode(value: AdbConnectMode): AdbBridgeState =
        copy(mode = value, busy = false, error = null)

    fun withLocalEndpoint(host: String, port: Int): AdbBridgeState =
        copy(localHost = host, localPort = port)
}
