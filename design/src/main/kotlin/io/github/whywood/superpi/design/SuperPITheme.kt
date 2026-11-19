package io.github.whywood.superpi.design

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemePreset { SYSTEM, CLASSIC_BLUE, SANTORINI, OCEANIC, PI }

@Immutable
data class SuperPIColors(
    val dark: Boolean,
    val paper: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val hairline: Color,
    val wash: Color,
    val composer: Color,
    val scrim: Color,
    val codeBackground: Color,
    val inlineCodeBackground: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val codeFunction: Color,
    val codeType: Color,
) {
    val userBubble: Color get() = accentSoft
    val onUserBubble: Color get() = ink
}

val LocalSuperPIColors = staticCompositionLocalOf { DdTheme.lightClassicBlue() }

enum class DdFontStyle(val id: String, val displayName: String) {
    FREDOKA("fredoka", "Fredoka"),
    OPEN_RUNDE("open-runde", "Open Runde"),
    LORA("lora", "Lora"),
    MANROPE("manrope", "Manrope"),
    SYSTEM("system", "System"),
    ;

    companion object {
        fun fromId(id: String?): DdFontStyle = entries.firstOrNull { it.id == id } ?: FREDOKA
    }
}

object DdFonts {
    private val openRundeFonts = listOf(
        DdCompositeFont(R.font.open_runde_regular, FontWeight.Normal, variable = false),
        DdCompositeFont(R.font.open_runde_medium, FontWeight.Medium, variable = false),
        DdCompositeFont(R.font.open_runde_semibold, FontWeight.SemiBold, variable = false),
    )
    private val fredokaFonts = variableFonts(R.font.fredoka_variable)
    private val loraFonts = variableFonts(R.font.lora_variable)
    private val manropeFonts = variableFonts(R.font.manrope_variable)
    private val systemFonts = listOf(
        DdCompositeFont(SYSTEM_LATIN_RES, FontWeight.Normal, variable = false),
        DdCompositeFont(SYSTEM_LATIN_RES, FontWeight.Medium, variable = false),
        DdCompositeFont(SYSTEM_LATIN_RES, FontWeight.SemiBold, variable = false),
    )

    private val families = mapOf(
        DdFontStyle.FREDOKA to FontFamily(fredokaFonts),
        DdFontStyle.OPEN_RUNDE to FontFamily(openRundeFonts),
        DdFontStyle.LORA to FontFamily(loraFonts),
        DdFontStyle.MANROPE to FontFamily(manropeFonts),
        DdFontStyle.SYSTEM to FontFamily(systemFonts),
    )
    private val selectedStyle = mutableStateOf(DdFontStyle.FREDOKA)
    var style: DdFontStyle
        get() = selectedStyle.value
        private set(value) {
            selectedStyle.value = value
        }

    fun apply(style: DdFontStyle) {
        this.style = style
    }

    fun familyFor(style: DdFontStyle): FontFamily = families.getValue(style)

    fun preload(context: Context) {
        val fonts = when (style) {
            DdFontStyle.FREDOKA -> fredokaFonts
            DdFontStyle.OPEN_RUNDE -> openRundeFonts
            DdFontStyle.LORA -> loraFonts
            DdFontStyle.MANROPE -> manropeFonts
            DdFontStyle.SYSTEM -> systemFonts
        }
        DdCompositeTypefaceLoader.preload(context, fonts)
    }

    val Sans: FontFamily get() = familyFor(style)

    val Display: FontFamily get() = familyFor(style)

    private fun variableFonts(resId: Int): List<DdCompositeFont> = listOf(
        DdCompositeFont(resId, FontWeight.Normal, variable = true),
        DdCompositeFont(resId, FontWeight.Medium, variable = true),
        DdCompositeFont(resId, FontWeight.SemiBold, variable = true),
    )
}

object DdType {
    val wordmark: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
        )
    val display: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            lineHeight = 49.sp,
            letterSpacing = (-0.3).sp,
        )
    val pageTitle: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.2).sp,
        )
    val heading: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 31.sp,
        )
    val body: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
        )
    val bodySmall: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    val label: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    val chip: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    val button: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
    val micro: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.7.sp,
        )
    val numeric: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp,
        )
}

private fun superPiTypography(style: DdFontStyle): Typography {
    val family = DdFonts.familyFor(style)
    fun TextStyle.font() = copy(fontFamily = family)
    return Typography(
        displayLarge = DdType.display.font().copy(fontSize = 52.sp, lineHeight = 60.sp),
        displayMedium = DdType.display.font(),
        displaySmall = DdType.pageTitle.font(),
        headlineLarge = DdType.pageTitle.font(),
        headlineMedium = DdType.heading.font(),
        headlineSmall = DdType.heading.font().copy(fontSize = 21.sp, lineHeight = 28.sp),
        titleLarge = DdType.label.font().copy(fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = DdType.label.font(),
        titleSmall = DdType.label.font().copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodyLarge = DdType.body.font(),
        bodyMedium = DdType.bodySmall.font(),
        bodySmall = DdType.bodySmall.font().copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = DdType.button.font(),
        labelMedium = DdType.label.font(),
        labelSmall = DdType.micro.font(),
    )
}

private val SuperPIShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun SuperPIColors.materialScheme() = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = inkSecondary,
        onSecondary = paper,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = wash,
        onSurfaceVariant = inkSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        surfaceContainerLow = paper,
        surfaceContainerLowest = paper,
        outline = hairline,
        outlineVariant = hairline,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = inkSecondary,
        onError = paper,
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = inkSecondary,
        onSecondary = paper,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = wash,
        onSurfaceVariant = inkSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        surfaceContainerLow = paper,
        surfaceContainerLowest = paper,
        outline = hairline,
        outlineVariant = hairline,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = inkSecondary,
        onError = paper,
        scrim = Color.Black,
    )
}
@Composable
public fun SuperPITheme(
    preset: ThemePreset = ThemePreset.PI,
    fontStyle: DdFontStyle = DdFonts.style,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (preset) {
        ThemePreset.PI -> if (systemDark) DdTheme.darkPi() else DdTheme.lightPi()
        ThemePreset.SYSTEM -> if (systemDark) DdTheme.darkOceanic() else DdTheme.lightSantorini()
        ThemePreset.SANTORINI -> DdTheme.lightSantorini()
        ThemePreset.OCEANIC -> DdTheme.darkOceanic()
        ThemePreset.CLASSIC_BLUE -> DdTheme.lightClassicBlue()
    }
    SuperPITheme(colors = colors, fontStyle = fontStyle, content = content)
}

@Composable
public fun SuperPITheme(
    colors: SuperPIColors,
    fontStyle: DdFontStyle = DdFonts.style,
    content: @Composable () -> Unit,
) {
    if (DdFonts.style != fontStyle) {
        SideEffect { DdFonts.apply(fontStyle) }
    }
    val typography = remember(fontStyle) { superPiTypography(fontStyle) }
    val colorScheme = remember(colors) { colors.materialScheme() }
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    CompositionLocalProvider(
        LocalSuperPIColors provides colors,
        LocalDdMotionEnabled provides motionEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = SuperPIShapes,
            content = content,
        )
    }
}

@Composable
fun superPiColors(): SuperPIColors = LocalSuperPIColors.current
