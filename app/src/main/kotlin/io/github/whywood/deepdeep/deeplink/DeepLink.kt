package io.github.whywood.deepdeep.deeplink

import io.github.whywood.deepdeep.ui.navigation.AppDestination
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonObject

sealed interface DeepLink {
    data class Session(val sessionId: String) : DeepLink

    data class Page(val destination: AppDestination) : DeepLink

    data class PluginPath(
        val pluginId: String,
        val path: String,
        val arguments: JsonObject = JsonObject(emptyMap()),
    ) : DeepLink

    companion object {
        private const val SCHEME = "deepdeep"
        private val MINIAPP_ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")
        private val PLUGIN_PATH_REGEX = Regex("^[a-z0-9][a-z0-9/._-]*$")
        private const val MAX_PLUGIN_PATH_LENGTH = 128

        fun parse(raw: String): DeepLink? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
            val host = uri.host?.lowercase() ?: return null
            val segments = segments(uri) ?: return null
            return when (host) {
                "chat" -> segments.singleOrNull()?.let(::validId)?.let(::Session)
                "settings" -> settingsPage(segments)
                "mcp" -> rootPage(segments, AppDestination.Mcp)
                "skills" -> rootPage(segments, AppDestination.Skills)
                "timers" -> rootPage(segments, AppDestination.Timers)
                "plugins" -> pluginPage(segments, uri)
                "miniapp" -> miniappPage(segments)
                else -> null
            }
        }

        private fun settingsPage(segments: List<String>): DeepLink? {
            if (segments.isEmpty()) return Page(AppDestination.Settings)
            val destination = AppDestination.fromRouteKey("settings/" + segments.joinToString("/")) ?: return null
            return Page(destination)
        }

        private fun pluginPage(segments: List<String>, uri: URI): DeepLink? = when (segments.size) {
            0 -> Page(AppDestination.Plugins)
            1 -> null
            2 -> {
                val pluginId = validId(segments[0]) ?: return null
                val pageId = validId(segments[1]) ?: return null
                val arguments = argumentsOf(uri) ?: return null
                Page(AppDestination.PluginPage(pluginId, pageId, arguments))
            }

            else -> {
                val pluginId = validId(segments[0]) ?: return null
                val path = segments.drop(1).joinToString("/")
                if (path.length > MAX_PLUGIN_PATH_LENGTH || !path.matches(PLUGIN_PATH_REGEX)) return null
                val arguments = argumentsOf(uri) ?: return null
                PluginPath(pluginId, path, arguments)
            }
        }

        private fun argumentsOf(uri: URI): JsonObject? {
            val encoded = queryParameter(uri.rawQuery, "args") ?: return JsonObject(emptyMap())
            return AppDestination.decodePluginArguments(encoded)
        }

        private fun queryParameter(rawQuery: String?, name: String): String? {
            val query = rawQuery?.takeIf { it.isNotBlank() } ?: return null
            for (part in query.split('&')) {
                val key = part.substringBefore('=', missingDelimiterValue = "")
                if (key != name) continue
                val value = part.substringAfter('=', missingDelimiterValue = "")
                if (value.isBlank()) return null
                return runCatching {
                    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
                }.getOrNull()
            }
            return null
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
