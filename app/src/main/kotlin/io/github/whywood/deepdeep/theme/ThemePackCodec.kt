package io.github.whywood.deepdeep.theme

import androidx.compose.ui.graphics.Color
import io.github.whywood.deepdeep.design.DeepDeepColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object ThemePackCodec {
    const val ENTRY: String = "assets/themes.json"

    const val FILE_NAME: String = "themes.json"

    const val SCHEMA: Int = 1

    const val MAX_BYTES: Long = 64L * 1024

    const val MAX_THEMES: Int = 24

    const val MAX_ID_LENGTH: Int = 64

    const val MAX_NAME_LENGTH: Int = 64

    private const val MIN_TEXT_CONTRAST = 4.5

    private const val MIN_ACCENT_CONTRAST = 3.0

    private val THEME_ID = Regex("^[a-z][a-z0-9_-]*$")

    private val COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun isValidThemeId(value: String): Boolean =
        value.length in 1..MAX_ID_LENGTH && THEME_ID.matches(value)

    fun decode(text: String): ThemePackResult = decode(text.toByteArray(Charsets.UTF_8))

    fun decode(bytes: ByteArray): ThemePackResult {
        if (bytes.size > MAX_BYTES) {
            return ThemePackResult.Invalid(
                listOf(ThemePackIssue("", "themes.json exceeds the ${MAX_BYTES / 1024} KiB limit")),
            )
        }
        val json = runCatching {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return ThemePackResult.Invalid(
            listOf(ThemePackIssue("", "themes.json is not a JSON object")),
        )
        return parse(json)
    }

    private fun parse(json: JsonObject): ThemePackResult {
        val issues = mutableListOf<ThemePackIssue>()
        val schema = json.int("schema")
        if (schema != SCHEMA) {
            issues += ThemePackIssue("schema", "schema must be $SCHEMA")
        }
        val themesJson = json["themes"] as? JsonArray
        if (themesJson == null) {
            issues += ThemePackIssue("themes", "themes must be an array")
            return ThemePackResult.Invalid(issues)
        }
        if (themesJson.isEmpty()) {
            issues += ThemePackIssue("themes", "themes must not be empty")
        }
        if (themesJson.size > MAX_THEMES) {
            issues += ThemePackIssue("themes", "at most $MAX_THEMES themes are allowed")
        }
        val seen = mutableSetOf<String>()
        val themes = themesJson.mapIndexedNotNull { index, element ->
            val themeJson = element as? JsonObject
            if (themeJson == null) {
                issues += ThemePackIssue("themes[$index]", "theme must be an object")
                null
            } else {
                parseTheme(themeJson, "themes[$index]", seen, issues)
            }
        }
        return if (issues.isEmpty()) {
            ThemePackResult.Valid(ThemePack(themes))
        } else {
            ThemePackResult.Invalid(issues)
        }
    }

    private fun parseTheme(
        json: JsonObject,
        path: String,
        seen: MutableSet<String>,
        issues: MutableList<ThemePackIssue>,
    ): ThemePackTheme? {
        val id = json.string("id")
        when {
            id == null -> issues += ThemePackIssue("$path.id", "id must be a string")
            !isValidThemeId(id) -> issues += ThemePackIssue(
                "$path.id",
                "id must match ${THEME_ID.pattern} and be at most $MAX_ID_LENGTH characters",
            )
            !seen.add(id) -> issues += ThemePackIssue("$path.id", "duplicate theme id: $id")
        }
        val name = parseName(json["name"] as? JsonObject, "$path.name", issues)
        val dark = (json["dark"] as? JsonPrimitive)?.booleanOrNull
        if (dark == null) {
            issues += ThemePackIssue("$path.dark", "dark must be a boolean")
        }
        val baseWire = json.string("base")
        val base = baseWire?.let(ThemeBase::fromWire)
        if (base == null) {
            issues += ThemePackIssue("$path.base", "base must be deepdeep, santorini or oceanic")
        }
        val tokensJson = json["tokens"] as? JsonObject
        val tokens = linkedMapOf<ThemeToken, Color>()
        if (tokensJson == null) {
            issues += ThemePackIssue("$path.tokens", "tokens must be an object")
        } else {
            tokensJson.forEach { (wire, element) ->
                val token = ThemeToken.fromWire(wire)
                val raw = (element as? JsonPrimitive)?.contentOrNull
                when {
                    token == null -> issues += ThemePackIssue("$path.tokens.$wire", "unknown token: $wire")
                    raw == null || !COLOR.matches(raw) -> issues += ThemePackIssue(
                        "$path.tokens.$wire",
                        "color must be #RRGGBB or #AARRGGBB",
                    )
                    else -> tokens[token] = color(raw)
                }
            }
        }
        if (id == null || name == null || dark == null || base == null || tokensJson == null) return null
        val colors = base.colors().applyTokens(tokens, dark)
        checkContrast(id, colors, path, issues)
        if (issues.any { it.path.startsWith("$path.") }) return null
        return ThemePackTheme(
            id = id,
            name = name,
            dark = dark,
            base = base,
            colors = colors,
        )
    }

    private fun parseName(
        json: JsonObject?,
        path: String,
        issues: MutableList<ThemePackIssue>,
    ): ThemePackName? {
        if (json == null) {
            issues += ThemePackIssue(path, "name must be an object")
            return null
        }
        val default = json.string("default")
        when {
            default == null -> issues += ThemePackIssue("$path.default", "default name must be a string")
            default.isBlank() -> issues += ThemePackIssue("$path.default", "default name must not be blank")
            default.length > MAX_NAME_LENGTH -> issues += ThemePackIssue(
                "$path.default",
                "default name must be at most $MAX_NAME_LENGTH characters",
            )
        }
        val zh = json.optionalName("zh", "$path.zh", issues)
        val en = json.optionalName("en", "$path.en", issues)
        if (default == null || default.isBlank()) return null
        return ThemePackName(default = default, zh = zh, en = en)
    }

    private fun JsonObject.optionalName(
        key: String,
        path: String,
        issues: MutableList<ThemePackIssue>,
    ): String? {
        val element = this[key] ?: return null
        val value = (element as? JsonPrimitive)?.contentOrNull
        if (value == null || value.isBlank()) {
            issues += ThemePackIssue(path, "translation must be a non-blank string")
            return null
        }
        if (value.length > MAX_NAME_LENGTH) {
            issues += ThemePackIssue(path, "translation must be at most $MAX_NAME_LENGTH characters")
            return null
        }
        return value
    }

    private fun checkContrast(
        id: String,
        colors: DeepDeepColors,
        path: String,
        issues: MutableList<ThemePackIssue>,
    ) {
        fun check(foreground: Color, background: Color, minimum: Double, label: String) {
            val ratio = contrastRatio(foreground, background)
            if (ratio + 1e-9 < minimum) {
                issues += ThemePackIssue(
                    "$path.tokens",
                    "theme $id: $label contrast is ${"%.2f".format(ratio)}:1, must be at least $minimum:1",
                )
            }
        }
        check(colors.ink, colors.paper, MIN_TEXT_CONTRAST, "ink/paper")
        check(colors.ink, colors.surface, MIN_TEXT_CONTRAST, "ink/surface")
        check(colors.inkSecondary, colors.paper, MIN_TEXT_CONTRAST, "inkSecondary/paper")
        check(colors.onAccent, colors.accent, MIN_ACCENT_CONTRAST, "onAccent/accent")
    }

    fun contrastRatio(foreground: Color, background: Color): Double {
        val alpha = foreground.alpha.toDouble()
        val fg = relativeLuminance(
            foreground.red.toDouble() * alpha + background.red.toDouble() * (1 - alpha),
            foreground.green.toDouble() * alpha + background.green.toDouble() * (1 - alpha),
            foreground.blue.toDouble() * alpha + background.blue.toDouble() * (1 - alpha),
        )
        val bg = relativeLuminance(
            background.red.toDouble(),
            background.green.toDouble(),
            background.blue.toDouble(),
        )
        val lighter = maxOf(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(red: Double, green: Double, blue: Double): Double {
        fun channel(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun color(raw: String): Color {
        val hex = raw.removePrefix("#")
        val value = if (hex.length == 6) 0xFF000000L or hex.toLong(16) else hex.toLong(16)
        return Color(value)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
}
