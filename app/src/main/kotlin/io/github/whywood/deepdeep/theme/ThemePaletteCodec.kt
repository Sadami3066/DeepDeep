package io.github.whywood.deepdeep.theme

import androidx.compose.ui.graphics.Color
import io.github.whywood.deepdeep.design.DeepDeepColors

object ThemePaletteCodec {
    fun encode(colors: DeepDeepColors): String = buildString {
        append(if (colors.dark) '1' else '0')
        append(';')
        colors.allColors().joinTo(this, ",") { (it.value shr 32).toString(16) }
    }

    fun decode(raw: String?): DeepDeepColors? {
        if (raw.isNullOrBlank()) return null
        val dark = when (raw.substringBefore(';')) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val values = raw.substringAfter(';', "").split(',').map { value ->
            value.toULongOrNull(16) ?: return null
        }
        if (values.size != 25) return null
        return DeepDeepColors(
            dark = dark,
            paper = Color(values[0].toLong()),
            surface = Color(values[1].toLong()),
            raised = Color(values[2].toLong()),
            ink = Color(values[3].toLong()),
            inkSecondary = Color(values[4].toLong()),
            inkTertiary = Color(values[5].toLong()),
            accent = Color(values[6].toLong()),
            accentPressed = Color(values[7].toLong()),
            onAccent = Color(values[8].toLong()),
            accentSoft = Color(values[9].toLong()),
            danger = Color(values[10].toLong()),
            success = Color(values[11].toLong()),
            warning = Color(values[12].toLong()),
            hairline = Color(values[13].toLong()),
            wash = Color(values[14].toLong()),
            composer = Color(values[15].toLong()),
            scrim = Color(values[16].toLong()),
            codeBackground = Color(values[17].toLong()),
            inlineCodeBackground = Color(values[18].toLong()),
            codeKeyword = Color(values[19].toLong()),
            codeString = Color(values[20].toLong()),
            codeComment = Color(values[21].toLong()),
            codeNumber = Color(values[22].toLong()),
            codeFunction = Color(values[23].toLong()),
            codeType = Color(values[24].toLong()),
        )
    }

    private fun DeepDeepColors.allColors(): List<Color> = listOf(
        paper,
        surface,
        raised,
        ink,
        inkSecondary,
        inkTertiary,
        accent,
        accentPressed,
        onAccent,
        accentSoft,
        danger,
        success,
        warning,
        hairline,
        wash,
        composer,
        scrim,
        codeBackground,
        inlineCodeBackground,
        codeKeyword,
        codeString,
        codeComment,
        codeNumber,
        codeFunction,
        codeType,
    )
}
