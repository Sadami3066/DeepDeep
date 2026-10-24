package io.github.whywood.deepdeep.ui.mcp

import dev.pi.mcp.config.McpExposure
import dev.pi.mcp.config.McpServerConfig
import java.net.URI

private val NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")

private const val DEFAULT_TIMEOUT_SECONDS = 60L

enum class McpDraftError {
    EMPTY_NAME,
    INVALID_NAME,
    DUPLICATE_NAME,
    INVALID_URL,
    INVALID_HEADERS,
}

data class McpServerDraft(
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val headers: String = "",
    val enabled: Boolean = true,
) {
    fun validate(existingNames: Set<String>): McpDraftError? {
        val trimmedName = name.trim()
        return when {
            trimmedName.isEmpty() -> McpDraftError.EMPTY_NAME
            !NAME_PATTERN.matches(trimmedName) -> McpDraftError.INVALID_NAME
            trimmedName in existingNames -> McpDraftError.DUPLICATE_NAME
            !isHttpUrl(url) -> McpDraftError.INVALID_URL
            parseHeaders(headers) == null -> McpDraftError.INVALID_HEADERS
            else -> null
        }
    }

    fun toConfig(existing: McpServerConfig.Http? = null): McpServerConfig.Http = McpServerConfig.Http(
        url = url.trim(),
        headers = parseHeaders(headers).orEmpty(),
        oauth = existing?.oauth,
        authProvider = existing?.authProvider,
        enabled = enabled,
        timeoutSeconds = existing?.timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS,
        description = description.trim().ifBlank { null },
        exposure = existing?.exposure ?: McpExposure.Deferred,
        toolExposure = existing?.toolExposure.orEmpty(),
    )

    companion object {
        fun from(name: String, config: McpServerConfig.Http): McpServerDraft = McpServerDraft(
            name = name,
            url = config.url,
            description = config.description.orEmpty(),
            headers = config.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            enabled = config.enabled,
        )

        fun isHttpUrl(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
            val scheme = uri.scheme ?: return false
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
                return false
            }
            return !uri.host.isNullOrBlank()
        }

        fun parseHeaders(text: String): Map<String, String>? {
            val headers = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val separator = trimmed.indexOf(':')
                if (separator <= 0) return null
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                if (key.isEmpty() || value.isEmpty()) return null
                if (key.any { it.isWhitespace() || it == ':' }) return null
                if (headers.keys.any { it.equals(key, ignoreCase = true) }) return null
                headers[key] = value
            }
            return headers
        }
    }
}
