package io.github.whywood.deepdeep.theme

import androidx.compose.ui.graphics.Color
import io.github.whywood.deepdeep.design.DdTheme
import io.github.whywood.deepdeep.design.DeepDeepColors
import java.io.File

enum class ThemeBase(val wire: String) {
    DEEP_DEEP("deepdeep") {
        override fun colors(): DeepDeepColors = DdTheme.lightDeepDeep()
    },
    SANTORINI("santorini") {
        override fun colors(): DeepDeepColors = DdTheme.lightSantorini()
    },
    OCEANIC("oceanic") {
        override fun colors(): DeepDeepColors = DdTheme.darkOceanic()
    },
    ;

    abstract fun colors(): DeepDeepColors

    companion object {
        fun fromWire(value: String): ThemeBase? = entries.firstOrNull { it.wire == value }
    }
}

enum class ThemeToken(val wire: String) {
    PAPER("paper"),
    SURFACE("surface"),
    RAISED("raised"),
    INK("ink"),
    INK_SECONDARY("inkSecondary"),
    INK_TERTIARY("inkTertiary"),
    ACCENT("accent"),
    ACCENT_PRESSED("accentPressed"),
    ON_ACCENT("onAccent"),
    ACCENT_SOFT("accentSoft"),
    DANGER("danger"),
    SUCCESS("success"),
    WARNING("warning"),
    HAIRLINE("hairline"),
    WASH("wash"),
    COMPOSER("composer"),
    SCRIM("scrim"),
    CODE_BACKGROUND("codeBackground"),
    INLINE_CODE_BACKGROUND("inlineCodeBackground"),
    CODE_KEYWORD("codeKeyword"),
    CODE_STRING("codeString"),
    CODE_COMMENT("codeComment"),
    CODE_NUMBER("codeNumber"),
    CODE_FUNCTION("codeFunction"),
    CODE_TYPE("codeType"),
    ;

    companion object {
        fun fromWire(value: String): ThemeToken? = entries.firstOrNull { it.wire == value }
    }
}

data class ThemePackName(
    val default: String,
    val zh: String? = null,
    val en: String? = null,
) {
    fun resolve(language: String?): String = when {
        language?.startsWith("zh") == true -> zh ?: default
        language?.startsWith("en") == true -> en ?: default
        else -> default
    }
}

data class ThemePackTheme(
    val id: String,
    val name: ThemePackName,
    val dark: Boolean,
    val base: ThemeBase,
    val colors: DeepDeepColors,
)

data class ThemePack(
    val themes: List<ThemePackTheme>,
)

data class ThemePackIssue(
    val path: String,
    val message: String,
)

sealed interface ThemePackResult {
    data class Valid(val pack: ThemePack) : ThemePackResult

    data class Invalid(val issues: List<ThemePackIssue>) : ThemePackResult
}

data class PluginThemeSource(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val assetsDir: File,
)

data class PluginThemePack(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val themes: List<ThemePackTheme>,
)

internal fun DeepDeepColors.applyTokens(overrides: Map<ThemeToken, Color>, dark: Boolean): DeepDeepColors {
    var colors = copy(dark = dark)
    overrides.forEach { (token, color) -> colors = colors.withToken(token, color) }
    return colors
}

private fun DeepDeepColors.withToken(token: ThemeToken, color: Color): DeepDeepColors = when (token) {
    ThemeToken.PAPER -> copy(paper = color)
    ThemeToken.SURFACE -> copy(surface = color)
    ThemeToken.RAISED -> copy(raised = color)
    ThemeToken.INK -> copy(ink = color)
    ThemeToken.INK_SECONDARY -> copy(inkSecondary = color)
    ThemeToken.INK_TERTIARY -> copy(inkTertiary = color)
    ThemeToken.ACCENT -> copy(accent = color)
    ThemeToken.ACCENT_PRESSED -> copy(accentPressed = color)
    ThemeToken.ON_ACCENT -> copy(onAccent = color)
    ThemeToken.ACCENT_SOFT -> copy(accentSoft = color)
    ThemeToken.DANGER -> copy(danger = color)
    ThemeToken.SUCCESS -> copy(success = color)
    ThemeToken.WARNING -> copy(warning = color)
    ThemeToken.HAIRLINE -> copy(hairline = color)
    ThemeToken.WASH -> copy(wash = color)
    ThemeToken.COMPOSER -> copy(composer = color)
    ThemeToken.SCRIM -> copy(scrim = color)
    ThemeToken.CODE_BACKGROUND -> copy(codeBackground = color)
    ThemeToken.INLINE_CODE_BACKGROUND -> copy(inlineCodeBackground = color)
    ThemeToken.CODE_KEYWORD -> copy(codeKeyword = color)
    ThemeToken.CODE_STRING -> copy(codeString = color)
    ThemeToken.CODE_COMMENT -> copy(codeComment = color)
    ThemeToken.CODE_NUMBER -> copy(codeNumber = color)
    ThemeToken.CODE_FUNCTION -> copy(codeFunction = color)
    ThemeToken.CODE_TYPE -> copy(codeType = color)
}
