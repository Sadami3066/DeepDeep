package io.github.whywood.deepdeep.plugin.api

public interface LocalizedText {
    public val values: Map<String, String>

    public fun resolve(language: String? = null): String? =
        language?.takeIf { it.isNotBlank() }?.let { values[it] } ?: values[DEFAULT_LANGUAGE]

    public fun hasDefault(): Boolean = !values[DEFAULT_LANGUAGE].isNullOrBlank()
}

public fun localizedText(default: String, zh: String? = null, en: String? = null): LocalizedText =
    localizedText(
        buildMap {
            put(DEFAULT_LANGUAGE, default)
            if (!zh.isNullOrBlank()) put("zh", zh)
            if (!en.isNullOrBlank()) put("en", en)
        },
    )

public fun localizedText(values: Map<String, String>): LocalizedText {
    require(!values[DEFAULT_LANGUAGE].isNullOrBlank()) { "default text is required" }
    return LocalizedTextImpl(values.toMap())
}

internal data class LocalizedTextImpl(override val values: Map<String, String>) : LocalizedText

public interface IconRef {
    public val asset: String
}

public fun assetIcon(path: String): IconRef = IconRefImpl(path)

internal data class IconRefImpl(override val asset: String) : IconRef
