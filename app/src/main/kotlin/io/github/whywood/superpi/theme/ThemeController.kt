package io.github.whywood.superpi.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import io.github.whywood.superpi.R
import io.github.whywood.superpi.data.ThemePreferences
import io.github.whywood.superpi.design.DdTheme
import io.github.whywood.superpi.design.SuperPIColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostThemeState(
    val choice: ThemeChoice,
    val colors: SuperPIColors?,
    val label: String,
)

sealed interface ThemeSource {
    data object BuiltIn : ThemeSource

    data class Plugin(val pluginId: String, val pluginName: String) : ThemeSource
}

data class ThemeOption(
    val id: ThemeChoice,
    val label: String,
    val source: ThemeSource,
    val dark: Boolean,
    val swatch: List<Color>,
    val colors: SuperPIColors,
)

object ThemePalettes {
    fun of(choice: ThemeChoice, systemDark: Boolean): SuperPIColors = when (choice) {
        ThemeChoice.Pi -> if (systemDark) DdTheme.darkPi() else DdTheme.lightPi()
        ThemeChoice.ClassicBlue -> DdTheme.lightClassicBlue()
        ThemeChoice.Santorini -> DdTheme.lightSantorini()
        ThemeChoice.Oceanic -> DdTheme.darkOceanic()
        is ThemeChoice.Pack -> if (systemDark) DdTheme.darkPi() else DdTheme.lightPi()
    }
}

object HostThemeRuntime {
    @Volatile
    var dark: Boolean = false
}

class ThemeController(
    private val context: Context,
    private val preferences: ThemePreferences,
    private val catalog: ThemeCatalog,
    private val pluginSources: () -> List<PluginThemeSource>,
    private val language: () -> String,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HostThemeState> = _state.asStateFlow()

    init {
        HostThemeRuntime.dark = _state.value.colors?.dark
            ?: ThemePalettes.of(_state.value.choice, systemDark()).dark
    }

    fun refresh() {
        catalog.refresh(pluginSources())
        catalog.packs()
        resolve(_state.value.choice)
    }

    fun setChoice(choice: ThemeChoice) {
        if (choice is ThemeChoice.Pack && catalog.find(choice.pluginId, choice.themeId) == null) return
        resolve(choice)
    }

    fun builtInOptions(): List<ThemeOption> =
        catalog.builtInOptions(label = ::builtInLabel, systemDark = systemDark())

    fun pluginOptions(): List<ThemeOption> = catalog.pluginOptions(language())

    private fun resolve(choice: ThemeChoice) {
        when (choice) {
            is ThemeChoice.Pack -> {
                val theme = catalog.find(choice.pluginId, choice.themeId)
                if (theme == null) fallbackToDefault() else applyPack(choice, theme)
            }

            else -> applyBuiltIn(choice)
        }
    }

    private fun applyPack(choice: ThemeChoice.Pack, theme: ThemePackTheme) {
        val label = theme.name.resolve(language())
        HostThemeRuntime.dark = theme.dark
        preferences.write(choice, theme.colors, label)
        _state.value = HostThemeState(choice, theme.colors, label)
    }

    private fun applyBuiltIn(choice: ThemeChoice) {
        HostThemeRuntime.dark = ThemePalettes.of(choice, systemDark()).dark
        preferences.write(choice, null, null)
        _state.value = HostThemeState(choice, null, builtInLabel(choice))
    }

    private fun fallbackToDefault() {
        HostThemeRuntime.dark = ThemePalettes.of(ThemeChoice.Pi, systemDark()).dark
        preferences.write(ThemeChoice.Pi, null, null)
        _state.value = HostThemeState(ThemeChoice.Pi, null, builtInLabel(ThemeChoice.Pi))
    }

    private fun initialState(): HostThemeState {
        val selection = preferences.read()
        return when (val choice = selection.choice) {
            is ThemeChoice.Pack -> HostThemeState(
                choice = choice,
                colors = selection.palette,
                label = selection.label ?: choice.themeId,
            )

            else -> HostThemeState(choice, null, builtInLabel(choice))
        }
    }

    private fun builtInLabel(choice: ThemeChoice): String = context.getString(
        when (choice) {
            ThemeChoice.Pi -> R.string.settings_theme_preset_pi
            ThemeChoice.ClassicBlue -> R.string.settings_theme_preset_classic
            ThemeChoice.Santorini -> R.string.settings_theme_preset_santorini
            ThemeChoice.Oceanic -> R.string.settings_theme_preset_oceanic
            is ThemeChoice.Pack -> R.string.settings_theme
        },
    )

    private fun systemDark(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
