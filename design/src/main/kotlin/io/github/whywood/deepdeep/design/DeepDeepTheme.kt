package io.github.whywood.deepdeep.design

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
data class DeepDeepColors(
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

private val Light = DeepDeepColors(
    dark = false,
    paper = Color(0xFFF7F8F8),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    ink = Color(0xFF0B1220),
    inkSecondary = Color(0xFF354451),
    inkTertiary = Color(0xFF64727E),
    accent = Color(0xFF2563EB),
    accentPressed = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F0FE),
    danger = Color(0xFFD93025),
    success = Color(0xFF188038),
    hairline = Color(0xFFDDE5E7),
    wash = Color(0xFFEDF3F4),
    composer = Color(0xFFFFFFFF),
    scrim = Color(0x520B1220),
    codeBackground = Color(0x0F0B1220),
    inlineCodeBackground = Color(0xFFF2F3F4),
    codeKeyword = Color(0xFF7C3AED),
    codeString = Color(0xFF15803D),
    codeComment = Color(0xFF64727E),
    codeNumber = Color(0xFFB45309),
    codeFunction = Color(0xFF1D4ED8),
    codeType = Color(0xFF0E7490),
)

private val Dark = DeepDeepColors(
    dark = true,
    paper = Color(0xFF070D0F),
    surface = Color(0xFF0F1A1D),
    raised = Color(0xFF111F22),
    ink = Color(0xFFF4F7F8),
    inkSecondary = Color(0xFFB7C7CB),
    inkTertiary = Color(0xFF8A9BA0),
    accent = Color(0xFF8AB4F8),
    accentPressed = Color(0xFFA8C7FA),
    onAccent = Color(0xFF07101F),
    accentSoft = Color(0xFF12233D),
    danger = Color(0xFFFF8A80),
    success = Color(0xFF81C995),
    hairline = Color(0xFF1C3034),
    wash = Color(0xFF142428),
    composer = Color(0xFF0F1A1D),
    scrim = Color(0x99000000),
    codeBackground = Color(0x22F4F7F8),
    inlineCodeBackground = Color(0x1FFFFFFF),
    codeKeyword = Color(0xFFC4A7F5),
    codeString = Color(0xFF8FD694),
    codeComment = Color(0xFF8A9BA0),
    codeNumber = Color(0xFFE8B67A),
    codeFunction = Color(0xFF8AB4F8),
    codeType = Color(0xFF7FD1E0),
)

val LocalDeepDeepColors = staticCompositionLocalOf { Light }

enum class DdFontStyle(val id: String, val displayName: String) {
    LORA("lora", "Lora"),
    MANROPE("manrope", "Manrope"),
    ;

    companion object {
        fun fromId(id: String?): DdFontStyle = entries.firstOrNull { it.id == id } ?: LORA
    }
}

@OptIn(ExperimentalTextApi::class)
object DdFonts {
    private val LoraFamily = variableFamily(R.font.lora_variable)
    private val ManropeFamily = variableFamily(R.font.manrope_variable)

    var style: DdFontStyle = DdFontStyle.LORA
        private set

    fun apply(style: DdFontStyle) {
        this.style = style
    }

    fun familyFor(style: DdFontStyle): FontFamily = when (style) {
        DdFontStyle.LORA -> LoraFamily
        DdFontStyle.MANROPE -> ManropeFamily
    }

    val Sans: FontFamily get() = familyFor(style)

    val Display: FontFamily get() = familyFor(style)

    private fun variableFamily(resId: Int): FontFamily = FontFamily(
        Font(resId, FontWeight.Normal, variationSettings = weightVariant(400)),
        Font(resId, FontWeight.Medium, variationSettings = weightVariant(500)),
        Font(resId, FontWeight.SemiBold, variationSettings = weightVariant(600)),
    )

    private fun weightVariant(weight: Int): FontVariation.Settings =
        FontVariation.Settings(FontVariation.weight(weight))
}

object DdType {
    val wordmark: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.Medium,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
        )
    val display: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.Medium,
            fontSize = 40.sp,
            lineHeight = 49.sp,
            letterSpacing = (-0.3).sp,
        )
    val pageTitle: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.2).sp,
        )
    val heading: TextStyle
        get() = TextStyle(
            fontFamily = DdFonts.Display,
            fontWeight = FontWeight.Medium,
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

private fun deepDeepTypography(): Typography = Typography(
    displayLarge = DdType.display.copy(fontSize = 52.sp, lineHeight = 60.sp),
    displayMedium = DdType.display,
    displaySmall = DdType.pageTitle,
    headlineLarge = DdType.pageTitle,
    headlineMedium = DdType.heading,
    headlineSmall = DdType.heading.copy(fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = DdType.label.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = DdType.label,
    titleSmall = DdType.label.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = DdType.body,
    bodyMedium = DdType.bodySmall,
    bodySmall = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = DdType.button,
    labelMedium = DdType.label,
    labelSmall = DdType.micro,
)

private val DeepDeepShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun DeepDeepColors.materialScheme() = if (dark) {
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
fun DeepDeepTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    fontStyle: DdFontStyle = DdFonts.style,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) Dark else Light
    DdFonts.apply(fontStyle)
    val typography = remember(fontStyle) { deepDeepTypography() }
    val colorScheme = remember(dark) { colors.materialScheme() }
    CompositionLocalProvider(LocalDeepDeepColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = DeepDeepShapes,
            content = content,
        )
    }
}

@Composable
fun deepDeepColors(): DeepDeepColors = LocalDeepDeepColors.current
