package io.github.whywood.deepdeep.deeplink

import io.github.whywood.deepdeep.ui.navigation.AppDestination
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface DeepLink {
    data class Session(val sessionId: String) : DeepLink

    data class Page(val destination: AppDestination) : DeepLink

    companion object {
        private const val SCHEME = "deepdeep"
        private val MINIAPP_ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

        fun parse(raw: String): DeepLink? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
            val host = uri.host?.lowercase() ?: return null
            val segments = segments(uri) ?: return null
            return when (host) {
                "chat" -> segments.singleOrNull()?.let(::validId)?.let(::Session)
                "settings" -> rootPage(segments, AppDestination.Settings)
                "mcp" -> rootPage(segments, AppDestination.Mcp)
                "skills" -> rootPage(segments, AppDestination.Skills)
                "timers" -> rootPage(segments, AppDestination.Timers)
                "plugins" -> pluginPage(segments)
                "miniapp" -> miniappPage(segments)
                else -> null
            }
        }

        private fun pluginPage(segments: List<String>): DeepLink? = when (segments.size) {
            0 -> Page(AppDestination.Plugins)
            1 -> validId(segments[0])?.let { Page(AppDestination.PluginDetail(it)) }
            else -> null
        }

        private fun miniappPage(segments: List<String>): DeepLink? = when (segments.size) {
            0 -> Page(AppDestination.Miniapp)
            1 -> validId(segments[0])
                ?.takeIf { it.matches(MINIAPP_ID_REGEX) }
                ?.let { Page(AppDestination.MiniappDetail(it)) }

            else -> null
        }

        private fun rootPage(segments: List<String>, destination: AppDestination): DeepLink? =
            if (segments.isEmpty()) Page(destination) else null

        private fun segments(uri: URI): List<String>? {
            val rawPath = uri.rawPath.orEmpty()
            if (rawPath.isEmpty() || rawPath == "/") return emptyList()
            val body = rawPath.removePrefix("/").removeSuffix("/")
            if (body.isEmpty()) return null
            val decoded = ArrayList<String>()
            for (segment in body.split('/')) {
                decoded += decode(segment) ?: return null
            }
            return decoded
        }

        private fun decode(segment: String): String? = runCatching {
            URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull()

        private fun validId(value: String): String? {
            val id = value.trim()
            if (id.isEmpty() || id.contains('/')) return null
            if (id.any { it.isWhitespace() || it.isISOControl() }) return null
            return id
        }
    }
}
