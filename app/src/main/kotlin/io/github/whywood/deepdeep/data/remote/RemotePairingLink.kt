package io.github.whywood.deepdeep.data.remote

import java.net.URI
import java.net.URISyntaxException

data class RemotePairingLink(
    val instanceId: String,
    val token: String,
    val origin: String,
    val kind: RemoteEntryKind,
    val requiresRemoteCa: Boolean,
) {
    companion object {
        fun parse(raw: String): RemotePairingLink? {
            val value = raw.trim()
            if (value.isEmpty() || value.length > MAX_LINK_LENGTH) return null
            val uri = try {
                URI(value)
            } catch (_: URISyntaxException) {
                return null
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.userInfo != null) return null
            val host = uri.host?.trim('[', ']')?.lowercase() ?: return null
            if (!isValidHost(host)) return null
            val port = if (uri.port == -1) 443 else uri.port
            if (port !in 1..65535) return null
            val path = uri.path?.trimEnd('/').orEmpty()
            if (!path.equals(PAIR_PATH, ignoreCase = true)) return null
            val fragment = parseFragment(uri.rawFragment ?: return null) ?: return null
            val origin = if (port == 443) "https://$host" else "https://$host:$port"
            return RemotePairingLink(
                instanceId = fragment.instanceId,
                token = fragment.token,
                origin = origin,
                kind = if (isLanHost(host)) RemoteEntryKind.Lan else RemoteEntryKind.Public,
                requiresRemoteCa = fragment.requiresRemoteCa,
            )
        }
    }
}

internal fun isLanHost(host: String): Boolean {
    val value = host.lowercase().trim('[', ']')
    if (value == "localhost") return true
    if (value.contains(':')) {
        return value == "::1" ||
            value.startsWith("fe80:") ||
            value.startsWith("fd") ||
            value.startsWith("fc") ||
            value == "::"
    }
    if (value.endsWith(".local") || value.endsWith(".lan")) return true
    if (!value.contains('.')) return true
    val parts = value.split('.')
    if (parts.size != 4) return false
    val first = parts[0].toIntOrNull()?.takeIf { it in 0..255 } ?: return false
    val second = parts[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return false
    return first == 10 ||
        first == 127 ||
        (first == 192 && second == 168) ||
        (first == 172 && second in 16..31) ||
        (first == 169 && second == 254) ||
        (first == 100 && second in 64..127)
}

internal fun originHost(origin: String): String? =
    runCatching { URI(origin).host?.trim('[', ']')?.lowercase() }.getOrNull()

private data class PairFragment(
    val instanceId: String,
    val token: String,
    val requiresRemoteCa: Boolean,
)

private fun parseFragment(raw: String): PairFragment? {
    val fields = raw.split('&')
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            pair.substring(0, separator) to pair.substring(separator + 1)
        }
        .toMap()
    val key = fields["key"]
    if (key != null) {
        val requiresRemoteCa = when {
            key.startsWith(APP_KEY_PREFIX_CA) -> true
            key.startsWith(APP_KEY_PREFIX) -> false
            else -> return null
        }
        val value = key.substringAfter('.')
        val separator = value.indexOf('.')
        if (separator != INSTANCE_ID_LENGTH) return null
        val instanceId = value.substring(0, separator)
        val token = value.substring(separator + 1)
        if (!isValidInstanceId(instanceId) || !isValidToken(token)) return null
        return PairFragment(instanceId, token, requiresRemoteCa)
    }
    val instanceId = fields["instance"] ?: return null
    val token = fields["token"] ?: return null
    if (!isValidInstanceId(instanceId) || !isValidToken(token)) return null
    return PairFragment(instanceId, token, requiresRemoteCa = false)
}

private fun isValidHost(host: String): Boolean =
    host.isNotEmpty() &&
        host.length <= MAX_HOST_LENGTH &&
        host.none { it.isWhitespace() || it == '/' || it == '\\' || it == '?' || it == '#' }

private fun isValidInstanceId(value: String): Boolean =
    value.length == INSTANCE_ID_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

private fun isValidToken(value: String): Boolean =
    value.length in TOKEN_MIN_LENGTH..TOKEN_MAX_LENGTH && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

private const val PAIR_PATH = "/mobile-access/pair"
private const val INSTANCE_ID_LENGTH = 64
private const val TOKEN_MIN_LENGTH = 20
private const val TOKEN_MAX_LENGTH = 128
private const val MAX_LINK_LENGTH = 2048
private const val MAX_HOST_LENGTH = 253
private const val APP_KEY_PREFIX = "dsh1."
private const val APP_KEY_PREFIX_CA = "dsh2."
