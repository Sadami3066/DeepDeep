package io.github.whywood.deepdeep.theme

import androidx.compose.ui.graphics.Color
import io.github.whywood.deepdeep.design.DdTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackCodecTest {
    @Test
    fun decodesBasePaletteWithTokenOverrides() {
        val result = ThemePackCodec.decode(
            """
            {
              "schema": 1,
              "themes": [
                {
                  "id": "aurora",
                  "name": { "default": "Aurora", "zh": "极光", "en": "Aurora" },
                  "dark": false,
                  "base": "santorini",
                  "tokens": { "accent": "#7C3AED" }
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result is ThemePackResult.Valid)
        val theme = (result as ThemePackResult.Valid).pack.themes.single()
        assertEquals("aurora", theme.id)
        assertEquals("极光", theme.name.resolve("zh-CN"))
        assertEquals("Aurora", theme.name.resolve("en"))
        assertEquals("Aurora", theme.name.resolve("ja"))
        assertEquals("santorini", theme.base.wire)
        assertEquals(Color(0xFF7C3AED), theme.colors.accent)
        assertEquals(DdTheme.lightSantorini().paper, theme.colors.paper)
    }

    @Test
    fun darkFieldOverridesBaseMode() {
        val result = ThemePackCodec.decode(
            """
            {
              "schema": 1,
              "themes": [
                {
                  "id": "night",
                  "name": { "default": "Night" },
                  "dark": true,
                  "base": "deepdeep",
                  "tokens": {
                    "paper": "#101418",
                    "surface": "#161B22",
                    "ink": "#E6EDF3",
                    "inkSecondary": "#C9D1D9",
                    "accent": "#58A6FF",
                    "onAccent": "#0D1117"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result is ThemePackResult.Valid)
        val theme = (result as ThemePackResult.Valid).pack.themes.single()
        assertTrue(theme.dark)
        assertTrue(theme.colors.dark)
        assertEquals(Color(0xFF101418), theme.colors.paper)
    }

    @Test
    fun rejectsWrongSchema() {
        val result = ThemePackCodec.decode("""{"schema":2,"themes":[]}""")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("schema"))
    }

    @Test
    fun rejectsUnknownToken() {
        val result = decodeTheme(tokens = "\"foo\": \"#FFFFFF\"")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes[0].tokens.foo"))
    }

    @Test
    fun rejectsInvalidColor() {
        val result = decodeTheme(tokens = "\"accent\": \"#12345\"")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes[0].tokens.accent"))
    }

    @Test
    fun rejectsLowContrast() {
        val result = decodeTheme(tokens = "\"paper\": \"#222222\"")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).any { it == "themes[0].tokens" })
    }

    @Test
    fun rejectsDuplicateThemeId() {
        val result = ThemePackCodec.decode(
            """
            {
              "schema": 1,
              "themes": [
                { "id": "aurora", "name": { "default": "A" }, "dark": false, "base": "deepdeep", "tokens": {} },
                { "id": "aurora", "name": { "default": "B" }, "dark": true, "base": "oceanic", "tokens": {} }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes[1].id"))
    }

    @Test
    fun rejectsMissingDefaultName() {
        val result = ThemePackCodec.decode(
            """
            {
              "schema": 1,
              "themes": [
                { "id": "aurora", "name": { "zh": "极光" }, "dark": false, "base": "deepdeep", "tokens": {} }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes[0].name.default"))
    }

    @Test
    fun rejectsTooManyThemes() {
        val themes = (0..ThemePackCodec.MAX_THEMES).joinToString(",") { index ->
            """{ "id": "theme_$index", "name": { "default": "T$index" }, "dark": false, "base": "deepdeep", "tokens": {} }"""
        }
        val result = ThemePackCodec.decode("""{"schema":1,"themes":[$themes]}""")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes"))
    }

    @Test
    fun rejectsOversizedFile() {
        val result = ThemePackCodec.decode(" ".repeat((ThemePackCodec.MAX_BYTES + 1).toInt()))
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains(""))
    }

    @Test
    fun rejectsEmptyThemes() {
        val result = ThemePackCodec.decode("""{"schema":1,"themes":[]}""")
        assertTrue(result is ThemePackResult.Invalid)
        assertTrue(paths(result).contains("themes"))
    }

    @Test
    fun contrastRatioUsesWcagLuminance() {
        val ratio = ThemePackCodec.contrastRatio(Color.White, Color.Black)
        assertEquals(21.0, ratio, 0.01)
        assertEquals(1.0, ThemePackCodec.contrastRatio(Color.Gray, Color.Gray), 0.01)
    }

    private fun decodeTheme(tokens: String): ThemePackResult = ThemePackCodec.decode(
        """
        {
          "schema": 1,
          "themes": [
            {
              "id": "aurora",
              "name": { "default": "Aurora" },
              "dark": false,
              "base": "deepdeep",
              "tokens": { $tokens }
            }
          ]
        }
        """.trimIndent(),
    )

    private fun paths(result: ThemePackResult): List<String> =
        (result as ThemePackResult.Invalid).issues.map { it.path }
}
