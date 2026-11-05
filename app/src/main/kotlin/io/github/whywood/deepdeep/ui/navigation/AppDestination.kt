package io.github.whywood.deepdeep.ui.navigation

import java.util.Base64

sealed interface AppDestination {
    val routeKey: String
    val depth: Int

    data object Chat : AppDestination {
        override val routeKey: String = "chat"
        override val depth: Int = 0
    }

    data object Settings : AppDestination {
        override val routeKey: String = "settings"
        override val depth: Int = 1
    }

    data object Providers : AppDestination {
        override val routeKey: String = "providers"
        override val depth: Int = 2
    }

    data class ProviderDetail(val id: String) : AppDestination {
        override val routeKey: String = "providers/$id"
        override val depth: Int = 3
    }

    data object Archived : AppDestination {
        override val routeKey: String = "archived"
        override val depth: Int = 2
    }

    data object Mcp : AppDestination {
        override val routeKey: String = "mcp"
        override val depth: Int = 1
    }

    data object RuntimeSettings : AppDestination {
        override val routeKey: String = "settings/runtime"
        override val depth: Int = 2
    }

    data object Personalization : AppDestination {
        override val routeKey: String = "settings/personalization"
        override val depth: Int = 2
    }

    data object Fonts : AppDestination {
        override val routeKey: String = "settings/fonts"
        override val depth: Int = 2
    }

    data object AssistantSettings : AppDestination {
        override val routeKey: String = "settings/assistant"
        override val depth: Int = 2
    }

    data object ChatSettings : AppDestination {
        override val routeKey: String = "settings/chat"
        override val depth: Int = 2
    }

    data object UsageHabits : AppDestination {
        override val routeKey: String = "settings/usage-habits"
        override val depth: Int = 2
    }

    data object Scan : AppDestination {
        override val routeKey: String = "settings/scan"
        override val depth: Int = 2
    }

    data object ScanJson : AppDestination {
        override val routeKey: String = "settings/scan/json"
        override val depth: Int = 3
    }

    data object Privacy : AppDestination {
        override val routeKey: String = "settings/privacy"
        override val depth: Int = 2
    }

    data object Permission : AppDestination {
        override val routeKey: String = "settings/permission"
        override val depth: Int = 2
    }

    data object FloatingWindow : AppDestination {
        override val routeKey: String = "settings/floating"
        override val depth: Int = 2
    }

    data object Search : AppDestination {
        override val routeKey: String = "search"
        override val depth: Int = 1
    }

    data class Files(val rootPath: String) : AppDestination {
        override val routeKey: String = FILES_PREFIX + encodeRoutePath(rootPath)
        override val depth: Int = 1
    }

    data object Skills : AppDestination {
        override val routeKey: String = "skills"
        override val depth: Int = 1
    }

    data object Schedules : AppDestination {
        override val routeKey: String = "schedules"
        override val depth: Int = 1
    }

    data object Plugins : AppDestination {
        override val routeKey: String = "plugins"
        override val depth: Int = 1
    }

    data object Workshop : AppDestination {
        override val routeKey: String = "workshop"
        override val depth: Int = 1
    }

    data class WorkshopDetail(val id: String) : AppDestination {
        override val routeKey: String = "workshop/$id"
        override val depth: Int = 2
    }

    data class PluginDetail(val id: String) : AppDestination {
        override val routeKey: String = "plugins/$id"
        override val depth: Int = 2
    }

    data class WorkspaceSettings(val workspaceId: String) : AppDestination {
        override val routeKey: String = "workspaces/$workspaceId/settings"
        override val depth: Int = 1
    }

    companion object {
        private const val FILES_PREFIX = "files/"
        private const val PROVIDER_DETAIL_PREFIX = "providers/"
        private const val PLUGIN_DETAIL_PREFIX = "plugins/"
        private const val WORKSHOP_DETAIL_PREFIX = "workshop/"
        private const val WORKSPACE_SETTINGS_PREFIX = "workspaces/"
        private const val WORKSPACE_SETTINGS_SUFFIX = "/settings"
        private val WORKSHOP_ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,47}")

        fun encodeRoutePath(path: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))

        fun decodeRoutePath(value: String): String? = runCatching {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        fun fromRouteKey(routeKey: String): AppDestination? = when {
            routeKey == Chat.routeKey -> Chat
            routeKey == Settings.routeKey -> Settings
            routeKey == Providers.routeKey -> Providers
            routeKey == Archived.routeKey -> Archived
            routeKey == RuntimeSettings.routeKey -> RuntimeSettings
            routeKey == Personalization.routeKey -> Personalization
            routeKey == Fonts.routeKey -> Fonts
            routeKey == ChatSettings.routeKey -> ChatSettings
            routeKey == UsageHabits.routeKey -> UsageHabits
            routeKey == Scan.routeKey -> Scan
            routeKey == ScanJson.routeKey -> ScanJson
            routeKey == AssistantSettings.routeKey -> AssistantSettings
            routeKey == Privacy.routeKey -> Privacy
            routeKey == Permission.routeKey -> Permission
            routeKey == FloatingWindow.routeKey -> FloatingWindow
            routeKey == Search.routeKey -> Search
            routeKey == Skills.routeKey -> Skills
            routeKey == Mcp.routeKey -> Mcp
            routeKey == Schedules.routeKey -> Schedules
            routeKey == Plugins.routeKey -> Plugins
            routeKey == Workshop.routeKey -> Workshop
            routeKey.startsWith(WORKSHOP_DETAIL_PREFIX) -> {
                val id = routeKey.removePrefix(WORKSHOP_DETAIL_PREFIX)
                WorkshopDetail(id).takeIf { id.matches(WORKSHOP_ID_REGEX) }
            }

            routeKey.startsWith(PLUGIN_DETAIL_PREFIX) ->
                PluginDetail(routeKey.removePrefix(PLUGIN_DETAIL_PREFIX)).takeIf {
                    it.id.isNotBlank() && !it.id.contains('/')
                }

            routeKey.startsWith(FILES_PREFIX) -> decodeRoutePath(routeKey.removePrefix(FILES_PREFIX))?.let(::Files)

            routeKey.startsWith(PROVIDER_DETAIL_PREFIX) ->
                ProviderDetail(routeKey.removePrefix(PROVIDER_DETAIL_PREFIX))

            routeKey.startsWith(WORKSPACE_SETTINGS_PREFIX) && routeKey.endsWith(WORKSPACE_SETTINGS_SUFFIX) -> {
                val id = routeKey
                    .removePrefix(WORKSPACE_SETTINGS_PREFIX)
                    .removeSuffix(WORKSPACE_SETTINGS_SUFFIX)
                WorkspaceSettings(id).takeIf { id.isNotBlank() }
            }

            else -> null
        }
    }
}
