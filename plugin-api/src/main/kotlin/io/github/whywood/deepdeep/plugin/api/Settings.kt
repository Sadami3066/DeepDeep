package io.github.whywood.deepdeep.plugin.api

data class SettingOption(
    val value: String,
    val label: LocalizedText,
)

sealed interface SettingSpec {
    val key: String

    val title: LocalizedText

    val default: String

    val requiresRestart: Boolean get() = false

    val summary: LocalizedText? get() = null

    data class Group(
        override val key: String,
        override val title: LocalizedText,
        val items: List<SettingSpec>,
        override val requiresRestart: Boolean = false,
        override val summary: LocalizedText? = null,
    ) : SettingSpec {
        override val default: String get() = ""
    }

    data class Toggle(
        override val key: String,
        override val title: LocalizedText,
        val defaultValue: Boolean = false,
        override val requiresRestart: Boolean = false,
        override val summary: LocalizedText? = null,
    ) : SettingSpec {
        override val default: String get() = defaultValue.toString()
    }

    data class Choice(
        override val key: String,
        override val title: LocalizedText,
        val options: List<SettingOption>,
        override val default: String,
        override val requiresRestart: Boolean = false,
        override val summary: LocalizedText? = null,
    ) : SettingSpec

    data class Number(
        override val key: String,
        override val title: LocalizedText,
        val min: Int,
        val max: Int,
        val defaultValue: Int,
        override val requiresRestart: Boolean = false,
        override val summary: LocalizedText? = null,
    ) : SettingSpec {
        override val default: String get() = defaultValue.toString()
    }

    data class Text(
        override val key: String,
        override val title: LocalizedText,
        override val default: String = "",
        val secret: Boolean = false,
        override val requiresRestart: Boolean = false,
        override val summary: LocalizedText? = null,
    ) : SettingSpec
}

fun SettingSpec.normalize(raw: String): String? = when (this) {
    is SettingSpec.Group -> null
    is SettingSpec.Toggle -> raw.takeIf { it == "true" || it == "false" }
    is SettingSpec.Choice -> raw.takeIf { candidate -> options.any { it.value == candidate } }
    is SettingSpec.Number -> raw.toIntOrNull()?.coerceIn(min, max)?.toString()
    is SettingSpec.Text -> if (secret) raw else raw.trim()
}

fun List<SettingSpec>.normalize(raw: Map<String, String>): Map<String, String> = flatMap { it.leaves() }
    .mapNotNull { spec ->
        val candidate = raw[spec.key] ?: spec.default
        spec.normalize(candidate)?.let { spec.key to it }
    }
    .toMap()

private fun SettingSpec.leaves(): List<SettingSpec> = when (this) {
    is SettingSpec.Group -> items.flatMap { it.leaves() }
    else -> listOf(this)
}
