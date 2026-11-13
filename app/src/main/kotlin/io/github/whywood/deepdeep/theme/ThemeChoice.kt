package io.github.whywood.deepdeep.theme

sealed interface ThemeChoice {
    val key: String

    data object System : ThemeChoice {
        override val key: String = "system"
    }

    data object DeepDeep : ThemeChoice {
        override val key: String = "deepdeep"
    }

    data object Santorini : ThemeChoice {
        override val key: String = "santorini"
    }

    data object Oceanic : ThemeChoice {
        override val key: String = "oceanic"
    }

    data class Pack(val pluginId: String, val themeId: String) : ThemeChoice {
        override val key: String = "$PACK_PREFIX$pluginId/$themeId"
    }

    companion object {
        const val PACK_PREFIX: String = "pack:"

        val builtIn: List<ThemeChoice> = listOf(System, DeepDeep, Santorini, Oceanic)

        fun parse(raw: String?): ThemeChoice = fromKey(raw) ?: System

        fun fromKey(raw: String?): ThemeChoice? {
            if (raw.isNullOrBlank()) return null
            return when (raw) {
                System.key -> System
                DeepDeep.key -> DeepDeep
                Santorini.key -> Santorini
                Oceanic.key -> Oceanic
                else -> pack(raw)
            }
        }

        fun pack(pluginId: String, themeId: String): Pack? =
            if (isValidPluginId(pluginId) && ThemePackCodec.isValidThemeId(themeId)) {
                Pack(pluginId, themeId)
            } else {
                null
            }

        private fun pack(raw: String): Pack? {
            if (!raw.startsWith(PACK_PREFIX)) return null
            val body = raw.removePrefix(PACK_PREFIX)
            val slash = body.indexOf('/')
            if (slash <= 0 || slash != body.lastIndexOf('/')) return null
            return pack(body.substring(0, slash), body.substring(slash + 1))
        }

        private fun isValidPluginId(value: String): Boolean =
            value.length <= 128 && PLUGIN_ID.matches(value)

        private val PLUGIN_ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    }
}
