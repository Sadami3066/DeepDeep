package io.github.whywood.deepdeep.plugin.api

sealed interface LocalizedText {
    data class Literal(val values: Map<String, String>) : LocalizedText {
        constructor(default: String) : this(mapOf(DEFAULT_LANGUAGE to default))

        fun resolve(language: String? = null): String? =
            language?.takeIf { it.isNotBlank() }?.let { values[it] } ?: values[DEFAULT_LANGUAGE]

        fun hasDefault(): Boolean = !values[DEFAULT_LANGUAGE].isNullOrBlank()
    }

    data class Resource(val id: Int) : LocalizedText
}

sealed interface IconRef {
    data class Resource(val id: Int) : IconRef

    data class Asset(val path: String) : IconRef
}
