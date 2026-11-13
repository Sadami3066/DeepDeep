package io.github.whywood.deepdeep.theme

import androidx.compose.ui.graphics.Color
import io.github.whywood.deepdeep.design.DeepDeepColors
import java.io.File

class ThemeCatalog {
    private var sources: List<PluginThemeSource> = emptyList()
    private val cache = mutableMapOf<String, List<ThemePackTheme>>()

    @Synchronized
    fun refresh(newSources: List<PluginThemeSource>) {
        val unique = newSources.distinctBy { it.pluginId }
        val keys = unique.map(::key).toSet()
        cache.keys.retainAll(keys)
        sources = unique
    }

    @Synchronized
    fun packs(): List<PluginThemePack> = sources.mapNotNull { source ->
        val themes = cache.getOrPut(key(source)) { load(source) }
        if (themes.isEmpty()) {
            null
        } else {
            PluginThemePack(
                pluginId = source.pluginId,
                pluginName = source.pluginName,
                version = source.version,
                themes = themes,
            )
        }
    }

    @Synchronized
    fun builtInOptions(label: (ThemeChoice) -> String, systemDark: Boolean): List<ThemeOption> =
        ThemeChoice.builtIn.map { choice ->
            val colors = ThemePalettes.of(choice, systemDark)
            ThemeOption(
                id = choice,
                label = label(choice),
                source = ThemeSource.BuiltIn,
                dark = colors.dark,
                swatch = swatch(colors),
                colors = colors,
            )
        }

    @Synchronized
    fun pluginOptions(language: String): List<ThemeOption> = packs().flatMap { pack ->
        pack.themes.map { theme ->
            ThemeOption(
                id = ThemeChoice.Pack(pack.pluginId, theme.id),
                label = theme.name.resolve(language),
                source = ThemeSource.Plugin(pack.pluginId, pack.pluginName),
                dark = theme.dark,
                swatch = swatch(theme.colors),
                colors = theme.colors,
            )
        }
    }

    @Synchronized
    fun find(pluginId: String, themeId: String): ThemePackTheme? = packs()
        .firstOrNull { it.pluginId == pluginId }
        ?.themes
        ?.firstOrNull { it.id == themeId }

    private fun swatch(colors: DeepDeepColors): List<Color> =
        listOf(colors.paper, colors.surface, colors.accent)

    private fun load(source: PluginThemeSource): List<ThemePackTheme> {
        val file = File(source.assetsDir, ThemePackCodec.FILE_NAME)
        if (!file.isFile || file.length() > ThemePackCodec.MAX_BYTES) return emptyList()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
        return when (val result = ThemePackCodec.decode(bytes)) {
            is ThemePackResult.Valid -> result.pack.themes
            is ThemePackResult.Invalid -> emptyList()
        }
    }

    private fun key(source: PluginThemeSource): String =
        "${source.pluginId}@${source.version}:${source.assetsDir.absolutePath}"
}
