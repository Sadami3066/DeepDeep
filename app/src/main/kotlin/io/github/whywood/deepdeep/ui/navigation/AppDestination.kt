package io.github.whywood.deepdeep.ui.navigation

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

    data object ChatSettings : AppDestination {
        override val routeKey: String = "settings/chat"
        override val depth: Int = 2
    }

    data object Privacy : AppDestination {
        override val routeKey: String = "settings/privacy"
        override val depth: Int = 2
    }

    data object Search : AppDestination {
        override val routeKey: String = "search"
        override val depth: Int = 1
    }

    companion object {
        private const val PROVIDER_DETAIL_PREFIX = "providers/"

        fun fromRouteKey(routeKey: String): AppDestination? = when {
            routeKey == Chat.routeKey -> Chat
            routeKey == Settings.routeKey -> Settings
            routeKey == Providers.routeKey -> Providers
            routeKey == Archived.routeKey -> Archived
            routeKey == RuntimeSettings.routeKey -> RuntimeSettings
            routeKey == Personalization.routeKey -> Personalization
            routeKey == Fonts.routeKey -> Fonts
            routeKey == ChatSettings.routeKey -> ChatSettings
            routeKey == Privacy.routeKey -> Privacy
            routeKey == Search.routeKey -> Search
            routeKey.startsWith(PROVIDER_DETAIL_PREFIX) ->
                ProviderDetail(routeKey.removePrefix(PROVIDER_DETAIL_PREFIX))
            else -> null
        }
    }
}
