package io.github.whywood.superpi.data.relay

import java.net.URI

data class RelayPairingInfo(
    val origin: String,
    val fingerprint: String?,
    val pairCode: String?,
)

object RelayPairingLink {
    private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun parse(raw: String): RelayPairingInfo? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val withScheme = if (schemePattern.containsMatchIn(text)) text else "https://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val origin = if (uri.port >= 0) "$scheme://$host:${uri.port}" else "$scheme://$host"
        val params = parseParams(uri.rawFragment) + parseParams(uri.rawQuery)
        val pairCode = params["code"]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return RelayPairingInfo(
            origin = origin,
            fingerprint = normalizeFingerprint(params["fp"]),
            pairCode = pairCode,
        )
    }

    fun normalizeFingerprint(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.replace(":", "").replace(" ", "").trim().lowercase()
        if (cleaned.length != FINGERPRINT_LENGTH) return null
        return cleaned.takeIf { value -> value.all { it in '0'..'9' || it in 'a'..'f' } }
    }

    private fun parseParams(part: String?): Map<String, String> {
        if (part.isNullOrEmpty()) return emptyMap()
        return part.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null else pair.take(index) to pair.substring(index + 1)
        }.toMap()
    }

    private const val FINGERPRINT_LENGTH = 64
}
