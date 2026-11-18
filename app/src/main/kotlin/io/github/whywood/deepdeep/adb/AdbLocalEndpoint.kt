package io.github.whywood.deepdeep.adb

data class AdbLocalEndpoint(
    val host: String,
    val port: Int,
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 5555

        fun parse(rawHost: String, rawPort: String): AdbLocalEndpoint? {
            val host = rawHost.trim()
            if (host.isEmpty() || host.length > 255 || host.any { it.isWhitespace() }) return null
            val port = rawPort.trim().toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return AdbLocalEndpoint(host, port)
        }
    }
}
